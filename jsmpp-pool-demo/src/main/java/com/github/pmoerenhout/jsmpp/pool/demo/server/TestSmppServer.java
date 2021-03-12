/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.github.pmoerenhout.jsmpp.pool.demo.server;

import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsmpp.PDUStringException;
import org.jsmpp.SMPPConstant;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.DataCodings;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.InterfaceVersion;
import org.jsmpp.bean.MessageMode;
import org.jsmpp.bean.MessageState;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitMultiResult;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindRequest;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SMPPServerSessionListener;
import org.jsmpp.session.ServerMessageReceiverListener;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.DeliveryReceiptState;
import org.jsmpp.util.HexUtil;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import org.jsmpp.util.RandomMessageIDGenerator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestSmppServer implements Runnable, ServerMessageReceiverListener {

  private static final String CANCELSM_NOT_IMPLEMENTED = "cancel_sm not implemented";
  private static final String REPLACESM_NOT_IMPLEMENTED = "replace_sm not implemented";
  private final MessageIDGenerator messageIDGenerator = new RandomMessageIDGenerator();
  private final AbsoluteTimeFormatter timeFormatter = new AbsoluteTimeFormatter();
  private final AtomicInteger requestCounter = new AtomicInteger();
  private ExecutorService waitBindExecService = Executors.newFixedThreadPool(5);
  private ScheduledExecutorService scheduledExecService = Executors.newScheduledThreadPool(5);
  private SMPPServerSessionListener sessionListener;
  private TrafficWatcherThread trafficWatcherThread;
  private int processorDegree;
  private int port;

  private MetricsService metricsService;

  private Map<String, SMPPServerSession> sessions = new ConcurrentHashMap<>();

  private volatile boolean running = true;

  public TestSmppServer(final int port, final int processorDegree) {
    this.port = port;
    this.processorDegree = processorDegree;
  }

  public void setMetricsService(final MetricsService metricsService) {
    this.metricsService = metricsService;
  }

  @Override
  public void run() {
    try {
      sessionListener = new SMPPServerSessionListener(port);
      sessionListener.setSessionStateListener(new SessionStateListenerImpl());
      sessionListener.setPduProcessorDegree(processorDegree);
      trafficWatcherThread = new TrafficWatcherThread();
      trafficWatcherThread.start();
      log.info("Listening on port {}", port);
      while (running) {
        SMPPServerSession serverSession = sessionListener.accept();
        log.info("Accepting connection for session {}", serverSession.getSessionId());
        sessions.put(serverSession.getSessionId(), serverSession);
        serverSession.setMessageReceiverListener(this);

        waitBindExecService.execute(new WaitBindTask(serverSession));
      }
    } catch (SocketException e) {
      log.info("Listen socket: {}", e.getMessage());
    } catch (IOException e) {
      log.error("I/O error occurred", e);
    } finally {
//      try {
//        trafficWatcherThread.join();
//      } catch (InterruptedException e) {
//        log.error("Join of the trafficWatcher thread failed", e);
//      }
      // sessionListener = null;
      waitBindExecService.shutdown();
      scheduledExecService.shutdown();
    }
    log.info("Done");
  }

  public void stop() {
    running = false;
    try {
      sessionListener.close();
    } catch (IOException e) {
      log.error("I/O error occurred on close", e);
    }
    sessions.values().stream().forEach(session -> {
      String sessionId = session.getSessionId();
      SessionState sessionState = session.getSessionState();
      switch (sessionState) {
        case CLOSED:
          log.debug("Server session {} is already closed", sessionId);
          break;
        case BOUND_TX:
        case BOUND_RX:
        case BOUND_TRX:
          log.debug("Unbind and close server session {} in state {}", sessionId, sessionState);
          session.unbindAndClose();
          break;
        case OPEN:
        case UNBOUND:
        case OUTBOUND:
          log.debug("Close server session {} in state {}", sessionId, sessionState);
          session.close();
          break;
        default:
          log.warn("Unknow state: session {} in state {}", sessionId, sessionState);
      }
    });
  }

  public QuerySmResult onAcceptQuerySm(QuerySm querySm,
                                       SMPPServerSession source) throws ProcessRequestException {
    String finalDate = timeFormatter.format(new Date());
    log.info("Receiving query_sm, and return {}", finalDate);
    metricsService.increment("server", "query_sm");
    QuerySmResult querySmResult = new QuerySmResult(finalDate, MessageState.DELIVERED, (byte) 0x00);
    return querySmResult;
  }

  public MessageId onAcceptSubmitSm(SubmitSm submitSm,
                                    SMPPServerSession source) throws ProcessRequestException {
    MessageId messageId = messageIDGenerator.newMessageId();
    byte[] shortMessage = submitSm.getShortMessage();
    String message = null;
    if (submitSm.isUdhi()) {
      int udhl = (shortMessage[0] & 0xff);
      // For now assume LATIN-1 encoding
      message = new String(shortMessage, 1 + udhl, shortMessage.length - udhl - 1, StandardCharsets.ISO_8859_1);
      log.debug("Receiving submit_sm {} {}, return message id {}",
          HexUtil.convertBytesToHexString(shortMessage, 0, 1 + udhl),
          message,
          messageId.getValue());
    } else {
      message = new String(shortMessage, StandardCharsets.ISO_8859_1);
      log.debug("Receiving submit_sm {}, return message id {}", new String(submitSm.getShortMessage()), messageId.getValue());
    }

    if ("close".equals(message)) {
      // Allow some time to send the response before closing
      scheduledExecService.schedule(() -> source.close(), 25, TimeUnit.MILLISECONDS);
    }

    metricsService.increment(source.getSessionId(), "server_submit_sm");
    metricsService.increment("server", "submit_sm");
    requestCounter.incrementAndGet();
    return messageId;
  }

  public SubmitMultiResult onAcceptSubmitMulti(SubmitMulti submitMulti,
                                               SMPPServerSession source) throws ProcessRequestException {
    MessageId messageId = messageIDGenerator.newMessageId();
    log.info("Receiving submit_multi {}, and return message id {}", new String(submitMulti.getShortMessage()), messageId.getValue());
    metricsService.increment("server", "submit_multi");
    requestCounter.incrementAndGet();
    SubmitMultiResult submitMultiResult = new SubmitMultiResult(messageId.getValue());
    return submitMultiResult;
  }

  public DataSmResult onAcceptDataSm(DataSm dataSm, Session source)
      throws ProcessRequestException {
    MessageId messageId = messageIDGenerator.newMessageId();
    OptionalParameter.Message_payload messagePayload = (OptionalParameter.Message_payload) dataSm.getOptionalParameter(OptionalParameter.Tag.MESSAGE_PAYLOAD);
    log.info("Receiving data_sm {}, and return message id {}", messagePayload.getValueAsString(), messageId.getValue());
    requestCounter.incrementAndGet();
    DataSmResult dataSmResult = new DataSmResult(messageId, new OptionalParameter[]{});
    return dataSmResult;
  }

  public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession source)
      throws ProcessRequestException {
    log.warn("CancelSm not implemented");
    throw new ProcessRequestException(CANCELSM_NOT_IMPLEMENTED, SMPPConstant.STAT_ESME_RCANCELFAIL);
  }

  public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession source)
      throws ProcessRequestException {
    log.warn("ReplaceSm not implemented");
    throw new ProcessRequestException(REPLACESM_NOT_IMPLEMENTED, SMPPConstant.STAT_ESME_RREPLACEFAIL);
  }

  private class SessionStateListenerImpl implements SessionStateListener {
    public void onStateChange(SessionState newState, SessionState oldState, Session source) {
      log.debug("New state of session {} is {}", source.getSessionId(), newState);
    }
  }

  private class WaitBindTask implements Runnable {
    private SMPPServerSession serverSession;

    public WaitBindTask(SMPPServerSession serverSession) {
      this.serverSession = serverSession;
    }

    public void run() {
      try {
        BindRequest bindRequest = serverSession.waitForBind(5000);
        log.info("Accepting bind for session {}", serverSession.getSessionId());
        try {
          bindRequest.accept("sys", InterfaceVersion.IF_34);
        } catch (PDUStringException e) {
          log.error("Invalid system id", e);
          bindRequest.reject(SMPPConstant.STAT_ESME_RSYSERR);
        }
      } catch (IllegalStateException e) {
        log.error("System error", e);
      } catch (TimeoutException e) {
        log.warn("Wait for bind has reach timeout", e);
      } catch (IOException e) {
        log.error("Failed accepting bind request for session {}", serverSession.getSessionId());
      } catch (Exception e) {
        log.error("Failed accepting bind request for session", e);
      }
    }
  }

  private class DeliveryReceiptTask implements Runnable {
    private final SMPPServerSession session;
    private final SubmitSm submitSm;
    private MessageId messageId;

    public DeliveryReceiptTask(SMPPServerSession session, SubmitSm submitSm, MessageId messageId) {
      this.session = session;
      this.submitSm = submitSm;
      this.messageId = messageId;
    }

    public void run() {
      String stringValue = Integer.valueOf(messageId.getValue(), 16).toString();
      try {

        DeliveryReceipt delRec = new DeliveryReceipt(stringValue, 1, 1, new Date(), new Date(), DeliveryReceiptState.DELIVRD, null,
            new String(submitSm.getShortMessage()));
        session.deliverShortMessage(
            "mc",
            TypeOfNumber.valueOf(submitSm.getDestAddrTon()),
            NumberingPlanIndicator.valueOf(submitSm.getDestAddrNpi()),
            submitSm.getDestAddress(),
            TypeOfNumber.valueOf(submitSm.getSourceAddrTon()),
            NumberingPlanIndicator.valueOf(submitSm.getSourceAddrNpi()),
            submitSm.getSourceAddr(),
            new ESMClass(MessageMode.DEFAULT, MessageType.SMSC_DEL_RECEIPT, GSMSpecificFeature.DEFAULT),
            (byte) 0,
            (byte) 0,
            new RegisteredDelivery(0),
            DataCodings.ZERO,
            delRec.toString().getBytes());
        log.debug("Sending delivery receipt for message id {}: {}", messageId, stringValue);
      } catch (Exception e) {
        log.error("Failed sending delivery_receipt for message id " + messageId + ":" + stringValue, e);
      }
    }
  }

  private class TrafficWatcherThread extends Thread {
    @Override
    public void run() {
      log.info("Starting traffic watcher...");
      while (running) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        int requestsPerSecond = requestCounter.getAndSet(0);
        log.info("Requests per second: {}", requestsPerSecond);
      }
      log.info("Stopped traffic watcher...");
    }
  }

}
