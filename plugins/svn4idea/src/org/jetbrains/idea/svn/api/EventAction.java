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

import java.util.Map;

public enum EventAction {

  // currently used to represent some not used event action from SVNKit
  UNKNOWN("unknown"),

  ADD("add"),
  DELETE("delete"),
  RESTORE("restore"),
  REVERT("revert"),
  FAILED_REVERT("failed_revert"),
  SKIP("skip"),

  UPDATE_DELETE("update_delete"),
  UPDATE_ADD("update_add"),
  UPDATE_UPDATE("update_update"),
  UPDATE_NONE("update_none"),
  UPDATE_COMPLETED("update_completed"),
  UPDATE_EXTERNAL("update_external"),
  UPDATE_SKIP_OBSTRUCTION("update_skip_obstruction"),
  UPDATE_STARTED("update_started"),

  COMMIT_MODIFIED("commit_modified"),
  COMMIT_ADDED("commit_added"),
  COMMIT_DELETED("commit_deleted"),
  COMMIT_REPLACED("commit_replaced"),
  COMMIT_DELTA_SENT("commit_delta_sent"),
  FAILED_OUT_OF_DATE("failed_out_of_date"),

  LOCKED("locked"),
  UNLOCKED("unlocked"),
  LOCK_FAILED("lock_failed"),
  UNLOCK_FAILED("unlock_failed"),

  UPGRADED_PATH("upgraded_path"),

  TREE_CONFLICT("tree_conflict");

  @NotNull private static final Map<String, EventAction> ourAllActions = ContainerUtil.newHashMap();

  static {
    for (EventAction action : EventAction.values()) {
      register(action);
    }
  }

  private String myKey;

  EventAction(String key) {
    myKey = key;
  }

  public String toString() {
    return myKey;
  }

  private static void register(@NotNull EventAction action) {
    ourAllActions.put(action.myKey, action);
  }
}
