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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import com.intellij.util.messages.Topic;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MessageBusConnectionImpl implements MessageBusConnection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.messages.impl.MessageBusConnectionImpl");

  private final MessageBusImpl myBus;
  private final ThreadLocal<Queue<Message>> myPendingMessages = new ThreadLocal<Queue<Message>>() {
    @Override
    protected Queue<Message> initialValue() {
      return new ConcurrentLinkedQueue<Message>();
    }
  };
  private MessageHandler myDefaultHandler;
  private Map<Topic, Object> mySubscriptions = new HashMap<Topic, Object>();

  public MessageBusConnectionImpl(MessageBusImpl bus) {
    myBus = bus;
  }

  public <L> void subscribe(Topic<L> topic, L handler) {
    if (mySubscriptions.containsKey(topic)) {
      throw new IllegalStateException("Subscription to " + topic + " already exists");
    }

    mySubscriptions.put(topic, handler);
    myBus.notifyOnSubscription(this, topic);
  }

  public <L> void subscribe(Topic<L> topic) {
    if (mySubscriptions.containsKey(topic)) {
      throw new IllegalStateException("Subscription to " + topic + " already exists");
    }

    if (myDefaultHandler == null) {
      throw new IllegalStateException("Connection must have default handler installed prior to any anonymous subscriptions.");
    }

    mySubscriptions.put(topic, myDefaultHandler);
    myBus.notifyOnSubscription(this, topic);
  }

  public void setDefaultHandler(MessageHandler handler) {
    myDefaultHandler = handler;
  }

  public void disconnect() {
    myBus.notifyConnectionTerminated(this);
  }

  public void dispose() {
    disconnect();
  }

  public void deliverImmediately() {
    while (!myPendingMessages.get().isEmpty()) {
      myBus.deliverSingleMessage();
    }
  }

  void deliverMessage(Message message) {
    final Message messageOnLocalQueue = myPendingMessages.get().poll();
    assert messageOnLocalQueue == message;

    final Topic topic = message.getTopic();
    final Object handler = mySubscriptions.get(topic);
    
    if (handler == myDefaultHandler) {
      myDefaultHandler.handle(message.getListenerMethod(), message.getArgs());
    }
    else {
      try {
        message.getListenerMethod().invoke(handler, message.getArgs());
      }
      catch (AbstractMethodError e) {
        //Do nothing. This listener just does not implement something newly added yet.
      }
      catch(Throwable e) {
        LOG.error(e.getCause());
      }
    }
  }

  void scheduleMessageDelivery(Message message) {
    myPendingMessages.get().offer(message);
  }

  public String toString() {
    return mySubscriptions.keySet().toString();
  }
}