/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2006
 * Time: 9:49:16 PM
 */
package com.intellij.util.messages;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class Topic<L> {
  private final String myDisplayName;
  private final Class<L> myListenerClass;

  public Topic(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass) {
    myDisplayName = displayName;
    myListenerClass = listenerClass;
  }

  @NotNull
  @NonNls
  public String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  public Class<L> getListenerClass() {
    return myListenerClass;
  }

  public String toString() {
    return myDisplayName;
  }
}