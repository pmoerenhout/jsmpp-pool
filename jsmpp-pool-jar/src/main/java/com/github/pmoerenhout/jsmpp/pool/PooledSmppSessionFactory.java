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
import org.jsmpp.session.connection.socket.NoTrustSSLSocketConnectionFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PooledSmppSessionFactory extends BasePooledObjectFactory<ThrottledSMPPSession> {

  private String host;
  private int port;
  private boolean ssl;
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

  public PooledSmppSessionFactory(final String host, final int port, final boolean ssl,
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
    this.ssl = ssl;
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
    final ThrottledSMPPSession session = getThrottledSMPPSession(ssl, messageRate, maxConcurrentRequests);
    final BindParameter bindParameter = new BindParameter(
        BindType.BIND_TRX, systemId, password, systemType, TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN,
        null);
    session.setEnquireLinkTimer(enquireLinkTimer);
    session.setTransactionTimer(transactionTimer);
    session.setPduProcessorDegree(pduProcessorDegree);
    session.setMessageReceiverListener(messageReceiverListener);
    session.addSessionStateListener(sessionStateListener);
    session.connectAndBind(host, port, bindParameter, bindTimeout);
    log.debug("Created new session {}", session.getSessionId());
    return session;
  }

  @Override
  public PooledObject<ThrottledSMPPSession> wrap(ThrottledSMPPSession session) {
    return new DefaultPooledObject<>(session);
  }

  @Override
  public boolean validateObject(PooledObject<ThrottledSMPPSession> pooledObject) {
    final ThrottledSMPPSession session = pooledObject.getObject();
    log.debug("validateObject {} {}", session.getSessionId(), session.getSessionState());
    return pooledObject.getObject().getSessionState().isBound();
  }

  @Override
  public void destroyObject(PooledObject<ThrottledSMPPSession> pooledObject)
      throws Exception {
    final ThrottledSMPPSession session = pooledObject.getObject();
    log.debug("destroyObject {} {}", session.getSessionId(), session.getSessionState());
    session.unbindAndClose();
  }

  public void activateObject(PooledObject<ThrottledSMPPSession> p) throws Exception {
    final ThrottledSMPPSession session = p.getObject();
    log.debug("activateObject {} {}", session.getSessionId(), session.getSessionState());
  }

  public void passivateObject(PooledObject<ThrottledSMPPSession> p) throws Exception {
    final ThrottledSMPPSession session = p.getObject();
    log.debug("passivateObject {}", session.getSessionId(), session.getSessionState());
  }

  private ThrottledSMPPSession getThrottledSMPPSession(final boolean ssl, final double messageRate, final int maxConcurrentRequests) {
    if (ssl) {
      return new ThrottledSMPPSession(new NoTrustSSLSocketConnectionFactory(), messageRate, maxConcurrentRequests);
    }
    return new ThrottledSMPPSession(messageRate, maxConcurrentRequests);
  }
}
