/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.openwire;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.DeliveryMode;
import javax.jms.MessageNotWriteableException;

import org.apache.activemq.Service;
import org.apache.activemq.apollo.Combinator.CombinationAware;
import org.apache.activemq.apollo.broker.Broker;
import org.apache.activemq.apollo.broker.BrokerFactory;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.ConnectionId;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.LocalTransactionId;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.MessageId;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.RemoveInfo;
import org.apache.activemq.command.SessionInfo;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.command.TransactionInfo;
import org.apache.activemq.command.XATransactionId;
import org.apache.activemq.legacy.openwireprotocol.StubConnection;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.transport.TransportFactory;
import org.apache.activemq.transport.TransportServer;

import static org.junit.Assert.*;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class BrokerTestScenario implements Service, CombinationAware {

    /**
     * Setting this to false makes the test run faster but they may be less
     * accurate.
     */
    public static final boolean FAST_NO_MESSAGE_LEFT_ASSERT = System.getProperty("FAST_NO_MESSAGE_LEFT_ASSERT", "true").equals("true");

    public Broker broker;
    public long idGenerator;
    public int msgIdGenerator;
    public int txGenerator;
    public int tempDestGenerator;

    public int maxWait = 4000;
    
    public static AtomicInteger pipe_counter = new AtomicInteger();
    
    // Since scenarios can be run concurrently, we want each to be configured with a different broker.
    public String BROKER_ID = "broker-"+pipe_counter.incrementAndGet();
    public String PIPE_URI = "pipe://"+BROKER_ID;
    
    public ActiveMQDestination destination;
    public int deliveryMode = DeliveryMode.NON_PERSISTENT;
    public int prefetch;
    public byte destinationType;
    public boolean durableConsumer;
    protected static final int MAX_NULL_WAIT=500;
	private Map<String, Object> combination;
    
	public void setCombination(Map<String, Object> combination) {
		this.combination = combination;
	}
	
	@Override
	public String toString() {
		if( combination !=null )
			return combination.toString();
		return "default";
	}
	
    private ArrayList<StubConnection> connections = new ArrayList<StubConnection>();

    public void start() throws Exception {
        broker = createBroker();
        broker.start();
    }

    public Broker createBroker() throws Exception {
    	Broker broker = BrokerFactory.createBroker(new URI("jaxb:classpath:non-persistent-activemq.xml"));
    	broker.getDefaultVirtualHost().setHostNames(BROKER_ID);
    	broker.addTransportServer(createTransnportServer());
        return broker;
    }

	public TransportServer createTransnportServer() throws IOException, URISyntaxException {
		return TransportFactory.bind(new URI(getBindURI()));
	}

    public String getBindURI() {
        return PIPE_URI;
    }

    public void stop() throws Exception {
        for (Iterator<StubConnection> iter = connections.iterator(); iter.hasNext();) {
            StubConnection connection = iter.next();
            try {
				connection.stop();
			} catch (Exception e) {
			}
            iter.remove();
        }

        broker.stop();
        broker = null;
    }

    public ConsumerInfo createConsumerInfo(SessionInfo sessionInfo, ActiveMQDestination destination) throws Exception {
        ConsumerInfo info = new ConsumerInfo(sessionInfo, ++idGenerator);
        info.setBrowser(false);
        info.setDestination(destination);
        info.setPrefetchSize(1000);
        info.setDispatchAsync(false);
        return info;
    }

    public RemoveInfo closeConsumerInfo(ConsumerInfo consumerInfo) {
        return consumerInfo.createRemoveCommand();
    }

    public ProducerInfo createProducerInfo(SessionInfo sessionInfo) throws Exception {
        ProducerInfo info = new ProducerInfo(sessionInfo, ++idGenerator);
        return info;
    }

    public SessionInfo createSessionInfo(ConnectionInfo connectionInfo) throws Exception {
        SessionInfo info = new SessionInfo(connectionInfo, ++idGenerator);
        return info;
    }

    public ConnectionInfo createConnectionInfo() throws Exception {
        ConnectionInfo info = new ConnectionInfo();
        info.setConnectionId(new ConnectionId("connection:" + (++idGenerator)));
        info.setClientId(info.getConnectionId().getValue());
        return info;
    }

    public Message createMessage(ProducerInfo producerInfo, ActiveMQDestination destination) {
        ActiveMQTextMessage message = new ActiveMQTextMessage();
        message.setMessageId(new MessageId(producerInfo, ++msgIdGenerator));
        message.setDestination(destination);
        message.setPersistent(false);
        try {
            message.setText("Test Message Payload.");
        } catch (MessageNotWriteableException e) {
        }
        return message;
    }

    public MessageAck createAck(ConsumerInfo consumerInfo, Message msg, int count, byte ackType) {
        MessageAck ack = new MessageAck();
        ack.setAckType(ackType);
        ack.setConsumerId(consumerInfo.getConsumerId());
        ack.setDestination(msg.getDestination());
        ack.setLastMessageId(msg.getMessageId());
        ack.setMessageCount(count);
        return ack;
    }

    public void profilerPause(String prompt) throws IOException {
        if (System.getProperty("profiler") != null) {
            System.out.println();
            System.out.println(prompt + "> Press enter to continue: ");
            while (System.in.read() != '\n') {
            }
            System.out.println(prompt + "> Done.");
        }
    }

    public RemoveInfo closeConnectionInfo(ConnectionInfo info) {
        return info.createRemoveCommand();
    }

    public RemoveInfo closeSessionInfo(SessionInfo info) {
        return info.createRemoveCommand();
    }

    public RemoveInfo closeProducerInfo(ProducerInfo info) {
        return info.createRemoveCommand();
    }

    public Message createMessage(ProducerInfo producerInfo, ActiveMQDestination destination, int deliveryMode) {
        Message message = createMessage(producerInfo, destination);
        message.setPersistent(deliveryMode == DeliveryMode.PERSISTENT);
        return message;
    }

    public LocalTransactionId createLocalTransaction(SessionInfo info) {
        LocalTransactionId id = new LocalTransactionId(info.getSessionId().getParentId(), ++txGenerator);
        return id;
    }

    public XATransactionId createXATransaction(SessionInfo info) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(baos);
        os.writeLong(++txGenerator);
        os.close();
        byte[] bs = baos.toByteArray();

        XATransactionId xid = new XATransactionId();
        xid.setBranchQualifier(bs);
        xid.setGlobalTransactionId(bs);
        xid.setFormatId(55);
        return xid;
    }

    public TransactionInfo createBeginTransaction(ConnectionInfo connectionInfo, TransactionId txid) {
        TransactionInfo info = new TransactionInfo(connectionInfo.getConnectionId(), txid, TransactionInfo.BEGIN);
        return info;
    }

    public TransactionInfo createPrepareTransaction(ConnectionInfo connectionInfo, TransactionId txid) {
        TransactionInfo info = new TransactionInfo(connectionInfo.getConnectionId(), txid, TransactionInfo.PREPARE);
        return info;
    }

    public TransactionInfo createCommitTransaction1Phase(ConnectionInfo connectionInfo, TransactionId txid) {
        TransactionInfo info = new TransactionInfo(connectionInfo.getConnectionId(), txid, TransactionInfo.COMMIT_ONE_PHASE);
        return info;
    }

    public TransactionInfo createCommitTransaction2Phase(ConnectionInfo connectionInfo, TransactionId txid) {
        TransactionInfo info = new TransactionInfo(connectionInfo.getConnectionId(), txid, TransactionInfo.COMMIT_TWO_PHASE);
        return info;
    }

    public TransactionInfo createRollbackTransaction(ConnectionInfo connectionInfo, TransactionId txid) {
        TransactionInfo info = new TransactionInfo(connectionInfo.getConnectionId(), txid, TransactionInfo.ROLLBACK);
        return info;
    }

    public int countMessagesInQueue(StubConnection connection, ConnectionInfo connectionInfo, ActiveMQDestination destination) throws Exception {

        SessionInfo sessionInfo = createSessionInfo(connectionInfo);
        connection.send(sessionInfo);
        ConsumerInfo consumerInfo = createConsumerInfo(sessionInfo, destination);
        consumerInfo.setPrefetchSize(1);
        consumerInfo.setBrowser(true);
        connection.send(consumerInfo);

        ArrayList<Object> skipped = new ArrayList<Object>();

        // Now get the messages.
        Object m = connection.getDispatchQueue().poll(maxWait, TimeUnit.MILLISECONDS);
        int i = 0;
        while (m != null) {
            if (m instanceof MessageDispatch && ((MessageDispatch)m).getConsumerId().equals(consumerInfo.getConsumerId())) {
                MessageDispatch md = (MessageDispatch)m;
                if (md.getMessage() != null) {
                    i++;
                    connection.send(createAck(consumerInfo, md.getMessage(), 1, MessageAck.STANDARD_ACK_TYPE));
                } else {
                    break;
                }
            } else {
                skipped.add(m);
            }
            m = connection.getDispatchQueue().poll(maxWait, TimeUnit.MILLISECONDS);
        }

        for (Iterator<Object> iter = skipped.iterator(); iter.hasNext();) {
            connection.getDispatchQueue().put(iter.next());
        }

        connection.send(closeSessionInfo(sessionInfo));
        return i;

    }

    public DestinationInfo createTempDestinationInfo(ConnectionInfo connectionInfo, byte destinationType) {
        DestinationInfo info = new DestinationInfo();
        info.setConnectionId(connectionInfo.getConnectionId());
        info.setOperationType(DestinationInfo.ADD_OPERATION_TYPE);
        info.setDestination(ActiveMQDestination.createDestination(info.getConnectionId() + ":" + (++tempDestGenerator), destinationType));
        return info;
    }

    public ActiveMQDestination createDestinationInfo(StubConnection connection, ConnectionInfo connectionInfo1, byte destinationType) throws Exception {
        if ((destinationType & ActiveMQDestination.TEMP_MASK) != 0) {
            DestinationInfo info = createTempDestinationInfo(connectionInfo1, destinationType);
            connection.send(info);
            return info.getDestination();
        } else {
            return ActiveMQDestination.createDestination("TEST", destinationType);
        }
    }

    public DestinationInfo closeDestinationInfo(DestinationInfo info) {
        info.setOperationType(DestinationInfo.REMOVE_OPERATION_TYPE);
        info.setTimeout(0);
        return info;
    }

    public static void recursiveDelete(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            for (int i = 0; i < files.length; i++) {
                recursiveDelete(files[i]);
            }
        }
        f.delete();
    }

    public StubConnection createConnection() throws Exception {
    	StubConnection connection = new StubConnection(createTransport());
        connections.add(connection);
        return connection;
    }

    public Transport createTransport() throws URISyntaxException, Exception {
		return TransportFactory.connect(new URI(getConnectURI()));
	}

    public String getConnectURI() {
        return getBindURI();
    }

	/**
     * @param connection
     * @return
     * @throws InterruptedException
     */
    public Message receiveMessage(StubConnection connection) throws InterruptedException {
        return receiveMessage(connection, maxWait);
    }

    public Message receiveMessage(StubConnection connection, long timeout) throws InterruptedException {
        while (true) {
            Object o = connection.getDispatchQueue().poll(timeout, TimeUnit.MILLISECONDS);

            if (o == null) {
                return null;
            }
            if (o instanceof MessageDispatch) {

                MessageDispatch dispatch = (MessageDispatch)o;
                if (dispatch.getMessage() == null) {
                    return null;
                }
                dispatch.setMessage(dispatch.getMessage().copy());
                dispatch.getMessage().setRedeliveryCounter(dispatch.getRedeliveryCounter());
                return dispatch.getMessage();
            }
        }
    };

    public void assertNoMessagesLeft(StubConnection connection) throws InterruptedException {
        long wait = FAST_NO_MESSAGE_LEFT_ASSERT ? 0 : maxWait;
        while (true) {
            Object o = connection.getDispatchQueue().poll(wait, TimeUnit.MILLISECONDS);
            if (o == null) {
                return;
            }
            if (o instanceof MessageDispatch && ((MessageDispatch)o).getMessage() != null) {
                fail(("Received a message: "+((MessageDispatch)o).getMessage().getMessageId()));
            }
        }
    }

}