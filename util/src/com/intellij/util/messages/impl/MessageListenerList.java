package com.intellij.util.messages.impl;

import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class MessageListenerList<T> {
  private final MessageBus myMessageBus;
  private final Topic<T> myTopic;
  private final Map<T, MessageBusConnection> myListenerToConnectionMap = new HashMap<T, MessageBusConnection>();

  public MessageListenerList(MessageBus messageBus, Topic<T> topic) {
    myTopic = topic;
    myMessageBus = messageBus;
  }

  public void add(T listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(myTopic, listener);
    myListenerToConnectionMap.put(listener, connection);
  }

  public void add(final T listener, Disposable parentDisposable) {
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        myListenerToConnectionMap.remove(listener);
      }
    });
    final MessageBusConnection connection = myMessageBus.connect(parentDisposable);
    connection.subscribe(myTopic, listener);
    myListenerToConnectionMap.put(listener, connection);
  }

  public void remove(T listener) {
    final MessageBusConnection connection = myListenerToConnectionMap.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }
}
