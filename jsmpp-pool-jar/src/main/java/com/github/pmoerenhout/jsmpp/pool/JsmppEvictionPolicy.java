package com.github.pmoerenhout.jsmpp.pool;

import java.util.Date;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.EvictionConfig;
import org.apache.commons.pool2.impl.EvictionPolicy;
import org.jsmpp.session.SMPPSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsmppEvictionPolicy<T> implements EvictionPolicy<T> {

  private static final Logger LOG = LoggerFactory.getLogger(JsmppEvictionPolicy.class);

  @Override
  public boolean evict(EvictionConfig config, PooledObject<T> underTest, int idleCount) {
    LOG.trace("Evict {} (idle count {})", underTest, idleCount);
    final SMPPSession session = (SMPPSession) underTest.getObject();
    final long idleTime = System.currentTimeMillis() - session.getLastActivityTimestamp();
    LOG.info("State of SMPP session {} is {} with last activity at {} ({}ms idle)",
        session.getSessionId(), session.getSessionState(), new Date(session.getLastActivityTimestamp()), idleTime);
    if (!session.getSessionState().isBound() || config.getIdleEvictTime() < idleTime) {
      LOG.warn("Evicted SMPP session {} ({} or {} < {})", session.getSessionId(), session.getSessionState(), config.getIdleEvictTime(), idleTime);
      return true;
    }
//    if ((config.getIdleSoftEvictTime() < underTest.getIdleTimeMillis() &&
//        config.getMinIdle() < idleCount) ||
//        config.getIdleEvictTime() < underTest.getIdleTimeMillis()) {
//      LOG.info("Evicted {} to destroy (idle count {})", underTest, idleCount);
//      return true;
//    }
    return false;
  }
}