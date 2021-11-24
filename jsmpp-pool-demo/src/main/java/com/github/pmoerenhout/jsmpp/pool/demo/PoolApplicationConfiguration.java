package com.github.pmoerenhout.jsmpp.pool.demo;

import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SessionStateListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.github.pmoerenhout.jsmpp.pool.demo.MessageReceiverListenerImpl;
import com.github.pmoerenhout.jsmpp.pool.demo.SessionStateListenerImpl;

@Configuration
public class PoolApplicationConfiguration {

  @Bean
  public MessageReceiverListener messageReceiverListener() {
    return new MessageReceiverListenerImpl();
  }

  @Bean
  public SessionStateListener sessionStateListener() {
    return new SessionStateListenerImpl();
  }

  @Bean
  public TaskExecutor smppTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("smpp-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    return executor;
  }
}
