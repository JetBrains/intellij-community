/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class CreatePatchAction extends AbstractCommitChangesAction {
  protected String getActionName(VcsContext dataContext) {
    return VcsBundle.message("create.patch.commit.action.title");
  }

  @Override @Nullable
  protected CommitExecutor getExecutor(Project project) {
    return CreatePatchCommitExecutor.getInstance(project);
  }
}