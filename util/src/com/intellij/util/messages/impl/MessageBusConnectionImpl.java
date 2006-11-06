/*
 * @author max
 */
package com.intellij.util.messages.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import com.intellij.util.messages.Topic;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class MessageBusConnectionImpl implements MessageBusConnection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.messages.impl.MessageBusConnectionImpl");

  private final MessageBusImpl myBus;
  private final Queue<Message> myPendingMessages = new LinkedList<Message>();
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
    while (!myPendingMessages.isEmpty()) {
      myBus.deliverSingleMessage();
    }
  }

  void deliverMessage(Message message) {
    final Message messageOnLocalQueue = myPendingMessages.poll();
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
      catch (RuntimeException e) {
        throw e;
      }
      catch (InvocationTargetException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException)cause;
        }
        else {
          LOG.error(cause);
        }
      }      
      catch(Exception e) {
        LOG.error(e.getCause());
      }
    }
  }

  void scheduleMessageDelivery(Message message) {
    myPendingMessages.offer(message);
  }

  public String toString() {
    return mySubscriptions.keySet().toString();
  }
}