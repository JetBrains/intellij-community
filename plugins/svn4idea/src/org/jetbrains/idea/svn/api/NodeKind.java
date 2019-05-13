// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import java.util.Map;

@XmlEnum
public enum NodeKind {

  // see comments in LogEntryPath.Builder for cases when "" kind could appear
  @XmlEnumValue("") UNKNOWN("unknown"),
  @XmlEnumValue("file") FILE("file"),
  @XmlEnumValue("dir") DIR("dir"),
  // used in ConflictVersion when node is missing
  @XmlEnumValue("none") NONE("none");

  @NotNull private static final Map<String, NodeKind> ourAllNodeKinds = ContainerUtil.newHashMap();

  static {
    for (NodeKind kind : NodeKind.values()) {
      register(kind);
    }
    ourAllNodeKinds.put("", UNKNOWN);
  }

  @NotNull private final String myKey;

  NodeKind(@NotNull String key) {
    myKey = key;
  }

  public boolean isFile() {
    return FILE.equals(this);
  }

  public boolean isDirectory() {
    return DIR.equals(this);
  }

  public boolean isNone() {
    return NONE.equals(this);
  }

  @Override
  public String toString() {
    return myKey;
  }

  private static void register(@NotNull NodeKind kind) {
    ourAllNodeKinds.put(kind.myKey, kind);
  }

  @NotNull
  public static NodeKind from(@NotNull String nodeKindName) {
    NodeKind result = ourAllNodeKinds.get(nodeKindName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown node kind " + nodeKindName);
    }

    return result;
  }

  @NotNull
  public static NodeKind from(boolean isDirectory) {
    return isDirectory ? DIR : FILE;
  }
}
