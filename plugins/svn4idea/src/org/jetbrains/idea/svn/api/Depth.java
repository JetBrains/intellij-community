/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.api;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public enum Depth {

  UNKNOWN("unknown"),
  INFINITY("infinity"),
  IMMEDIATES("immediates"),
  FILES("files"),
  EMPTY("empty");

  @NotNull private static final Map<String, Depth> ourAllDepths = ContainerUtil.newHashMap();

  static {
    for (Depth action : Depth.values()) {
      register(action);
    }
  }

  @NotNull private final String myName;

  Depth(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return myName;
  }

  private static void register(@NotNull Depth depth) {
    ourAllDepths.put(depth.myName, depth);
  }

  @NotNull
  public static Depth from(@NotNull String depthName) {
    Depth result = ourAllDepths.get(depthName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown depth " + depthName);
    }

    return result;
  }

  @NotNull
  public static Depth allOrFiles(boolean recursive) {
    return recursive ? INFINITY : FILES;
  }

  @NotNull
  public static Depth allOrEmpty(boolean recursive) {
    return recursive ? INFINITY : EMPTY;
  }

  public static boolean isRecursive(@Nullable Depth depth) {
    return depth == null || depth == INFINITY || depth == UNKNOWN;
  }
}
