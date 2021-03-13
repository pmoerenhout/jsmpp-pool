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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SmppServerService {

  @Autowired
  private TaskExecutor smppTaskExecutor;

  @Value("${smpp.server.port:8056}")
  private int port;

  @Value("${smpp.server.processorDegree:3}")
  private int processorDegree;

  @Autowired
  private MetricsService metricsService;

  private TestSmppServer server;

  public void start() {
    server = new TestSmppServer(port, processorDegree);
    server.setMetricsService(metricsService);
    smppTaskExecutor.execute(server);
  }

  public void closeBoundSessions(){
    server.closeBoundSessions();
  }

  public void stop() {
    server.stop();
  }

}
