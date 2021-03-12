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
package com.github.pmoerenhout.jsmpp.pool.demo;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.MessageType;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.Session;
import org.jsmpp.util.InvalidDeliveryReceiptException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageReceiverListenerImpl implements MessageReceiverListener {
  
  private Charset charset = StandardCharsets.ISO_8859_1;

  public void onAcceptDeliverSm(final DeliverSm deliverSm)
      throws ProcessRequestException {

    if (MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass())) {
      // this message is delivery receipt
      try {
        final DeliveryReceipt delReceipt = deliverSm.getShortMessageAsDeliveryReceipt();

        // lets cover the id to hex string format
        long id = Long.parseLong(delReceipt.getId()) & 0xffffffff;
        final String messageId = Long.toString(id, 16).toUpperCase();

        /*
         * you can update the status of your submitted message on the
         * database based on messageId
         */

        log.info("Receiving delivery receipt for message '{}' from {} to {} : {}", messageId, deliverSm.getSourceAddr(), deliverSm.getDestAddress(),
            delReceipt);
      } catch (InvalidDeliveryReceiptException e) {
        log.error("Failed getting delivery receipt", e);
      }
    } else {
      // this message is regular short message

      /*
       * you can save the incoming message to database.
       */

      log.info("Receiving message : {}", new String(deliverSm.getShortMessage(), charset));
    }
  }

  public void onAcceptAlertNotification(final AlertNotification alertNotification) {
  }

  public DataSmResult onAcceptDataSm(final DataSm dataSm, final Session source)
      throws ProcessRequestException {
    return null;
  }
}
