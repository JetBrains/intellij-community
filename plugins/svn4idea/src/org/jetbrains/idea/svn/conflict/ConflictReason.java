// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.conflict;

import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlEnumValue;
import java.util.HashMap;
import java.util.Map;

public enum ConflictReason {

  @XmlEnumValue("edit") EDITED("edit", "edited"),
  @XmlEnumValue("obstruction") OBSTRUCTED("obstruction", "obstruct", "obstructed"),
  @XmlEnumValue("delete") DELETED("delete", "deleted"),
  @XmlEnumValue("missing") MISSING("missing", "miss"),
  @XmlEnumValue("unversioned") UNVERSIONED("unversioned", "unversion"),

  @XmlEnumValue("add") ADDED("add", "added"),

  @XmlEnumValue("replace") REPLACED("replace", "replaced"),

  @XmlEnumValue("moved-away") MOVED_AWAY("moved-away"),
  @XmlEnumValue("moved-here") MOVED_HERE("moved-here");

  @NotNull private static final Map<String, ConflictReason> ourAllReasons = new HashMap<>();

  static {
    for (ConflictReason reason : ConflictReason.values()) {
      register(reason);
    }
  }

  @NotNull private final String myKey;
  @NotNull private final String[] myOtherKeys;

  ConflictReason(@NotNull String key, @NotNull String... otherKeys) {
    myKey = key;
    myOtherKeys = otherKeys;
  }

  @Override
  public String toString() {
    return myKey;
  }

  private static void register(@NotNull ConflictReason reason) {
    ourAllReasons.put(reason.myKey, reason);

    for (String key : reason.myOtherKeys) {
      ourAllReasons.put(key, reason);
    }
  }

  @NotNull
  public static ConflictReason from(@NotNull String reasonName) {
    ConflictReason result = ourAllReasons.get(reasonName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown conflict reason " + reasonName);
    }

    return result;
  }
}
