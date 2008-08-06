//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd.,
//   Cohesive Financial Technologies LLC., and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd., Cohesive Financial Technologies
//   LLC., and Rabbit Technologies Ltd. are Copyright (C) 2007-2008
//   LShift Ltd., Cohesive Financial Technologies LLC., and Rabbit
//   Technologies Ltd.;
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import java.io.IOException;

import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;

import com.rabbitmq.client.AMQP;

public class QosTests extends BrokerTestCase
{
    protected final String Q = "QosTests";

    protected void setUp()
        throws IOException
    {
        openConnection();
        openChannel();
        channel.queueDeclare(ticket, Q);
    }

    protected void tearDown()
        throws IOException
    {
	if (channel != null) {
	    channel.queueDelete(ticket, Q);
	}
        closeChannel();
        closeConnection();
    }

    public void fill(int n)
	throws IOException
    {
	for (int i = 0; i < n; i++) {
	    channel.basicPublish(ticket, "", Q, null, Integer.toString(n).getBytes());
	}
    }

    public int drain()
	throws IOException
    {
	try {
	    QueueingConsumer c;
	    QueueingConsumer.Delivery d;

	    c = new QueueingConsumer(channel);
	    String consumerTag = channel.basicConsume(ticket, Q, false, c);

	    Thread.sleep(500);

	    channel.basicCancel(consumerTag);

	    int count = 0;
	    while (c.nextDelivery(0) != null) { count++; }
	    return count;
	} catch (InterruptedException ie) {
	    return -1;
	}
    }

    public void testMessageLimitGlobalFails()
	throws IOException
    {
	try {
	    channel.basicQos(0, 1, true);
	} catch (IOException ioe) {
	    checkShutdownSignal(AMQP.NOT_IMPLEMENTED, ioe);
	}
    }

    public void testMessageLimit0()
	throws IOException
    {
	channel.basicQos(0, 0, false);
	fill(3);
	assertEquals(3, drain());
    }

    public void testMessageLimit1()
	throws IOException
    {
	channel.basicQos(0, 1, false);
	fill(3);
	assertEquals(1, drain());
	assertEquals(2, drain());
    }
}