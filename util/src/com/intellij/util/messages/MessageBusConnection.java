/*
 * @author max
 */
package com.intellij.util.messages;

import com.intellij.openapi.Disposable;

public interface MessageBusConnection extends Disposable {
  <L> void subscribe(Topic<L> topic, L handler);
  <L> void subscribe(Topic<L> topic);

  void setDefaultHandler(MessageHandler handler);

  void deliverImmediately();
  void disconnect();
}
