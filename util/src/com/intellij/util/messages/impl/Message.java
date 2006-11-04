/*
 * @author max
 */
package com.intellij.util.messages.impl;

import com.intellij.util.messages.Topic;

import java.lang.reflect.Method;

public final class Message {
  private Topic myTopic;
  private Method myListenerMethod;
  private Object[] myArgs;

  public Message(final Topic topic, final Method listenerMethod, final Object[] args) {
    myTopic = topic;
    myListenerMethod = listenerMethod;
    myArgs = args;
  }

  public Topic getTopic() {
    return myTopic;
  }

  public Method getListenerMethod() {
    return myListenerMethod;
  }

  public Object[] getArgs() {
    return myArgs;
  }
}