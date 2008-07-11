/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;


public abstract class AbstractCommonCheckinAction extends AbstractVcsAction {
  private static final int MIXED = 0;
  protected static final int DIRECTORIES = 1;
  private static final int FILES = 2;

  public void actionPerformed(final VcsContext context) {
    final Project project = context.getProject();

    if (project == null) return;

    FilePath[] roots = filterDescindingFiles(getRoots(context), project);

    if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().saveAll();
    }

    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.ensureUpToDate(true)) {
      LocalChangeList initialSelection = getInitiallySelectedChangeList(context, project);

      Change[] changes = context.getSelectedChanges();
      if (changes != null && changes.length > 0) {
        Collection<Change> changeCollection = new ArrayList<Change>();
        Collections.addAll(changeCollection, changes);
        CommitChangeListDialog.commitChanges(project, changeCollection, initialSelection, getExecutor(project), null);
      }
      else {
        CommitChangeListDialog.commitPaths(project, Arrays.asList(roots), initialSelection, getExecutor(project), null);
      }
    }
  }

  @Nullable
  protected CommitExecutor getExecutor(Project project) {
    return null;
  }

  @Nullable
  protected LocalChangeList getInitiallySelectedChangeList(final VcsContext context, final Project project) {
    LocalChangeList initialSelection;
    ChangeList[] selectedChangeLists = context.getSelectedChangeLists();
    if (selectedChangeLists != null && selectedChangeLists.length > 0) {
      // convert copy to real
      initialSelection = ChangeListManager.getInstance(project).findChangeList(selectedChangeLists [0].getName());
    }
    else {
      Change[] selectedChanges = context.getSelectedChanges();
      if (selectedChanges != null && selectedChanges.length > 0) {
        initialSelection = ChangeListManager.getInstance(project).getChangeList(selectedChanges [0]);
      }
      else {
        initialSelection = ChangeListManager.getInstance(project).getDefaultChangeList();
      }
    }
    return initialSelection;
  }

  @Nullable
  protected static AbstractVcs getCommonVcsFor(FilePath[] roots, Project project) {
    if (roots.length == 0) return null;
    AbstractVcs firstVcs = VcsUtil.getVcsFor(project, roots[0]);
    if (firstVcs == null) return null;

    for (FilePath file : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      if (vcs == null) return null;
      if (firstVcs != vcs) {
        return null;
      }
    }
    return firstVcs;
  }

  protected static int getCheckinType(FilePath[] roots) {
    if (roots.length == 0) return MIXED;
    FilePath file = roots[0];
    int firstType = getCheckinType(file);

    for (FilePath root : roots) {
      int checkinType = getCheckinType(root);
      if (checkinType != firstType) return MIXED;
    }

    return firstType;
  }

  private static int getCheckinType(FilePath file) {
    return file.isDirectory() ? DIRECTORIES : FILES;
  }

  protected abstract String getActionName(VcsContext dataContext);

  protected abstract FilePath[] getRoots(VcsContext dataContext);

  protected void update(VcsContext vcsContext, Presentation presentation) {
    Project project = vcsContext.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    if (ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    FilePath[] roots = filterRoots(getRoots(vcsContext), project);
    if (roots == null || roots.length == 0) {
      presentation.setEnabled(false);
      return;
    }

    int checkinType = getCheckinType(roots);
    if (checkinType == DIRECTORIES) {
      if (roots.length == 0) {
        presentation.setEnabled(false);
        return;
      }
    }
    else {
      AbstractVcs commonVcsFor = getCommonVcsFor(roots, project);
      if (commonVcsFor == null) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }
    }

    String actionName = getActionName(vcsContext) + "...";
    presentation.setText(actionName);

    presentation.setEnabled(!ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning());
    presentation.setVisible(true);
  }

  private FilePath[] filterRoots(final FilePath[] roots, final Project project) {
    final ArrayList<FilePath> result = new ArrayList<FilePath>();
    for (FilePath root : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, root);
      if (vcs != null) {
        if (!filterRootsBeforeAction() || canCheckinRoot(project, vcs, root)) {
          CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
          if (checkinEnvironment != null) {
            result.add(root);
          }
        }
      }
    }
    return result.toArray(new FilePath[result.size()]);
  }

  private static boolean canCheckinRoot(final Project project, final AbstractVcs vcs, final FilePath root) {
    VirtualFile file = root.getVirtualFile();
    if (file != null && !file.isDirectory()) {
      final FileStatus fileStatus = ChangeListManager.getInstance(project).getStatus(file);
      return fileStatus != FileStatus.UNKNOWN && fileStatus != FileStatus.NOT_CHANGED;
    }
    return vcs.fileIsUnderVcs(root);
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected abstract boolean filterRootsBeforeAction();

  protected static FilePath[] getAllContentRoots(final VcsContext context) {
    Project project = context.getProject();
    ArrayList<FilePath> virtualFiles = new ArrayList<FilePath>();
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
    VirtualFile[] roots = manager.getAllVersionedRoots();
    for (VirtualFile root : roots) {
      virtualFiles.add(new FilePathImpl(root));
    }
    return virtualFiles.toArray(new FilePath[virtualFiles.size()]);
  }
}
