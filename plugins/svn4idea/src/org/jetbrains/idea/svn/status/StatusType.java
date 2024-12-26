// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.status;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlEnumValue;
import java.util.HashMap;
import java.util.Map;

public enum StatusType {
  INAPPLICABLE("inapplicable"),
  UNKNOWN("unknown"),
  UNCHANGED("unchanged"),
  MISSING("missing"),
  OBSTRUCTED("obstructed"),
  CHANGED("changed"),
  @XmlEnumValue("merged") MERGED("merged"),
  CONFLICTED("conflicted"),

  @XmlEnumValue("none") STATUS_NONE("none"),
  @XmlEnumValue("normal") STATUS_NORMAL("normal", ' '),
  @XmlEnumValue("modified") STATUS_MODIFIED("modified", 'M'),
  @XmlEnumValue("added") STATUS_ADDED("added", 'A'),
  @XmlEnumValue("deleted") STATUS_DELETED("deleted", 'D'),
  @XmlEnumValue("unversioned") STATUS_UNVERSIONED("unversioned", '?'),
  @XmlEnumValue("missing") STATUS_MISSING("missing", '!'),
  @XmlEnumValue("replaced") STATUS_REPLACED("replaced", 'R'),
  @XmlEnumValue("conflicted") STATUS_CONFLICTED("conflicted", 'C'),
  @XmlEnumValue("obstructed") STATUS_OBSTRUCTED("obstructed", '~'),
  @XmlEnumValue("ignored") STATUS_IGNORED("ignored", 'I'),
  // directory is incomplete - checkout or update was interrupted
  @XmlEnumValue("incomplete") STATUS_INCOMPLETE("incomplete", '!'),
  @XmlEnumValue("external") STATUS_EXTERNAL("external", 'X');

  private static final @NonNls String STATUS_PREFIX = "STATUS_";

  private static final @NotNull Map<String, StatusType> ourStatusTypesForStatusOperation = new HashMap<>();

  static {
    for (StatusType action : values()) {
      register(action);
    }
  }

  private final String myName;
  private final char myCode;

  StatusType(String name) {
    this(name, ' ');
  }

  StatusType(String name, char code) {
    myName = name;
    myCode = code;
  }

  public char getCode() {
    return myCode;
  }

  @Override
  public String toString() {
    return myName;
  }

  private static void register(@NotNull StatusType action) {
    if (action.name().startsWith(STATUS_PREFIX)) {
      ourStatusTypesForStatusOperation.put(action.myName, action);
    }
  }

  public static @Nullable StatusType forStatusOperation(@NotNull String statusName) {
    return ourStatusTypesForStatusOperation.get(statusName);
  }
}
