//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client.test;

import com.rabbitmq.client.impl.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import javax.net.SocketFactory;

public class CloseInMainLoop extends BrokerTestCase{
  class SpecialConnection extends AMQConnection{
    private AtomicBoolean validShutdown = new AtomicBoolean();

    public boolean hadValidShutdown(){
      if(isOpen()) throw new IllegalStateException("hadValidShutdown called while connection is still open");
      return validShutdown.get();
    }

    public SpecialConnection() throws Exception{
      super(
          new ConnectionFactory(),
          new SocketFrameHandler(SocketFactory.getDefault().createSocket("localhost", 5672)),
          new DefaultExceptionHandler(){
            @Override public void handleConsumerException(Channel channel,
                                                           Throwable exception,
                                                           Consumer consumer,
                                                           String consumerTag,
                                                           String methodName){
                try {
                  ((AMQConnection) channel.getConnection()).close(AMQP.INTERNAL_ERROR,
                                                                  "Internal error in Consumer " +
                                                                    consumerTag,
                                                                  false,
                                                                  exception);
                } catch (IOException ioe) {
                    // Man, this clearly isn't our day.
                    // Ignore the exception? TODO: Log the nested failure
                }
            }
        });

        this.start();
      }

    @Override
    public boolean processControlCommand(Command c) throws IOException{
      if(c.getMethod() instanceof AMQP.Connection.CloseOk) validShutdown.set(true);
      return super.processControlCommand(c);
    }

  }


  public void testCloseOKNormallyReceived() throws Exception{
    SpecialConnection connection = new SpecialConnection();
    connection.close();
    assertTrue(connection.hadValidShutdown());
  }

  // The thrown runtime exception should get intercepted by the
  // consumer exception handler, and result in a clean shut down.
  public void testCloseWithFaultyConsumer() throws Exception{
    SpecialConnection connection = new SpecialConnection();
    Channel channel = connection.createChannel();
    channel.exchangeDeclare("x", "direct");
    channel.queueDeclare("q", false, false, false, null);
    channel.queueBind("q", "x", "k");

    final CountDownLatch latch = new CountDownLatch(1);

    channel.basicConsume("q", true, new DefaultConsumer(channel){
      public void handleDelivery(String consumerTag,
                                 Envelope envelope,
                                 AMQP.BasicProperties properties,
                                 byte[] body){
        latch.countDown();
        throw new RuntimeException("I am a bad consumer");
      }
    });

    channel.basicPublish("x", "k", null, new byte[10]);

    latch.await();
    Thread.sleep(200);
    assertTrue(connection.hadValidShutdown());
  }

}
