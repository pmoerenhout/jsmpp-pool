package com.github.pmoerenhout.jsmpp.pool;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import org.jsmpp.PDUReader;
import org.jsmpp.PDUSender;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.connection.ConnectionFactory;

import com.google.common.util.concurrent.RateLimiter;

public class ThrottledSMPPSession extends SMPPSession {

  private final RateLimiter rateLimiter;
  private final int maxConcurrentRequests;
  private final Semaphore semaphore;

  public ThrottledSMPPSession(final double rate, final int maxConcurrentRequests) {
    checkArguments(rate, maxConcurrentRequests);
    this.rateLimiter = RateLimiter.create(rate);
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.semaphore = new Semaphore(maxConcurrentRequests, true);
  }

  public ThrottledSMPPSession(final PDUSender pduSender, final PDUReader pduReader,
                              final ConnectionFactory connFactory, final double rate, final int maxConcurrentRequests) {
    super(pduSender, pduReader, connFactory);
    checkArguments(rate, maxConcurrentRequests);
    this.rateLimiter = RateLimiter.create(rate);
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.semaphore = new Semaphore(maxConcurrentRequests, true);
  }

  public ThrottledSMPPSession(final String host, final int port, final BindParameter bindParam,
                              final PDUSender pduSender, final PDUReader pduReader,
                              final ConnectionFactory connFactory,
                              final double rate, final int maxConcurrentRequests) throws IOException {
    super(host, port, bindParam, pduSender, pduReader, connFactory);
    checkArguments(rate, maxConcurrentRequests);
    this.rateLimiter = RateLimiter.create(rate);
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.semaphore = new Semaphore(maxConcurrentRequests, true);
  }

  public ThrottledSMPPSession(final String host, final int port, final BindParameter bindParam,
                              final int rate, final int maxConcurrentRequests) throws IOException {
    super(host, port, bindParam);
    checkArguments(rate, maxConcurrentRequests);
    this.rateLimiter = RateLimiter.create(rate);
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.semaphore = new Semaphore(maxConcurrentRequests, true);
  }

  private void checkArguments(final double rate, final int maxConcurrentRequests) {
    if (rate <= 0) {
      throw new IllegalArgumentException("The rate parameter must be > 0");
    }
    if (maxConcurrentRequests < 1) {
      throw new IllegalArgumentException("The maxConcurrentRequests paramater must be >= 1");
    }
  }

  public double getRate() {
    return this.rateLimiter.getRate();
  }

  public void setRate(final double rate) {
    this.rateLimiter.setRate(rate);
  }

  public int getMaxConcurrentRequests() {
    return this.maxConcurrentRequests;
  }

  public int getAvailablePermits() {
    return this.semaphore.availablePermits();
  }

  public double acquire() throws InterruptedException {
    this.semaphore.acquire();
    return this.rateLimiter.acquire();
  }

  public void release() {
    this.semaphore.release();
  }

  public RateLimiter getRateLimiter() {
    return rateLimiter;
  }

  public Semaphore getSemaphore() {
    return semaphore;
  }
}
