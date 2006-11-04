/*
 * @author max
 */
package com.intellij.util.messages;

import com.intellij.util.messages.impl.MessageBusImpl;

public class Messages {
  private Messages() {}

  public static MessageBus newMessageBus() {
    return new MessageBusImpl(null);
  }

  public static MessageBus newMessageBus(MessageBus parentBus) {
    return new MessageBusImpl(parentBus);
  }
}