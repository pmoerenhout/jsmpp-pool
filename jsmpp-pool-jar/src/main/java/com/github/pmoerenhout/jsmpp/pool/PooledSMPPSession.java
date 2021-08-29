package com.github.pmoerenhout.jsmpp.pool;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.pool2.PoolUtils;
import org.apache.commons.pool2.impl.DefaultPooledObjectInfo;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SessionStateListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PooledSMPPSession<T extends SMPPSession> implements AutoCloseable {

  private final String id;
  private final double messageRate;
  private GenericObjectPool<T> pool;

  public PooledSMPPSession(final String host, final int port, final boolean ssl, final String systemId,
                           final String password, final String systemType,
                           final MessageReceiverListener messageReceiverListener,
                           final SessionStateListener sessionStateListener,
                           final int enquireLinkTimer, final long transactionTimer,
                           final long bindTimeout,
                           final int maxTotal,
                           final int minIdle,
                           final int maxIdle, final double messageRate, final int maxConcurrentRequests, final int pduProcessorDegree) throws Exception {
    this.pool = createObjectPool(host, port, ssl, systemId, password, systemType,
        messageReceiverListener,
        sessionStateListener,
        enquireLinkTimer, transactionTimer, bindTimeout, maxTotal, minIdle, maxIdle, messageRate, maxConcurrentRequests, pduProcessorDegree);
    this.id = UUID.randomUUID().toString();
    this.messageRate = messageRate;
    //this.pool.addObjects(pool.getMaxTotal());
    PoolUtils.checkMinIdle(pool, pool.getMinIdle(), 5000);
  }

  private GenericObjectPool createObjectPool(final String host, final int port, final boolean ssl,
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
    log.debug("createObjectPool {}:{} ssl:{} systemId:{} systemType:{}", host, port, ssl, systemId, systemType);
    log.debug("timers enquire:{} transaction:{} bind:{}", enquireLinkTimer, transactionTimer, bindTimeout);
    log.debug("messageRate:{} maxConcurrentRequests:{} pduProcessorDegree:{}", messageRate, maxConcurrentRequests, pduProcessorDegree);

    final GenericObjectPool<ThrottledSMPPSession> pool = new GenericObjectPool<>(
        new PooledSmppSessionFactory(host, port, ssl, systemId, password, systemType, messageReceiverListener,
            sessionStateListener, enquireLinkTimer, transactionTimer, bindTimeout, messageRate, maxConcurrentRequests, pduProcessorDegree));
    log.info("eviction idle time:{} (enquireLinkTime * 2)", enquireLinkTimer * 2);
    final GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    config.setLifo(false);
    config.setEvictionPolicyClassName(JsmppEvictionPolicy.class.getName());
    config.setFairness(true);
    //config.setTimeBetweenEvictionRunsMillis(15000);
    config.setTimeBetweenEvictionRuns(Duration.ofMillis(15000));
    //config.setMinEvictableIdleTimeMillis(60000);
    config.setMinEvictableIdleTime(Duration.ofMillis(60000));
    //config.setSoftMinEvictableIdleTimeMillis(60000);
    config.setSoftMinEvictableIdleTime(Duration.ofMillis(60000));
    config.setMaxTotal(maxTotal);
    config.setMinIdle(minIdle);
    config.setMaxIdle(maxIdle);
    config.setTestOnCreate(true);
    config.setTestOnBorrow(true);
    config.setTestWhileIdle(true);
    config.setTestOnReturn(true);
    pool.setConfig(config);

//    final AbandonedConfig abandonedConfig = new AbandonedConfig();
//    abandonedConfig.setLogAbandoned(true);
//    abandonedConfig.setUseUsageTracking(true);
//    pool.setAbandonedConfig(abandonedConfig);

    return pool;
  }

  public T borrowObject() throws Exception {
    log.trace("Borrow Object from pool {}", id);
    T session = pool.borrowObject();
    log.trace("Borrowed Object from pool {}", id);
    if (messageRate != 0) {
      log.debug("Session {} is throttled to {} msg/s", session.getSessionId(), messageRate);
    }
    return session;
  }

  public T useOrBorrowObject(final T session) throws Exception {
    log.trace("Pool {} useOrBorrowObject session:{}", id, session != null ? session.getSessionId() : "null");
    if (session != null && session.getSessionState().isBound()) {
      return session;
    }
    pool.evict();
    return pool.borrowObject();
  }

  public void returnObject(final T session) throws Exception {
    log.trace("Pool {} returnObject session:{}", id, session != null ? session.getSessionId() : "null");
    pool.returnObject(session);
  }

  public void invalidateObject(final T session) throws Exception {
    log.trace("Pool {} invalidateObject session:{}", id, session != null ? session.getSessionId() : "null");
    pool.invalidateObject(session);
  }

  public void close() {
    log.info("Close pool {}", id);
    pool.close();
  }

  public String getId() {
    return id;
  }

  public Set<DefaultPooledObjectInfo> listAllObjects() {
    return pool.listAllObjects();
  }

  public int getNumActive() {
    return pool.getNumActive();
  }

  public int getNumIdle() {
    return pool.getNumIdle();
  }

  public int getNumWaiters() {
    return pool.getNumWaiters();
  }

}
