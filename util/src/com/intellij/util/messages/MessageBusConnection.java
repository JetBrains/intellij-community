/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2006
 * Time: 9:48:27 PM
 */
package com.intellij.util.messages;

public interface MessageBusConnection {
  <L> void subscribe(Topic<L> topic, L handler);

  void deliverInIdleTime();
  
  void terminateConnection();
}
