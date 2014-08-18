package com.whosbean.qpush.core.service;

import com.google.common.collect.Lists;
import com.whosbean.qpush.core.MetricBuilder;
import com.whosbean.qpush.core.entity.Payload;
import com.whosbean.qpush.core.entity.PayloadHistory;
import com.whosbean.qpush.core.entity.PayloadStatus;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

/**
 * Created by yaming_deng on 14-8-8.
 */
@Service
public class PayloadService extends BaseService {

    public static PayloadService instance;

    protected static final RowMapper<Payload> Payload_ROWMAPPER = new BeanPropertyRowMapper<Payload>(
            Payload.class);

    protected static final RowMapper<PayloadHistory> PayloadHistory_ROWMAPPER = new BeanPropertyRowMapper<PayloadHistory>(
            PayloadHistory.class);

    private ThreadPoolTaskExecutor jdbcExecutor = null;

    public Payload get(long id){
        String sql = "select * from payload where id = ?";
        List<Payload> list = mainJdbc.query(sql, Payload_ROWMAPPER, id);
        if(list.size() > 0){
            Payload payload = list.get(0);
            if (payload.getTotalUsers() > 0){
                sql = "select userId from payload_client where id = ?";
                List<String> clients = mainJdbc.queryForList(sql, String.class, id);
                payload.setClients(clients);
            }
            return payload;
        }
        return null;
    }

    public void add(final Payload payload){
        if (payload == null){
            return;
        }

        final String sql = "insert into payload(title, badge, extras, sound, productId, totalUsers, createAt, statusId, broadcast)values(?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder holder = new GeneratedKeyHolder();
        this.mainJdbc.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(
                    Connection connection) throws SQLException {
                PreparedStatement ps = connection.prepareStatement(sql,
                        Statement.RETURN_GENERATED_KEYS);
                ps.setObject(1, payload.getTitle());
                ps.setObject(2, payload.getBadge());
                ps.setObject(3, payload.getExtras());
                ps.setObject(4, payload.getSound());
                ps.setObject(5, payload.getProductId());
                ps.setObject(6, 0);
                ps.setObject(7, new Date().getTime()/1000);
                ps.setObject(8, PayloadStatus.Pending);
                ps.setObject(9, payload.getBroadcast());
                return ps;
            }
        }, holder);

        payload.setId(holder.getKey().longValue());

        if (payload.getClients() != null){
            List<Object[]> args = Lists.newArrayList();
            final String sql0 = "insert into payload_client(id, userId, productId)values(?, ?, ?)";
            for(String userId : payload.getClients()){
                args.add(new Object[]{payload.getId(), userId, payload.getProductId()});
            }
            this.mainJdbc.batchUpdate(sql0, args);
        }
    }

    public List<Payload> findNormalList(int productId, long start, int page, int limit){
        String sql = "select id from payload where productId = ? and broadcast=? and statusId=? and id > ? order by id limit ?, ?";
        int offset = (page - 1) * limit;
        return mainJdbc.query(sql, Payload_ROWMAPPER, productId, 0, PayloadStatus.Pending, start, offset, limit);
    }

    public List<Payload> findBrodcastList(int productId, long start, int page, int limit){
        String sql = "select id from payload where productId = ? and broadcast=? and statusId=? and id > ? order by id limit ?, ?";
        int offset = (page - 1) * limit;
        return mainJdbc.query(sql, Payload_ROWMAPPER, productId, 1, PayloadStatus.Pending, start, offset, limit);
    }

    public void updateSendStatus(final Payload message, final int counting) {

        jdbcExecutor.submit(new Runnable() {
            @Override
            public void run() {

                String sql = "update payload set statusId=?, totalUsers=totalUsers+?, sentDate=? where id = ?";
                mainJdbc.update(sql, PayloadStatus.Sent, counting, new Date().getTime()/1000, message.getId());

                sql = "update payload_client set statusId=? where id = ?";
                mainJdbc.update(sql, PayloadStatus.Sent, message.getId());

                MetricBuilder.jdbcUpdateMeter.mark(2);
            }
        });


    }

    public Payload findLatest(int productId, String userId){
        String sql = "select id from payload_client where productId=? and userId = ? and statusId=? order by id desc limit 1, 0";
        List<Long> list = this.mainJdbc.queryForList(sql, Long.class, productId, userId, PayloadStatus.Pending);
        if (list.size() == 0){
            return null;
        }
        return this.get(list.get(0));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        instance = this;
        int limit = Integer.parseInt(appConfigs.getProperty("jdbc.executors", "100"));

        //实际扫描线程池
        jdbcExecutor = new ThreadPoolTaskExecutor();
        jdbcExecutor.setCorePoolSize(limit/5);
        jdbcExecutor.setMaxPoolSize(limit);
        jdbcExecutor.setWaitForTasksToCompleteOnShutdown(true);
        jdbcExecutor.afterPropertiesSet();
    }
}
