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
