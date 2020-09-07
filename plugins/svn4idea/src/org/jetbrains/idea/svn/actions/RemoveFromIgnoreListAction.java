// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.ignore.IgnoreInfoGetter;
import org.jetbrains.idea.svn.ignore.SvnPropertyService;

public class RemoveFromIgnoreListAction extends BasicAction {
  private @ActionText String myActionName;
  private final boolean myUseCommonExtension;
  @NotNull private final IgnoreInfoGetter myInfoGetter;

  public RemoveFromIgnoreListAction(boolean useCommonExtension, @NotNull IgnoreInfoGetter getter) {
    myUseCommonExtension = useCommonExtension;
    myInfoGetter = getter;
  }

  public void setActionText(@ActionText @NotNull String name) {
    myActionName = name;
  }

  @NotNull
  @Override
  protected String getActionName() {
    return SvnBundle.message("action.name.undo.ignore.files");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    presentation.setEnabledAndVisible(true);
    presentation.setText(myActionName);
    presentation.setDescription(SvnBundle.messagePointer("action.Subversion.UndoIgnore.description"));
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return true;
  }

  @Override
  protected void doVcsRefresh(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(vcs.getProject());
    if (file.getParent() != null) {
      vcsDirtyScopeManager.fileDirty(file.getParent());
    }
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) {
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, VirtualFile @NotNull [] files, @NotNull DataContext context) throws VcsException {
    SvnPropertyService.doRemoveFromIgnoreProperty(vcs, myUseCommonExtension, files, myInfoGetter);
  }

  @Override
  protected boolean isBatchAction() {
    return true;
  }
}
