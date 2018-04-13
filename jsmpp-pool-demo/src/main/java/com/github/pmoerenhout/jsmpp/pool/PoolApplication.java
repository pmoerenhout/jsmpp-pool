package com.github.pmoerenhout.jsmpp.pool;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class PoolApplication implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(PoolApplication.class);

  @Autowired
  AnnotationConfigApplicationContext context;

  @Autowired
  SmppService smppService;

  public static void main(String[] args) {
    LOG.info("Start main with arguments {}", args);
    SpringApplication.run(PoolApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    LOG.info("Run with arguments: {}", args);
    LOG.info("Application id: {}", context.getId());
    LOG.info("Application startup date: {}", new Date(context.getStartupDate()));
    smppService.start();
  }
}