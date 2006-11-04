/*
 * @author max
 */
package com.intellij.util.messages.impl;

import com.intellij.util.containers.WeakList;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

public class MessageBusImpl implements MessageBus {
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"}) // Holds strong connections to prevent them from GC-ing.
  private List<MessageBusConnectionImpl> myConnections = new ArrayList<MessageBusConnectionImpl>();

  private Queue<DeliveryJob> myMessageQueue = new LinkedList<DeliveryJob>();

  private Map<Topic, Object> mySyncPublishers = new HashMap<Topic, Object>();
  private Map<Topic, Object> myAsyncPublishers = new HashMap<Topic, Object>();
  private Map<Topic, List<MessageBusConnectionImpl>> mySubscribers = new HashMap<Topic, List<MessageBusConnectionImpl>>();

  private final static Object NA = new Object();

  private static class DeliveryJob {
    public DeliveryJob(final MessageBusConnectionImpl connection, final Message message) {
      this.connection = connection;
      this.message = message;
    }

    public MessageBusConnectionImpl connection;
    public Message message;
  }

  public MessageBusConnection connectStrongly() {
    final MessageBusConnectionImpl connection = new MessageBusConnectionImpl(this);
    myConnections.add(connection);
    return connection;
  }

  public MessageBusConnection connectWeakly() {
    return new MessageBusConnectionImpl(this);
  }

  @SuppressWarnings({"unchecked"})
  public <L> L syncPublisher(final Topic<L> topic) {
    L publisher = (L)mySyncPublishers.get(topic);
    if (publisher == null) {
      final Class<L> listenerClass = topic.getListenerClass();
      InvocationHandler handler = new InvocationHandler() {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          sendMessage(new Message(topic, method, args));
          return NA;
        }
      };
      publisher = (L)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, handler);
      mySyncPublishers.put(topic, publisher);
    }
    return publisher;
  }

  @SuppressWarnings({"unchecked"})
  public <L> L asyncPublisher(final Topic<L> topic) {
    L publisher = (L)myAsyncPublishers.get(topic);
    if (publisher == null) {
      final Class<L> listenerClass = topic.getListenerClass();
      InvocationHandler handler = new InvocationHandler() {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          postMessage(new Message(topic, method, args));
          return NA;
        }
      };
      publisher = (L)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, handler);
      myAsyncPublishers.put(topic, publisher);
    }
    return publisher;
  }

  private void postMessage(Message message) {
    final Topic topic = message.getTopic();
    final List<MessageBusConnectionImpl> topicSubscribers = mySubscribers.get(topic);
    if (topicSubscribers != null) {
      for (MessageBusConnectionImpl subscriber : topicSubscribers) {
        myMessageQueue.offer(new DeliveryJob(subscriber, message));
        subscriber.scheduleMessageDelivery(message);
      }
    }
  }

  private void sendMessage(Message message) {
    postMessage(message);
    pumpMessages();
  }

  private void pumpMessages() {
    DeliveryJob job;
    do {
      job = myMessageQueue.poll();
      if (job == null) break;
      job.connection.deliverMessage(job.message);
    }
    while (true);
  }

  public void notifyOnSubscription(final MessageBusConnectionImpl connection, final Topic topic) {
    List<MessageBusConnectionImpl> topicSubscribers = mySubscribers.get(topic);
    if (topicSubscribers == null) {
      topicSubscribers = new WeakList<MessageBusConnectionImpl>();
      mySubscribers.put(topic, topicSubscribers);
    }

    topicSubscribers.add(connection);
  }

  public void notifyConnectionTerminated(final MessageBusConnectionImpl connection) {
    myConnections.remove(connection);

    for (List<MessageBusConnectionImpl> topicSubscribers : mySubscribers.values()) {
      topicSubscribers.remove(connection);
    }

    final Iterator<DeliveryJob> i = myMessageQueue.iterator();
    while (i.hasNext()) {
      final DeliveryJob job = i.next();
      if (job.connection == connection) {
        i.remove();
      }
    }
  }

  public void deliverSingleMessage() {
    final DeliveryJob job = myMessageQueue.poll();
    if (job == null) return;
    job.connection.deliverMessage(job.message);
  }
}