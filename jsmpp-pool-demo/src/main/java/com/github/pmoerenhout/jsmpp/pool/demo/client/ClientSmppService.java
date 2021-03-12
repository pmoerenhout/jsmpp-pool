package com.github.pmoerenhout.jsmpp.pool.demo.client;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.pmoerenhout.jsmpp.pool.PooledSMPPSession;
import com.github.pmoerenhout.jsmpp.pool.ThrottledSMPPSession;
import com.github.pmoerenhout.jsmpp.pool.demo.server.MetricsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ClientSmppService {

  private PooledSMPPSession<ThrottledSMPPSession> pooledSMPPSession;

  @Autowired
  private MetricsService metricsService;

  @Value("${smpp.client.host:localhost}")
  private String host;

  @Value("${smpp.client.port}")
  private int port;

  @Value("${smpp.client.systemid}")
  private String systemId;

  @Value("${smpp.client.password}")
  private String password;

  @Value("${smpp.client.system-type}")
  private String systemType;

  @Autowired
  private MessageReceiverListener messageReceiverListener;

  @Autowired
  private SessionStateListener sessionStateListener;

  private int enquireLinkTimer = 30000;
  private long transactionTimer = 5000L;
  private long bindTimeout = 5000L;
  private int maxTotal = 1;
  private int minIdle = 0;
  private int maxIdle = 1;
  private double rate = 1000;
  private int maxConcurrentRequests = 5;
  private int pduProcessorDegree = 10;

  public void sendMessages(final int numberOfMessages) {
    try {
      pooledSMPPSession = new PooledSMPPSession(
          host, port, systemId, password, systemType, messageReceiverListener,
          sessionStateListener, enquireLinkTimer, transactionTimer, bindTimeout,
          maxTotal, minIdle, maxIdle, rate, maxConcurrentRequests, pduProcessorDegree);

      for (int i = 0; i < numberOfMessages; i++) {
        try {

          if (pooledSMPPSession.getNumIdle() == 0) {
            log.info("[{}]: There are no idle pooled sessions, borrowObject will trigger new session", i);
          }

          ThrottledSMPPSession session = pooledSMPPSession.borrowObject();

          if (pooledSMPPSession.getNumActive() == 0) {
            throw new RuntimeException("Active pool sessions should be there after borrowObject");
          }

          log.debug("Session from pool is {}", session.getSessionId());
          log.debug("Pool active:{} idle:{} waiters:{}",
              pooledSMPPSession.getNumActive(), pooledSMPPSession.getNumIdle(), pooledSMPPSession.getNumWaiters());

          // Let's see if the RateLimiter has some spare room
          double acquired = session.acquire();
          log.debug("Acquired in {}ms", acquired * 1000L);

          String message = String.format("This is a message %d", i);
          log.debug("[{}] session {}, send submit_sm", i, session.getSessionId());

          // At random, instruct the server to close the session, pool will re-initiate
          if (i % 5000 == 4999) {
            log.info("Instruct server to close session {} at some time", session.getSessionId());
            message = "close";
          }

          try {
            // Send 1 message over the session
            for (int j = 0; j < 1; j++) {
              metricsService.increment("client", "submit_sm");
              final String messageId = session
                  .submitShortMessage("CMT", TypeOfNumber.ABBREVIATED, NumberingPlanIndicator.ISDN, "5252",
                      TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, "31614240689",
                      new ESMClass(), (byte) 0, (byte) 0, null, null, new RegisteredDelivery(), (byte) 0,
                      new GeneralDataCoding(),
                      (byte) 0, message.getBytes());
              log.debug("Submitted message ID {} on session {}", messageId, session.getSessionId());
              metricsService.increment("client_" + session.getSessionId(), "client_submit_sm");
              metricsService.increment("client", "submit_sm_ok");
            }
            log.debug("release for throttle and return bound session to pool");
            session.release();
            pooledSMPPSession.returnObject(session);

          } catch (ResponseTimeoutException e) {
            log.error("Response timeout: {}", e.getMessage());
            metricsService.increment("client", "submit_sm_response_timeout");
            pooledSMPPSession.invalidateObject(session);
          } catch (NegativeResponseException e) {
            log.error("Negative response: {}", e.getMessage());
            metricsService.increment("client", "submit_sm_negative_response");
            pooledSMPPSession.invalidateObject(session);
          } catch (InvalidResponseException e) {
            log.error("Invalid response: {}", e.getMessage());
            metricsService.increment("client", "submit_sm_invalid_response");
            pooledSMPPSession.invalidateObject(session);
          } catch (PDUException e) {
            log.error("PDU exception: {}", e.getMessage());
            metricsService.increment("client", "submit_sm_pdu_exception");
            pooledSMPPSession.invalidateObject(session);
          } catch (IOException e) {
            log.error("I/O exception: {}", e.getMessage());
            metricsService.increment("client", "submit_sm_io_exception");
            pooledSMPPSession.invalidateObject(session);
          } finally {

          }

        } catch (Exception ee) {
          log.error("Error in pool occurred", ee);
        }

      }

      pooledSMPPSession.close();

    } catch (Exception e) {
      log.error("Error getting pool object", e);
    }
  }

}
