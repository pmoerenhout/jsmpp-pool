package com.github.pmoerenhout.jsmpp.pool;

import java.io.IOException;

import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SessionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmppService implements InitializingBean {

  private static final Logger LOG = LoggerFactory.getLogger(SmppService.class);
  private PooledSMPPSession pooledSMPPSession;

  @Value("${smpp.host:localhost}")
  private String host;

  @Value("${smpp.port}")
  private int port;

  @Value("${smpp.systemid}")
  private String systemId;

  @Value("${smpp.password}")
  private String password;

  @Value("${smpp.system-type}")
  private String systemType;

  private int enquireLinkTimer = 30000;
  private long transactionTimer = 2000L;
  private long bindTimeout = 2000L;
  private int maxTotal = 1;
  private int minIdle = 0;
  private int maxIdle = 1;
  private double rate = 1;
  private int maxConcurrentRequests = 1;
  private int pduProcessorDegree = 3;

  public void afterPropertiesSet() throws Exception {
    final MessageReceiverListener messageReceiverListener = new MessageReceiverListenerImpl();
    final SessionStateListener sessionStateListener = new SessionStateListenerImpl();
    this.pooledSMPPSession = new PooledSMPPSession(host, port, systemId, password, systemType, messageReceiverListener,
        sessionStateListener, enquireLinkTimer, transactionTimer, bindTimeout, maxTotal, minIdle, maxIdle, rate, maxConcurrentRequests, pduProcessorDegree);
  }

  public void start() {
    try {
      ThrottledSMPPSession session = pooledSMPPSession.borrowObject();
      for (int i = 0; i < 200; i++) {
        try {
          session = pooledSMPPSession.useOrBorrowObject(session);

          double acquired = session.acquire();
          LOG.debug("Acquired in {}s", acquired);

          //LOG.info("Pool {}/{}/{}", pool.getNumActive(), pool.getNumIdle(), ((GenericObjectPool) pool).getNumWaiters());
          //LOG.info("session {}", session.getSessionId());
          final String message = "This is a message!";
//          if (i % 55 == 0) {
          LOG.info("[{}] session {}", i, session.getSessionId());
//          }
//          if (random.nextInt(888) == 0) {
//            LOG.info("Instruct server to close session {}", session.getSessionId());
//            message = "close";
//          }
          try {
            for (int j = 0; j < 1; j++) {
              final String messageId = session
                  .submitShortMessage("CMT", TypeOfNumber.ABBREVIATED, NumberingPlanIndicator.ISDN, "5252",
                      TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, "31614240689",
                      new ESMClass(), (byte) 0, (byte) 0, null, null, new RegisteredDelivery(), (byte) 0,
                      new GeneralDataCoding(),
                      (byte) 0, message.getBytes());
              LOG.debug("Submitted message ID {} on session {}", messageId, session.getSessionId());

//              if (random.nextInt(500) == 0) {
//                LOG.info("Message ID {} on session {}", messageId, session.getSessionId());
//                throw new InvalidResponseException("Simulate invalid response");
//              }
            }
          } catch (ResponseTimeoutException e) {
            LOG.error("Response timeout: {}", e.getMessage());
            pooledSMPPSession.invalidateObject(session);
            continue;
          } catch (NegativeResponseException e) {
            LOG.error("Negative response: {}", e.getMessage());
            pooledSMPPSession.invalidateObject(session);
            continue;
          } catch (InvalidResponseException e) {
            LOG.error("Invalid response: {}", e.getMessage());
            pooledSMPPSession.invalidateObject(session);
            continue;
          } catch (PDUException e) {
            LOG.error("PDU exception: {}", e.getMessage());
            pooledSMPPSession.invalidateObject(session);
            continue;
          } catch (IOException e) {
            LOG.error("IO exception: {}", e.getMessage());
            pooledSMPPSession.invalidateObject(session);
            continue;
          } finally {
            session.release();
          }
//          if (random.nextInt(100) == 0) {
//            LOG.error("Return session {} to pool", session.getSessionId());
//            pooledSMPPSession.returnObject(session);
//            session = null;
//          }
        } catch (Exception ee) {
          LOG.error("Error in pool occurred", ee);
        }
      }

      LOG.info("Sleep 60 seconds before shutdown");
      Thread.sleep(60000);
      pooledSMPPSession.close();
    } catch (Exception e) {
      LOG.error("Error getting pool object", e);
    }
  }

}
