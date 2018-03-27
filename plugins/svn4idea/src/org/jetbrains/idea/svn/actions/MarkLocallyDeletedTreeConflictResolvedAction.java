/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnLocallyDeletedChange;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;

import java.util.Collections;
import java.util.List;

/**
 * @author irengrig
 */
public class MarkLocallyDeletedTreeConflictResolvedAction extends AnAction {
  public MarkLocallyDeletedTreeConflictResolvedAction() {
    super(SvnBundle.message("action.mark.tree.conflict.resolved.text"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final MyLocallyDeletedChecker locallyDeletedChecker = new MyLocallyDeletedChecker(e);
    if (! locallyDeletedChecker.isEnabled()) return;

    final String markText = SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.title");
    final Project project = locallyDeletedChecker.getProject();
    final int result = Messages.showYesNoDialog(project,
                                                SvnBundle.message("action.mark.tree.conflict.resolved.confirmation.text"), markText,
                                                Messages.getQuestionIcon());
    if (result == Messages.YES) {
      final Ref<VcsException> exception = new Ref<>();
      ProgressManager.getInstance().run(new Task.Backgroundable(project, markText, true) {
        public void run(@NotNull ProgressIndicator indicator) {
          resolveLocallyDeletedTextConflict(locallyDeletedChecker, exception);
        }
      });
      if (! exception.isNull()) {
        AbstractVcsHelper.getInstance(project).showError(exception.get(), markText);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final MyLocallyDeletedChecker locallyDeletedChecker = new MyLocallyDeletedChecker(e);
    e.getPresentation().setVisible(locallyDeletedChecker.isEnabled());
    e.getPresentation().setEnabled(locallyDeletedChecker.isEnabled());
    //e.getPresentation().setText(SvnBundle.message("action.mark.tree.conflict.resolved.text"));
  }

  private void resolveLocallyDeletedTextConflict(MyLocallyDeletedChecker checker, Ref<VcsException> exception) {
    final FilePath path = checker.getPath();
    resolve(checker.getProject(), exception, path);
    VcsDirtyScopeManager.getInstance(checker.getProject()).filePathsDirty(Collections.singletonList(path), null);
  }

  private void resolve(Project project, Ref<VcsException> exception, FilePath path) {
    SvnVcs vcs = SvnVcs.getInstance(project);

    try {
      vcs.getFactory(path.getIOFile()).createConflictClient().resolve(path.getIOFile(), Depth.EMPTY, false, false, true);
    }
    catch (VcsException e) {
      exception.set(e);
    }
  }

  private static class MyLocallyDeletedChecker {
    private final boolean myEnabled;
    private final FilePath myPath;
    private final Project myProject;

    public MyLocallyDeletedChecker(final AnActionEvent e) {
      final DataContext dc = e.getDataContext();
      myProject = CommonDataKeys.PROJECT.getData(dc);
      if (myProject == null) {
        myPath = null;
        myEnabled = false;
        return;
      }

      final List<LocallyDeletedChange> missingFiles = e.getData(ChangesListView.LOCALLY_DELETED_CHANGES);

      if (missingFiles == null || missingFiles.isEmpty()) {
        myPath = null;
        myEnabled = false;
        return;
      }
      /*if (missingFiles == null || missingFiles.size() != 1) {
        final Change[] changes = e.getData(VcsDataKeys.CHANGES);
        if (changes == null || changes.length != 1 || changes[0].getAfterRevision() != null) {
          myPath = null;
          myEnabled = false;
          return;
        }
        myEnabled = changes[0] instanceof ConflictedSvnChange && ((ConflictedSvnChange) changes[0]).getConflictState().isTree();
        if (myEnabled) {
          myPath = changes[0].getBeforeRevision().getFile();
        } else {
          myPath = null;
        }
        return;
      } */

      final LocallyDeletedChange change = missingFiles.get(0);
      myEnabled = change instanceof SvnLocallyDeletedChange && ((SvnLocallyDeletedChange) change).getConflictState().isTree();
      if (myEnabled) {
        myPath = change.getPath();
      }
      else {
        myPath = null;
      }
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public FilePath getPath() {
      return myPath;
    }

    public Project getProject() {
      return myProject;
    }
  }
}
