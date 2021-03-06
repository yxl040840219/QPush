package com.argo.qpush.gateway.dispatch;

import com.argo.qpush.core.MetricBuilder;
import com.argo.qpush.core.entity.*;
import com.argo.qpush.core.service.ClientServiceImpl;
import com.argo.qpush.core.service.PayloadServiceImpl;
import com.argo.qpush.gateway.Connection;
import com.argo.qpush.gateway.SentProgress;
import com.argo.qpush.gateway.keeper.APNSKeeper;
import com.argo.qpush.gateway.keeper.ConnectionKeeper;
import com.argo.qpush.protobuf.PBAPNSMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 *
 * 1对1或1对多的推送
 *
 * Created by yaming_deng on 14-8-8.
 */
public class OneSendThread implements Callable<Integer> {

    protected static Logger logger = LoggerFactory.getLogger(OneSendThread.class);

    private Payload message;
    private Product product;
    private SentProgress progress;

    public OneSendThread(final Product product, final Payload message, final SentProgress progress) {
        super();
        this.message = message;
        this.product = product;
        this.progress = progress;
    }

    @Override
    public Integer call() throws Exception {
        int ret = 0;
        try {
            ret = doSend();
        } catch (Exception e) {
            this.progress.incrFailed();
            logger.error(e.getMessage(), e);
        }
        return ret;
    }

    private Integer doSend() throws Exception {

        if(message == null){
            this.progress.incrFailed();
            return 0;
        }

        if (message.getClients() == null || message.getClients().size() == 0){
            this.progress.incrFailed();
            logger.error("Message Clients is Empty. {}", message);
            return 0;
        }

        SentProgress thisProg = new SentProgress(message.getClients().size());
        for (String client : message.getClients()){
            if (logger.isDebugEnabled()){
                logger.debug("Send Message to {}, {}", client, message);
            }

            Client cc = ClientServiceImpl.instance.findByUserId(client);
            if (cc == null){
                //离线
                logger.error("Client not found. client=" + client);
                thisProg.incrFailed();

                if (message.getOfflineMode().intValue() == PBAPNSMessage.OfflineModes.SendAfterOnline_VALUE){
                    thisProg.incrFailed();
                    message.addFailedClient(client, new PushError(PushError.NoClient));
                }else{
                    thisProg.incrSuccess();
                }

                continue;
            }

            //显示在客户端
            message.setBadge(cc.getBadge() + 1);

            Connection c = ConnectionKeeper.get(product.getAppKey(), client);
            if(c != null) {
                c.send(thisProg, message);
            }else{

                if (!cc.isDevice(ClientType.iOS)){
                    //不是iOS, 可以不继续跑
                    logger.error("Client is not iOS. client=" + client);
                    thisProg.incrFailed();
                    message.addFailedClient(cc.getUserId(), new PushError(PushError.NoConnections));
                    continue;
                }

                if (StringUtils.isBlank(cc.getDeviceToken()) || "NULL".equalsIgnoreCase(cc.getDeviceToken())){
                    logger.error("Client's deviceToken not found. client=" + client);

                    if (message.getOfflineMode().intValue() == PBAPNSMessage.OfflineModes.SendAfterOnline_VALUE){
                        thisProg.incrFailed();
                        message.addFailedClient(cc.getUserId(), new PushError(PushError.NoDevivceToken));
                    }else{
                        thisProg.incrSuccess();
                    }

                    continue;
                }

                if (0 == message.getToMode()){
                    if (message.getOfflineMode().intValue() == PBAPNSMessage.OfflineModes.APNS_VALUE) {
                        APNSKeeper.instance.push(thisProg, this.product, cc, message);
                    }else if (message.getOfflineMode().intValue() == PBAPNSMessage.OfflineModes.SendAfterOnline_VALUE){
                        thisProg.incrFailed();
                        message.addFailedClient(cc.getUserId(), new PushError(PushError.WaitOnline));
                    }
                }else{
                    thisProg.incrSuccess();
                }

            }
        }

        try {
            thisProg.getCountDownLatch().await(message.getClients().size() * 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("SingSend Summary. id=" + message.getId() + ", " + thisProg);

        int total = thisProg.getSuccess().get();

        if (total > 0) {
            MetricBuilder.pushMeter.mark(total);
            MetricBuilder.pushSingleMeter.mark(total);
        }

        try {
            if (message.getStatusId().intValue() == PayloadStatus.Pending0) {
                message.setTotalUsers(total);
                message.setSentDate(new Date().getTime()/1000);
                message.setStatusId(PayloadStatus.Sent);
                PayloadServiceImpl.instance.saveAfterSent(message);
            }else {
                PayloadServiceImpl.instance.updateSendStatus(message, total);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        this.progress.incrSuccess();

        return total;

    }

}
