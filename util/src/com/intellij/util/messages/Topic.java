/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2006
 * Time: 9:49:16 PM
 */
package com.intellij.util.messages;

public class Topic<L> {
  private Class<L> myListenerClass;

  public Topic(final Class<L> listenerClass) {
    myListenerClass = listenerClass;
  }
}