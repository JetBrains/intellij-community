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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.VcsBackgroundTask;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.checkin.SvnCheckinEnvironment;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.util.*;

public class AddAction extends BasicAction {
  static final Logger log = Logger.getInstance("org.jetbrains.idea.svn.action.AddAction");

  protected String getActionName(AbstractVcs vcs) {
    log.debug("enter: getActionName");
    return SvnBundle.message("action.name.add.files", vcs.getName());
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    return SvnStatusUtil.fileCanBeAdded(project, file);
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void batchPerform(final Project project, final SvnVcs activeVcs, final VirtualFile[] files, DataContext context)
    throws VcsException {
    log.debug("enter: batchPerform");

    addFiles(project, activeVcs, files);
  }

  private void addFiles(final Project project, final SvnVcs activeVcs, final VirtualFile[] files) {
    // passed parameter serves only for ""
    VcsBackgroundTask task =
      new VcsBackgroundTask<VirtualFile[]>(project, getActionName(activeVcs), BackgroundFromStartOption.getInstance(),
                                            Collections.singleton(files), true) {
        @Override
        protected void process(VirtualFile[] items) throws VcsException {
          ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
          manager.startBackgroundVcsOperation();
          try {

            SVNWCClient wcClient = activeVcs.createWCClient();

            Collection<SVNException> exceptions =
              SvnCheckinEnvironment.scheduleUnversionedFilesForAddition(wcClient, Arrays.asList(items), true);
            markDirty(project, items);
            if (!exceptions.isEmpty()) {
              final Collection<String> messages = new ArrayList<String>(exceptions.size());
              for (SVNException exception : exceptions) {
                messages.add(exception.getMessage());
              }
              throw new VcsException(messages);
            }
          } finally {
            manager.stopBackgroundVcsOperation();
          }
        }
      };
    ProgressManager.getInstance().run(task);
  }

  private void markDirty(Project project, VirtualFile[] items) {
    final List<VirtualFile> vDirs = new ArrayList<VirtualFile>();
    final List<VirtualFile> vFiles = new ArrayList<VirtualFile>();

    for (VirtualFile file : items) {
      if (file.isDirectory()) {
        vDirs.add(file);
      } else {
        vFiles.add(file);
      }
    }
    VcsDirtyScopeManager.getInstance(project).filesDirty(vFiles, vDirs);
  }

  @Override
  protected boolean witeLocalHistory() {
    return false;
  }

  protected boolean isBatchAction() {
    log.debug("enter: isBatchAction");
    return true;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context)
    throws VcsException {
    addFiles(project, activeVcs, new VirtualFile[] {file});
  }
}
