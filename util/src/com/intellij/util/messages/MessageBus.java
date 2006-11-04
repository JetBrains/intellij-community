/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2006
 * Time: 9:48:08 PM
 */
package com.intellij.util.messages;

public interface MessageBus {
  MessageBusConnection connectWeakly();
  MessageBusConnection connectStrongly();

  <L> L syncPublisher(Topic<L> topic);
  <L> L asyncPublisher(Topic<L> topic);

  void dispose();
}