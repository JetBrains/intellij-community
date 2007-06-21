/*
 * @author max
 */
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class MessageBusImpl implements MessageBus {
  private final ThreadLocal<Queue<DeliveryJob>> myMessageQueue = new ThreadLocal<Queue<DeliveryJob>>() {
    @Override
    protected Queue<DeliveryJob> initialValue() {
      return new ConcurrentLinkedQueue<DeliveryJob>();
    }
  };
  private final Map<Topic, Object> mySyncPublishers = new ConcurrentHashMap<Topic, Object>();
  private final Map<Topic, Object> myAsyncPublishers = new ConcurrentHashMap<Topic, Object>();
  private final Map<Topic, List<MessageBusConnectionImpl>> mySubscribers = new ConcurrentHashMap<Topic, List<MessageBusConnectionImpl>>();
  private final List<MessageBusImpl> myChildBusses = Collections.synchronizedList(new ArrayList<MessageBusImpl>());

  private final static Object NA = new Object();
  private final MessageBusImpl myParentBus;

  //is used for debugging purposes
  @SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
  private Object myOwner;

  public MessageBusImpl(MessageBus parentBus) {
    this(null, parentBus);
  }

  public MessageBusImpl(final Object owner, MessageBus parentBus) {
    myOwner = owner;
    myParentBus = (MessageBusImpl)parentBus;
    if (myParentBus != null) {
      myParentBus.notifyChildBusCreated(this);
    }
  }

  private void notifyChildBusCreated(final MessageBusImpl messageBus) {
    myChildBusses.add(messageBus);
  }

  private void notifyChildBusDisposed(final MessageBusImpl bus) {
    myChildBusses.remove(bus);
  }

  private static class DeliveryJob {
    public DeliveryJob(final MessageBusConnectionImpl connection, final Message message) {
      this.connection = connection;
      this.message = message;
    }

    public final MessageBusConnectionImpl connection;
    public final Message message;
  }

  public MessageBusConnection connect() {
    return new MessageBusConnectionImpl(this);
  }

  public MessageBusConnection connect(Disposable parentDisposable) {
    final MessageBusConnection connection = connect();
    Disposer.register(parentDisposable, connection);
    return connection;
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

  public void dispose() {
    myMessageQueue.get().clear();
    if (myParentBus != null) {
      myParentBus.notifyChildBusDisposed(this);
    }
  }

  private void postMessage(Message message) {
    final Topic topic = message.getTopic();
    final List<MessageBusConnectionImpl> topicSubscribers = mySubscribers.get(topic);
    if (topicSubscribers != null) {
      for (MessageBusConnectionImpl subscriber : topicSubscribers) {
        myMessageQueue.get().offer(new DeliveryJob(subscriber, message));
        subscriber.scheduleMessageDelivery(message);
      }
    }

    Topic.BroadcastDirection direction = topic.getBroadcastDirection();

    if (direction == Topic.BroadcastDirection.TO_CHILDREN) {
      for (MessageBusImpl childBus : myChildBusses) {
        childBus.postMessage(message);
      }
    }

    if (direction == Topic.BroadcastDirection.TO_PARENT && myParentBus != null) {
      myParentBus.postMessage(message);
    }
  }

  private void sendMessage(Message message) {
    pumpMessages();
    postMessage(message);
    pumpMessages();
  }

  private void pumpMessages() {
    DeliveryJob job;
    do {
      job = myMessageQueue.get().poll();
      if (job == null) break;
      job.connection.deliverMessage(job.message);
    }
    while (true);

    for (MessageBusImpl childBus : myChildBusses) {
      childBus.pumpMessages();
    }
  }

  public void notifyOnSubscription(final MessageBusConnectionImpl connection, final Topic topic) {
    List<MessageBusConnectionImpl> topicSubscribers = mySubscribers.get(topic);
    if (topicSubscribers == null) {
      topicSubscribers = new CopyOnWriteArrayList<MessageBusConnectionImpl>();
      mySubscribers.put(topic, topicSubscribers);
    }

    topicSubscribers.add(connection);
  }

  public void notifyConnectionTerminated(final MessageBusConnectionImpl connection) {
    for (List<MessageBusConnectionImpl> topicSubscribers : mySubscribers.values()) {
      topicSubscribers.remove(connection);
    }

    final Iterator<DeliveryJob> i = myMessageQueue.get().iterator();
    while (i.hasNext()) {
      final DeliveryJob job = i.next();
      if (job.connection == connection) {
        i.remove();
      }
    }
  }

  public void deliverSingleMessage() {
    final DeliveryJob job = myMessageQueue.get().poll();
    if (job == null) return;
    job.connection.deliverMessage(job.message);
  }
}