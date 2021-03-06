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
package org.jetbrains.idea.svn.ignore;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;

import static com.intellij.util.ArrayUtil.isEmpty;

public class IgnoreGroupHelperAction {
  private boolean myAllCanBeIgnored;
  private boolean myAllAreIgnored;
  private FileIterationListener myListener;

  public void update(@NotNull AnActionEvent e) {
    myAllAreIgnored = true;
    myAllCanBeIgnored = true;

    // TODO: This logic was taken from BasicAction.update(). Probably it'll be more convenient to share these conditions for correctness.
    Project project = e.getProject();
    SvnVcs vcs = project != null ? SvnVcs.getInstance(project) : null;
    VirtualFile[] files = getSelectedFiles(e);
    boolean enabledAndVisible = project != null && vcs != null && !isEmpty(files) && isEnabled(vcs, files);

    e.getPresentation().setEnabledAndVisible(enabledAndVisible);
  }

  private VirtualFile @Nullable [] getSelectedFiles(@NotNull AnActionEvent e) {
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP)) {
      Iterable<VirtualFile> exactlySelectedFiles = e.getData(ChangesListView.EXACTLY_SELECTED_FILES_DATA_KEY);
      if (exactlySelectedFiles != null) {
        return JBIterable.from(exactlySelectedFiles).toList().toArray(VirtualFile[]::new);
      }
    }
    return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
  }

  protected boolean isEnabled(@NotNull SvnVcs vcs, VirtualFile @NotNull [] files) {
    return ProjectLevelVcsManager.getInstance(vcs.getProject()).checkAllFilesAreUnder(vcs, files) &&
           ContainerUtil.and(files, file -> isEnabled(vcs, file));
  }

  public void setFileIterationListener(FileIterationListener listener) {
    myListener = listener;
  }

  private boolean isEnabledImpl(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    if (SvnStatusUtil.isIgnoredInAnySense(vcs.getProject(), file)) {
      myAllCanBeIgnored = false;
      return myAllAreIgnored;
    }
    else if (ChangeListManager.getInstance(vcs.getProject()).isUnversioned(file)) {
      VirtualFile parent = file.getParent();
      if (parent != null && SvnStatusUtil.isUnderControl(vcs, parent)) {
        myAllAreIgnored = false;
        return myAllCanBeIgnored;
      }
    }
    myAllCanBeIgnored = false;
    myAllAreIgnored = false;
    return false;
  }

  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    boolean result = isEnabledImpl(vcs, file);
    if (result) {
      myListener.onFileEnabled(file);
    }
    return result;
  }

  public boolean allCanBeIgnored() {
    return myAllCanBeIgnored;
  }

  public boolean allAreIgnored() {
    return myAllAreIgnored;
  }
}
