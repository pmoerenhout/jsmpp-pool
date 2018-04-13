package com.github.pmoerenhout.jsmpp.pool;

import java.io.IOException;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SessionStateListener;

public class PooledSmppSessionFactory extends BasePooledObjectFactory<ThrottledSMPPSession> {

  private String host;
  private int port;
  private String systemId;
  private String password;
  private String systemType;
  private MessageReceiverListener messageReceiverListener;
  private SessionStateListener sessionStateListener;
  private int enquireLinkTimer;
  private long transactionTimer;
  private long bindTimeout;
  private double messageRate;
  private int maxConcurrentRequests;
  private int pduProcessorDegree;

  public PooledSmppSessionFactory(final String host, final int port,
                                  final String systemId, final String password,
                                  final String systemType,
                                  final MessageReceiverListener messageReceiverListener,
                                  final SessionStateListener sessionStateListener,
                                  final int enquireLinkTimer,
                                  final long transactionTimer,
                                  final long bindTimeout,
                                  final double messageRate,
                                  final int maxConcurrentRequests,
                                  final int pduProcessorDegree) {
    this.host = host;
    this.port = port;
    this.systemId = systemId;
    this.password = password;
    this.systemType = systemType;
    this.messageReceiverListener = messageReceiverListener;
    this.sessionStateListener = sessionStateListener;
    this.enquireLinkTimer = enquireLinkTimer;
    this.transactionTimer = transactionTimer;
    this.bindTimeout = bindTimeout;
    this.messageRate = messageRate;
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.pduProcessorDegree = pduProcessorDegree;
  }

  @Override
  public ThrottledSMPPSession create() throws IOException {
    final ThrottledSMPPSession session = new ThrottledSMPPSession(messageRate, maxConcurrentRequests);
    final BindParameter bindParameter = new BindParameter(
        BindType.BIND_TRX, systemId, password, systemType, TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN,
        null);
    session.setEnquireLinkTimer(enquireLinkTimer);
    session.setTransactionTimer(transactionTimer);
    session.setPduProcessorDegree(pduProcessorDegree);
    session.setMessageReceiverListener(messageReceiverListener);
    session.addSessionStateListener(sessionStateListener);
    session.connectAndBind(host, port, bindParameter, bindTimeout);
    return session;
  }

  @Override
  public PooledObject<ThrottledSMPPSession> wrap(ThrottledSMPPSession session) {
    return new DefaultPooledObject<ThrottledSMPPSession>(session);
  }

  @Override
  public boolean validateObject(PooledObject<ThrottledSMPPSession> pooledObject) {
    return pooledObject.getObject().getSessionState().isBound();
  }

  @Override
  public void destroyObject(PooledObject<ThrottledSMPPSession> pooledObject)
      throws Exception {
    pooledObject.getObject().unbindAndClose();
  }

}
