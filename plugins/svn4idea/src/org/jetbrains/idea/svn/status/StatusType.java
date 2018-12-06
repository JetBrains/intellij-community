// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public enum StatusType {
  INAPPLICABLE("inapplicable"),
  UNKNOWN("unknown"),
  UNCHANGED("unchanged"),
  MISSING("missing"),
  OBSTRUCTED("obstructed"),
  CHANGED("changed"),
  MERGED("merged"),
  CONFLICTED("conflicted"),

  STATUS_NONE("none"),
  STATUS_NORMAL("normal", ' '),
  STATUS_MODIFIED("modified", 'M'),
  STATUS_ADDED("added", 'A'),
  STATUS_DELETED("deleted", 'D'),
  STATUS_UNVERSIONED("unversioned", '?'),
  STATUS_MISSING("missing", '!'),
  STATUS_REPLACED("replaced", 'R'),
  STATUS_CONFLICTED("conflicted", 'C'),
  STATUS_OBSTRUCTED("obstructed", '~'),
  STATUS_IGNORED("ignored", 'I'),
  // directory is incomplete - checkout or update was interrupted
  STATUS_INCOMPLETE("incomplete", '!'),
  STATUS_EXTERNAL("external", 'X');

  private static final String STATUS_PREFIX = "STATUS_";

  @NotNull private static final Map<String, StatusType> ourOtherStatusTypes = ContainerUtil.newHashMap();
  @NotNull private static final Map<String, StatusType> ourStatusTypesForStatusOperation = ContainerUtil.newHashMap();

  static {
    for (StatusType action : StatusType.values()) {
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

  public String toString() {
    return myName;
  }

  private static void register(@NotNull StatusType action) {
    (action.name().startsWith(STATUS_PREFIX) ? ourStatusTypesForStatusOperation : ourOtherStatusTypes).put(action.myName, action);
  }

  @Nullable
  public static StatusType forStatusOperation(@NotNull String statusName) {
    return ourStatusTypesForStatusOperation.get(statusName);
  }
}
