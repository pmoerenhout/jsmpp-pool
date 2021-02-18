package com.github.pmoerenhout.jsmpp.pool;

import java.util.Set;
import java.util.UUID;

import org.apache.commons.pool2.PoolUtils;
import org.apache.commons.pool2.impl.DefaultPooledObjectInfo;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SessionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PooledSMPPSession {

  private static final Logger LOG = LoggerFactory.getLogger(PooledSMPPSession.class);
  private final String id;
  private final double messageRate;
  private GenericObjectPool<ThrottledSMPPSession> pool;

  public PooledSMPPSession(final String host, final int port, final String systemId,
                           final String password, final String systemType,
                           final MessageReceiverListener messageReceiverListener,
                           final SessionStateListener sessionStateListener,
                           final int enquireLinkTimer, final long transactionTimer,
                           final long bindTimeout,
                           final int maxTotal,
                           final int minIdle,
                           final int maxIdle, final double messageRate, final int maxConcurrentRequests, final int pduProcessorDegree) throws Exception {
    this.pool = createObjectPool(host, port, systemId, password, systemType,
        messageReceiverListener,
        sessionStateListener,
        enquireLinkTimer, transactionTimer, bindTimeout, maxTotal, minIdle, maxIdle, messageRate, maxConcurrentRequests, pduProcessorDegree);
    this.id = UUID.randomUUID().toString();
    this.messageRate = messageRate;
    this.pool.addObjects(pool.getMaxTotal());
    PoolUtils.checkMinIdle(pool, pool.getMinIdle(), 15000);
  }

  private GenericObjectPool createObjectPool(final String host, final int port,
                                             final String systemId, final String password,
                                             final String systemType,
                                             final MessageReceiverListener messageReceiverListener,
                                             final SessionStateListener sessionStateListener,
                                             final int enquireLinkTimer,
                                             final long transactionTimer,
                                             final long bindTimeout,
                                             final int maxTotal,
                                             final int minIdle,
                                             final int maxIdle,
                                             final double messageRate,
                                             final int maxConcurrentRequests,
                                             final int pduProcessorDegree) {
    LOG.info("createObjectPool {}:{} systemId:{} systemType:{}", host, port, systemId, systemType);
    LOG.info("timers enquire:{} transaction:{} bind:{}", enquireLinkTimer, transactionTimer, bindTimeout);
    LOG.info("messageRate:{} maxConcurrentRequests:{} pduProcessorDegree:{}", messageRate, maxConcurrentRequests, pduProcessorDegree);

    final GenericObjectPool<ThrottledSMPPSession> pool = new GenericObjectPool<>(
        new PooledSmppSessionFactory(host, port, systemId, password, systemType, messageReceiverListener,
            sessionStateListener, enquireLinkTimer, transactionTimer, bindTimeout, messageRate, maxConcurrentRequests, pduProcessorDegree));
    LOG.info("eviction idle time:{} (enquireLinkTime * 2)", enquireLinkTimer * 2);
    final GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    config.setLifo(false);
    config.setEvictionPolicyClassName(JsmppEvictionPolicy.class.getName());
    config.setFairness(true);
    config.setTimeBetweenEvictionRunsMillis(enquireLinkTimer * 2);
    config.setMinEvictableIdleTimeMillis(enquireLinkTimer * 2);
    config.setSoftMinEvictableIdleTimeMillis(enquireLinkTimer * 2);
    config.setMaxTotal(maxTotal);
    config.setMinIdle(minIdle);
    config.setMaxIdle(maxIdle);
    config.setTestOnCreate(true);
    config.setTestOnBorrow(true);
    config.setTestWhileIdle(true);
    config.setTestOnReturn(true);
    pool.setConfig(config);
    return pool;
  }

  public ThrottledSMPPSession borrowObject() throws Exception {
    LOG.trace("Borrow Object from pool {}", id);
    ThrottledSMPPSession session = pool.borrowObject();
    if (messageRate != 0) {
      LOG.debug("Session {} is throttled to {} msg/s", session.getSessionId(), messageRate);
    }
    return session;
  }

  public ThrottledSMPPSession useOrBorrowObject(final ThrottledSMPPSession session) throws Exception {
    LOG.trace("Pool {} useOrBorrowObject session:{}", id, session != null ? session.getSessionId() : "null");
    if (session != null && session.getSessionState().isBound()) {
      return session;
    }
    return pool.borrowObject();
  }

  public void returnObject(final ThrottledSMPPSession session) throws Exception {
    LOG.trace("Pool {} returnObject session:{}", id, session != null ? session.getSessionId() : "null");
    pool.returnObject(session);
  }

  public void invalidateObject(final ThrottledSMPPSession session) throws Exception {
    LOG.trace("Pool {} invalidateObject session:{}", id, session != null ? session.getSessionId() : "null");
    pool.invalidateObject(session);
  }

  public void close() {
    LOG.debug("Close pool {}", id);
    pool.close();
  }

  public String getId() {
    return id;
  }

  public Set<DefaultPooledObjectInfo> listAllObjects() {
    return pool.listAllObjects();
  }

}
