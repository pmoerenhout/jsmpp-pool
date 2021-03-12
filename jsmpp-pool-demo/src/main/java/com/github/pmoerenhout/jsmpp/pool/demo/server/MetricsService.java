package com.github.pmoerenhout.jsmpp.pool.demo.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MetricsService {

  private Map<String, Map<String, AtomicInteger>> counters = new ConcurrentHashMap<>();

  public synchronized int increment(String sessionId, String name) {
    Map<String, AtomicInteger> counter = counters.get(sessionId);
    if (counter != null) {
      AtomicInteger i = counter.get(name);
      if (i != null) {
        return i.incrementAndGet();
      } else {
        counter.put(name, new AtomicInteger(1));
        return 1;
      }
    } else {
      Map<String, AtomicInteger> c = new ConcurrentHashMap<>();
      c.put(name, new AtomicInteger(1));
      counters.put(sessionId, c);
      return 1;
    }
  }

  public int get(String sessionId, String name) {
    return counters.get(sessionId).get(name).get();
  }

  public void show() {
    counters.forEach((sessionId, map) -> {
      map.forEach((counter, value) -> log.info("Session {}: {} => {}", sessionId, counter, value));
    });
  }

  public void show(final String sessionId) {
    final Map<String, AtomicInteger> map = counters.get(sessionId);
    if (map != null) {
      map.forEach((counter, value) -> log.info("{}: {} => {}", sessionId, counter, value));
    }
  }
}
