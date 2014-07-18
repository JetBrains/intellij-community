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
package org.jetbrains.idea.svn.conflict;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public enum ConflictReason {

  EDITED("edit", "edited"),
  OBSTRUCTED("obstruction", "obstruct", "obstructed"),
  DELETED("delete", "deleted"),
  MISSING("missing", "miss"),
  UNVERSIONED("unversioned", "unversion"),

  /**
   * @since 1.6
   */
  ADDED("add", "added"),

  /**
   * @since 1.7
   */
  REPLACED("replace", "replaced"),

  /**
   * @since 1.8
   */
  MOVED_AWAY("moved-away"),
  MOVED_HERE("moved-here");

  @NotNull private static final Map<String, ConflictReason> ourAllReasons = ContainerUtil.newHashMap();

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
