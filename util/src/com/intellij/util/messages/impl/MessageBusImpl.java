/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
  private final List<MessageBusImpl> myChildBusses = new CopyOnWriteArrayList<MessageBusImpl>();

  private static final Object NA = new Object();
  private final MessageBusImpl myParentBus;

  //is used for debugging purposes
  @SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
  private final Object myOwner;

  public MessageBusImpl() {
    this(null, null);
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
    if (myParentBus != null) {
      myParentBus.pumpMessages();
    }
    else {
      doPumpMessages();
    }
  }

  private void doPumpMessages() {
    do {
      DeliveryJob job = myMessageQueue.get().poll();
      if (job == null) break;
      job.connection.deliverMessage(job.message);
    }
    while (true);

    for (MessageBusImpl childBus : myChildBusses) {
      childBus.doPumpMessages();
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