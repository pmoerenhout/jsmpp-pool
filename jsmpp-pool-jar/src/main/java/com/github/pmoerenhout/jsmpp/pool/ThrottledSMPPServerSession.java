package com.github.pmoerenhout.jsmpp.pool;

import java.util.concurrent.TimeUnit;

import org.jsmpp.DefaultPDUReader;
import org.jsmpp.DefaultPDUSender;
import org.jsmpp.PDUReader;
import org.jsmpp.PDUSender;
import org.jsmpp.SynchronizedPDUSender;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.ServerMessageReceiverListener;
import org.jsmpp.session.ServerResponseDeliveryListener;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.session.connection.Connection;

import com.google.common.util.concurrent.RateLimiter;

public class ThrottledSMPPServerSession extends SMPPServerSession {

  private final RateLimiter rateLimiter;

  public ThrottledSMPPServerSession(Connection conn,
                                    SessionStateListener sessionStateListener,
                                    ServerMessageReceiverListener messageReceiverListener,
                                    ServerResponseDeliveryListener responseDeliveryListener,
                                    int pduProcessorDegree, double rate) {
    super(conn, sessionStateListener, messageReceiverListener,
        responseDeliveryListener, pduProcessorDegree,
        new SynchronizedPDUSender(new DefaultPDUSender()),
        new DefaultPDUReader());
    checkArguments(rate);
    rateLimiter = RateLimiter.create(rate);
  }

  public ThrottledSMPPServerSession(Connection conn,
                                    SessionStateListener sessionStateListener,
                                    ServerMessageReceiverListener messageReceiverListener,
                                    ServerResponseDeliveryListener responseDeliveryListener,
                                    int pduProcessorDegree, PDUSender pduSender, PDUReader pduReader, double rate) {
    super(conn, sessionStateListener, messageReceiverListener,
        responseDeliveryListener, pduProcessorDegree, pduSender, pduReader);
    checkArguments(rate);
    rateLimiter = RateLimiter.create(rate);
  }

  private void checkArguments(final double rate) {
    if (rate <= 0) {
      throw new IllegalArgumentException("The rate parameter must > 0");
    }
  }

  public double getRate() {
    return this.rateLimiter.getRate();
  }

  public void setRate(final double rate) {
    this.rateLimiter.setRate(rate);
  }

  public double acquire() {
    return this.rateLimiter.acquire();
  }

  public boolean tryAcquire() {
    return this.rateLimiter.tryAcquire();
  }

  public boolean tryAcquire(int permits) {
    return this.rateLimiter.tryAcquire(permits);
  }

  public boolean tryAcquire(int permits, TimeUnit timeUnit) {
    return this.rateLimiter.tryAcquire(permits, timeUnit);
  }

  public boolean tryAcquire(int permits, long timeout, TimeUnit timeUnit) {
    return this.rateLimiter.tryAcquire(permits, timeout, timeUnit);
  }

  public boolean tryAcquire(long timeout, TimeUnit timeUnit) {
    return this.rateLimiter.tryAcquire(timeout, timeUnit);
  }
}
