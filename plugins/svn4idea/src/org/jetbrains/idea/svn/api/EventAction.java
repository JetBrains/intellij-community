// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

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

  private final String myKey;

  EventAction(String key) {
    myKey = key;
  }

  @Override
  public String toString() {
    return myKey;
  }
}
