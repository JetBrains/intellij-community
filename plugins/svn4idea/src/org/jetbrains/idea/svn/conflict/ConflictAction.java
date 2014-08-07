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
public enum ConflictAction {

  EDIT("edit", "edited"),
  ADD("add", "added"),
  DELETE("delete", "deleted"),
  REPLACE("replace", "replaced");

  @NotNull private static final Map<String, ConflictAction> ourAllActions = ContainerUtil.newHashMap();

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
