/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.11.2006
 * Time: 19:28:19
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.AbstractCommonCheckinAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor;
import org.jetbrains.annotations.Nullable;

public class CreatePatchAction extends AbstractCommonCheckinAction {
  protected String getActionName(VcsContext dataContext) {
    return VcsBundle.message("create.patch.commit.action.title");
  }

  protected FilePath[] getRoots(VcsContext context) {
    return getAllContentRoots(context);
  }

  protected boolean filterRootsBeforeAction() {
    return false;
  }

  @Override @Nullable
  protected CommitExecutor getExecutor(Project project) {
    return CreatePatchCommitExecutor.getInstance(project);
  }
}