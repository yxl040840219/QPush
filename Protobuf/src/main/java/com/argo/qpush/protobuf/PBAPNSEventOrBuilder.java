// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: pb_message.proto

package com.argo.qpush.protobuf;

public interface PBAPNSEventOrBuilder
    extends com.google.protobuf.MessageOrBuilder {

  // required int32 op = 1;
  /**
   * <code>required int32 op = 1;</code>
   */
  boolean hasOp();
  /**
   * <code>required int32 op = 1;</code>
   */
  int getOp();

  // required string token = 2;
  /**
   * <code>required string token = 2;</code>
   */
  boolean hasToken();
  /**
   * <code>required string token = 2;</code>
   */
  java.lang.String getToken();
  /**
   * <code>required string token = 2;</code>
   */
  com.google.protobuf.ByteString
      getTokenBytes();

  // required string appKey = 3;
  /**
   * <code>required string appKey = 3;</code>
   */
  boolean hasAppKey();
  /**
   * <code>required string appKey = 3;</code>
   */
  java.lang.String getAppKey();
  /**
   * <code>required string appKey = 3;</code>
   */
  com.google.protobuf.ByteString
      getAppKeyBytes();

  // required string userId = 4;
  /**
   * <code>required string userId = 4;</code>
   */
  boolean hasUserId();
  /**
   * <code>required string userId = 4;</code>
   */
  java.lang.String getUserId();
  /**
   * <code>required string userId = 4;</code>
   */
  com.google.protobuf.ByteString
      getUserIdBytes();

  // required int32 typeId = 5;
  /**
   * <code>required int32 typeId = 5;</code>
   */
  boolean hasTypeId();
  /**
   * <code>required int32 typeId = 5;</code>
   */
  int getTypeId();

  // optional int32 read = 6;
  /**
   * <code>optional int32 read = 6;</code>
   */
  boolean hasRead();
  /**
   * <code>optional int32 read = 6;</code>
   */
  int getRead();
}
