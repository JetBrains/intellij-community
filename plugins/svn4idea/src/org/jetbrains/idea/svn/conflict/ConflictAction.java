// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.conflict;

import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlEnumValue;
import java.util.HashMap;
import java.util.Map;

public enum ConflictAction {

  @XmlEnumValue("edit") EDIT("edit", "edited"),
  @XmlEnumValue("add") ADD("add", "added"),
  @XmlEnumValue("delete") DELETE("delete", "deleted"),
  @XmlEnumValue("replace") REPLACE("replace", "replaced");

  private static final @NotNull Map<String, ConflictAction> ourAllActions = new HashMap<>();

  static {
    for (ConflictAction action : ConflictAction.values()) {
      register(action);
    }
  }

  private final @NotNull String myKey;
  private final String @NotNull [] myOtherKeys;

  ConflictAction(@NotNull String key, String @NotNull ... otherKeys) {
    myKey = key;
    myOtherKeys = otherKeys;
  }

  @Override
  public String toString() {
    return myKey;
  }

  private static void register(@NotNull ConflictAction action) {
    ourAllActions.put(action.myKey, action);

    for (String otherKey : action.myOtherKeys) {
      ourAllActions.put(otherKey, action);
    }
  }

  public static @NotNull ConflictAction from(@NotNull String actionName) {
    ConflictAction result = ourAllActions.get(actionName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown conflict action " + actionName);
    }

    return result;
  }
}
