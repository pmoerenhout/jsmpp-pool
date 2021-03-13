package com.github.pmoerenhout.jsmpp.pool.demo;

import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SessionStateListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.github.pmoerenhout.jsmpp.pool.demo.client.ClientSmppService;
import com.github.pmoerenhout.jsmpp.pool.demo.server.MetricsService;
import com.github.pmoerenhout.jsmpp.pool.demo.server.SmppServerService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableAsync
@SpringBootApplication
public class PoolApplication implements CommandLineRunner {

  @Autowired
  private AnnotationConfigApplicationContext context;

  @Autowired
  private ClientSmppService client;

  @Autowired
  private SmppServerService server;
  @Autowired
  private MetricsService metricsService;

  public static void main(String[] args) {
    log.info("Start main with arguments {}", args);
    new SpringApplicationBuilder(PoolApplication.class)
        .web(WebApplicationType.NONE)
        .build()
        .run(args).close();
  }

  @Override
  public void run(String... args) throws Exception {
    try {

      server.start();
      client.start();

      /*
       * Set the minIdle to 1 or greater in the pool configuration to let the pool create a new session when no session are left.
       * The timeBetweenEvictionRunsMillis in the pool determines after what time the session will be created
       */

      // client.sendMessages(10000);

      Thread.sleep(10000);
      log.info("Send a message");
      client.sendMessages(1);

      Thread.sleep(30000);
      log.info("Close all server sessions");
      server.closeBoundSessions();

      Thread.sleep(30000);
      log.info("Close all server sessions");
      server.closeBoundSessions();


      // Enable for more details
      // metricsService.show();

      metricsService.show("client");
      metricsService.show("server");

      client.stop();
      server.stop();

    } catch (Exception e) {
      log.error("Error in application", e);
    } finally {
      log.info("Finished application");
    }
  }

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