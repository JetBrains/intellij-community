/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 21.11.2006
 * Time: 14:17:13
 */
package com.intellij.openapi.diff.impl.patch;

public enum ApplyPatchStatus {
  SUCCESS, PARTIAL, ALREADY_APPLIED, FAILURE;

  public static ApplyPatchStatus and(ApplyPatchStatus lhs, ApplyPatchStatus rhs) {
    if (lhs == null) return rhs;
    if (rhs == null) return lhs;
    if (lhs == FAILURE || rhs == FAILURE) return FAILURE;
    if (lhs == SUCCESS && rhs == SUCCESS) return SUCCESS;
    if (lhs == ALREADY_APPLIED && rhs == ALREADY_APPLIED) return ALREADY_APPLIED;
    return PARTIAL;
  }
}