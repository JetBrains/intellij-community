/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  private final BroadcastDirection myBroadcastDirection;

  public Topic(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass) {
    this(displayName, listenerClass, BroadcastDirection.TO_CHILDREN);
  }

  public Topic(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass, final BroadcastDirection broadcastDirection) {
    myDisplayName = displayName;
    myListenerClass = listenerClass;
    myBroadcastDirection = broadcastDirection;
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

  public static <L> Topic<L> create(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass) {
    return new Topic<L>(displayName, listenerClass);
  }

  public BroadcastDirection getBroadcastDirection() {
    return myBroadcastDirection;
  }

  public enum BroadcastDirection {
    TO_CHILDREN,
    NONE,
    TO_PARENT
  }
}