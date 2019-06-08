// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull private static final Map<String, ConflictAction> ourAllActions = new HashMap<>();

  static {
    for (ConflictAction action : ConflictAction.values()) {
      register(action);
    }
  }

  @NotNull private final String myKey;
  @NotNull private final String[] myOtherKeys;

  ConflictAction(@NotNull String key, @NotNull String... otherKeys) {
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

  @NotNull
  public static ConflictAction from(@NotNull String actionName) {
    ConflictAction result = ourAllActions.get(actionName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown conflict action " + actionName);
    }

    return result;
  }
}
