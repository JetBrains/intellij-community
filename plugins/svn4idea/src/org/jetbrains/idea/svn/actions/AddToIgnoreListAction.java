/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.ignore.IgnoreInfoGetter;
import org.jetbrains.idea.svn.ignore.SvnPropertyService;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class AddToIgnoreListAction extends BasicAction {
  private String myActionName;
  private final boolean myUseCommonExtension;
  @NotNull private final IgnoreInfoGetter myInfoGetter;

  public AddToIgnoreListAction(@NotNull IgnoreInfoGetter infoGetter, boolean useCommonExtension) {
    myInfoGetter = infoGetter;
    myUseCommonExtension = useCommonExtension;
  }

  public void setActionText(String name) {
    myActionName = name;
  }

  @NotNull
  @Override
  protected String getActionName() {
    return message("action.name.ignore.files");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    presentation.setEnabledAndVisible(true);
    presentation.setText(myActionName, false);
    presentation.setDescription(message(
      myUseCommonExtension ? "action.Subversion.Ignore.MatchExtension.description" : "action.Subversion.Ignore.ExactMatch.description",
      myActionName));
  }

  @Override
  protected void doVcsRefresh(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(vcs.getProject());
    if (file.getParent() != null) {
      vcsDirtyScopeManager.fileDirty(file.getParent());
    }
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return true;
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) {
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) throws VcsException {
    SvnPropertyService.doAddToIgnoreProperty(vcs, myUseCommonExtension, files, myInfoGetter);
  }

  protected boolean isBatchAction() {
    return true;
  }
}
