/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2006
 * Time: 9:49:16 PM
 */
package com.intellij.util.messages;

public class Topic<L> {
  private final String myDisplayName;
  private Class<L> myListenerClass;

  public Topic(String displayName, final Class<L> listenerClass) {
    myDisplayName = displayName;
    myListenerClass = listenerClass;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public Class<L> getListenerClass() {
    return myListenerClass;
  }
}