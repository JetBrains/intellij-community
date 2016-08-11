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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class CleanupWorker {
  protected VirtualFile[] myRoots;
  private final Project myProject;
  private final String myTitleKey;

  public CleanupWorker(final VirtualFile[] roots, final Project project, final String titleKey) {
    myRoots = roots;
    myProject = project;
    myTitleKey = titleKey;
  }

  public void execute() {
    ApplicationManager.getApplication().saveAll();

    chanceToFillRoots();
    if (myRoots.length == 0) return;

    final List<Pair<VcsException, VirtualFile>> exceptions = new LinkedList<>();
    final SvnVcs vcs = SvnVcs.getInstance(myProject);

    final Task.Backgroundable task = new Task.Backgroundable(myProject, SvnBundle.message(myTitleKey), true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        VirtualFile currentRoot;
        for (VirtualFile root : myRoots) {
          currentRoot = root;
          try {
            final File path = new File(root.getPath());

            indicator.setText(SvnBundle.message("action.Subversion.cleanup.progress.text", path));
            ProgressTracker handler = new ProgressTracker() {
              @Override
              public void consume(ProgressEvent event) throws SVNException {
              }

              @Override
              public void checkCancelled() throws SVNCancelException {
                if (indicator.isCanceled()) throw new SVNCancelException();
              }
            };

            vcs.getFactory(path).createCleanupClient().cleanup(path, handler);
          }
          catch (VcsException ex) {
            exceptions.add(Pair.create(ex, currentRoot));
          }
        }
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        if (myProject.isDisposed()) {
          return;
        }
        final VcsDirtyScopeManager manager = VcsDirtyScopeManager.getInstance(myProject);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                if (myProject.isDisposed()) {
                  return;
                }
                for (final VirtualFile root : myRoots) {
                  root.refresh(false, true);
                }
              }
            });
          }
        });
        for (final VirtualFile root : myRoots) {
          if (root.isDirectory()) {
            manager.dirDirtyRecursively(root);
          } else {
            manager.fileDirty(root);
          }
        }

        if (! exceptions.isEmpty()) {
          final List<VcsException> vcsExceptions = new LinkedList<>();
          for (Pair<VcsException, VirtualFile> pair : exceptions) {
            final VcsException exception = pair.first;
            vcsExceptions.add(new VcsException(SvnBundle.message("action.Subversion.cleanup.error.message",
                                                              FileUtil.toSystemDependentName(pair.second.getPath()),
                                                              ((exception == null) ? "" : exception.getMessage()))));
          }
          final AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
          helper.showErrors(vcsExceptions, SvnBundle.message(myTitleKey));
        }
      }
    };

    ProgressManager.getInstance().run(task);
  }

  protected void chanceToFillRoots() {
  }
}
