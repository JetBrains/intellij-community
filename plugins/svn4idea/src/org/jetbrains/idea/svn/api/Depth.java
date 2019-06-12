// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlEnumValue;
import java.util.HashMap;
import java.util.Map;

public enum Depth {

  @XmlEnumValue("") UNKNOWN("unknown"),
  @XmlEnumValue("infinity") INFINITY("infinity"),
  @XmlEnumValue("immediates") IMMEDIATES("immediates"),
  @XmlEnumValue("files") FILES("files"),
  @XmlEnumValue("empty") EMPTY("empty"),
  @XmlEnumValue("exclude") EXCLUDE("exclude");

  @NotNull private static final Map<String, Depth> ourAllDepths = new HashMap<>();

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
