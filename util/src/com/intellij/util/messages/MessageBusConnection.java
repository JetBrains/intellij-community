/*
 * @author max
 */
package com.intellij.util.messages;

public interface MessageBusConnection {
  <L> void subscribe(Topic<L> topic, L handler);
  <L> void subscribe(Topic<L> topic);

  void setDefaultHandler(MessageHandler handler);

  void deliverImmediately();
  void disconnect();
}
