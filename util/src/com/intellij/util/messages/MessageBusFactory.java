/*
 * @author max
 */
package com.intellij.util.messages;

import com.intellij.util.messages.impl.MessageBusImpl;

public class MessageBusFactory {
  private MessageBusFactory() {}

  public static MessageBus newMessageBus(Object owner) {
    return new MessageBusImpl(owner, null);
  }

  public static MessageBus newMessageBus(Object owner, MessageBus parentBus) {
    return new MessageBusImpl(owner, parentBus);
  }
}