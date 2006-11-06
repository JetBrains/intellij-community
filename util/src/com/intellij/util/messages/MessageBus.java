/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2006
 * Time: 9:48:08 PM
 */
package com.intellij.util.messages;

import com.intellij.openapi.Disposable;

public interface MessageBus {
  MessageBusConnection connectWeakly();
  MessageBusConnection connectStrongly();
  MessageBusConnection connectStrongly(Disposable parentDisposable);

  <L> L syncPublisher(Topic<L> topic);
  <L> L asyncPublisher(Topic<L> topic);

  void dispose();
}