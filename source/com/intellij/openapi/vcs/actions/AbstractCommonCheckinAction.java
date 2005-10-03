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

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.VcsOperation;
import com.intellij.openapi.vcs.fileView.impl.FileViewPanel;
import com.intellij.openapi.vcs.ui.CheckinDialog;
import com.intellij.openapi.vcs.ui.CheckinFileDialog;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.impl.CheckinProjectPanelImpl;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcsUtil.VcsUtil;

import java.util.*;


public abstract class AbstractCommonCheckinAction extends AbstractVcsAction {
  private static final int MIXED = 0;
  protected static final int DIRECTORIES = 1;
  private static final int FILES = 2;

  public void actionPerformed(final VcsContext context) {
    final Project project = context.getProject();

    if (project == null) return;

    FilePath[] roots = filterDescindingFiles(getRoots(context), project);


    int ciType = getCheckinType(roots);

    if (ciType == MIXED) {
      return;
    }
    if (ciType == DIRECTORIES) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        ApplicationManager.getApplication().saveAll();
      }
      checkinDirectories(project, context, roots);
    }
    else {
      CheckinEnvironment env = getCommonEnvironmentFor(roots, project);
      if (env == null) return;
      if (ApplicationManager.getApplication().isDispatchThread()) {
        ApplicationManager.getApplication().saveAll();
      }
      final List<VcsException> errors = checkinFiles(project, context, roots, env);

      processErrors(errors, VcsConfiguration.getInstance(project));
    }

  }

  protected CheckinEnvironment getCommonEnvironmentFor(FilePath[] roots, Project project) {
    if (roots.length == 0) return null;
    AbstractVcs firstVcs = VcsUtil.getVcsFor (project, roots[0]);
    if (firstVcs == null) return null;
    CheckinEnvironment firstEnv = firstVcs.getCheckinEnvironment();
    if (firstEnv == null) return null;

    for (FilePath file : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      if (vcs == null) return null;
      CheckinEnvironment env = vcs.getCheckinEnvironment();
      if (firstEnv != env) {
        return null;
      }
    }
    return firstEnv;
  }

  private List<VcsException> checkinFiles(final Project project, final VcsContext context, FilePath[] roots, CheckinEnvironment checkinEnvironment) {
    List<VcsException> vcsExceptions = new ArrayList<VcsException>();
    if (ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.CHECKIN).getValue() 
        || OptionsDialog.shiftIsPressed(context.getModifiers())) {


      CheckinFileDialog dialog = new CheckinFileDialog(project, getActionName(context),
                                                       checkinEnvironment,
                                                       roots);
      dialog.show();
      if (!dialog.isOK()) return vcsExceptions;
      vcsExceptions.addAll(checkinEnvironment.commit(roots, project, dialog.getPreparedComment(checkinEnvironment)));
      if (!vcsExceptions.isEmpty()) {
        AbstractVcsHelper.getInstance(project).showErrors(vcsExceptions, getActionName(context));
      }
    }
    else {
      vcsExceptions.addAll(checkinEnvironment.commit(roots, project,
                                                                   checkinEnvironment.prepareCheckinMessage(
                                                                     CheckinDialog.getInitialMessage(roots, project))));
      if (!vcsExceptions.isEmpty()) {
        AbstractVcsHelper.getInstance(project).showErrors(vcsExceptions, getActionName(context));
      }
    }

    final LvcsAction lvcsAction = LocalVcs.getInstance(project).startAction(getActionName(context), "", true);
    VcsUtil.refreshFiles(roots, new Runnable(){
      public void run() {
        lvcsAction.finish();
        FileStatusManager.getInstance(project).fileStatusesChanged();
        final Refreshable refreshablePanel = context.getRefreshableDialog();
        if (refreshablePanel != null) {
          refreshablePanel.refresh();
        }
        refreshFileView(project);
      }
    });

    return vcsExceptions;
  }

  private void checkinDirectories(final Project project, final VcsContext context, FilePath[] roots) {
    final CheckinProjectDialogImplementer dialog =
      AbstractVcsHelper.getInstance(project).createCheckinProjectDialog(getActionName(context), true, asPathList(roots));

    final Refreshable refreshablePanel = context.getRefreshableDialog();

    final List<VcsException> vcsExceptions = new ArrayList<VcsException>();

    Runnable actionAfterDialogWasShown = new Runnable() {
      public void run() {
        try {
          if (!shouldCheckin(dialog, project)) {
            return;
          }

          final Runnable checkinAction = new Runnable() {
            public void run() {
              CheckinProjectPanelImpl checkinProjectPanel = (CheckinProjectPanelImpl)dialog.getCheckinProjectPanel();
              final Map<CheckinEnvironment, List<VcsOperation>> checkinOperations = checkinProjectPanel.getCheckinOperations();
              Runnable checkinAction = new Runnable() {
                public void run() {

                  for (CheckinEnvironment checkinEnvironment : checkinOperations.keySet()) {
                    vcsExceptions.addAll(checkinEnvironment.commit(dialog, project));
                  }

                  final LvcsAction lvcsAction = LocalVcs.getInstance(project).startAction(getActionName(context), "", true);
                  VirtualFileManager.getInstance().refresh(true, new Runnable() {
                    public void run() {
                      lvcsAction.finish();
                      FileStatusManager.getInstance(project).fileStatusesChanged();
                      if (refreshablePanel != null) {
                        refreshablePanel.refresh();
                      }
                      refreshFileView(project);
                    }
                  });
                  AbstractVcsHelper.getInstance(project).showErrors(vcsExceptions, getActionName(context));
                }
              };
              ApplicationManager.getApplication().runProcessWithProgressSynchronously(checkinAction, getActionName(context), true, project);
            }
          };

          AbstractVcsHelper.getInstance(project).optimizeImportsAndReformatCode(dialog.getCheckinProjectPanel().getVirtualFiles(),
                                                                                VcsConfiguration.getInstance(project), checkinAction, true);


        }
        finally {
          dialog.dispose();
        }

      }
    };

    try {
      dialog.analyzeChanges(true, actionAfterDialogWasShown);
    }
    catch (VcsException e) {
      Messages.showErrorDialog(VcsBundle.message("message.text.cannot.analyze.changes", e.getLocalizedMessage()), VcsBundle.message("message.title.analizing.changes"));
    }

    processErrors(vcsExceptions, VcsConfiguration.getInstance(project));

  }

  private void processErrors(final List<VcsException> allExceptions, VcsConfiguration config) {
    //if (allExceptions.isEmpty()) return;
    int errorsSize = collectErrors(allExceptions).size();
    int warningsSize = allExceptions.size() - errorsSize;

    config.ERROR_OCCURED = errorsSize > 0;

    if (errorsSize > 0 && warningsSize > 0) {
      Messages.showErrorDialog(VcsBundle.message("message.text.commit.failed.with.errors.and.warnings"), VcsBundle.message("message.title.commit"));
    }
    else if (errorsSize > 0) {
      Messages.showErrorDialog(VcsBundle.message("message.text.commit.failed.with.errors"), VcsBundle.message("message.title.commit"));
    }
    else if (warningsSize > 0) {
      Messages.showErrorDialog(VcsBundle.message("message.text.commit.finished.with.warnings"), VcsBundle.message("message.title.commit"));
    }

  }

  private List<VcsException> collectErrors(final List<VcsException> vcsExceptions) {
    final ArrayList<VcsException> result = new ArrayList<VcsException>();
    for (VcsException vcsException : vcsExceptions) {
      if (!vcsException.isWarning()) {
        result.add(vcsException);
      }
    }
    return result;
  }

  protected int getCheckinType(FilePath[] roots) {
    if (roots.length == 0) return MIXED;
    FilePath file = roots[0];
    int firstType = getCheckinType(file);

    for (FilePath root : roots) {
      int checkinType = getCheckinType(root);
      if (checkinType != firstType) return MIXED;
    }

    return firstType;
  }

  private int getCheckinType(FilePath file) {
    return file.isDirectory() ? DIRECTORIES : FILES;
  }

  private Collection<String> asPathList(FilePath[] roots) {
    ArrayList<String> result = new ArrayList<String>();
    for (FilePath root : roots) {
      result.add(root.getPath());
    }
    return result;
  }

  protected abstract String getActionName(VcsContext dataContext);

  protected abstract FilePath[] getRoots(VcsContext project);

  protected void refreshFileView(Project project) {
    if (project == null) return;
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);

    ToolWindowEx fileViewToolWindow = (ToolWindowEx)toolWindowManager
      .getToolWindow(ProjectLevelVcsManager.FILE_VIEW_TOOL_WINDOW_ID);
    if (fileViewToolWindow == null) return;
    if (!fileViewToolWindow.isAvailable()) return;
    ((FileViewPanel)fileViewToolWindow.getComponent()).refresh();
  }

  private boolean shouldCheckin(CheckinProjectDialogImplementer d, Project project) {
    if (!d.hasDiffs()) {
      Messages.showMessageDialog(project, VcsBundle.message("message.text.nothing.was.found.to.commit"), VcsBundle.message("message.title.nothing.was.found.to.commit"),
                                 Messages.getInformationIcon());
      return false;
    }
    d.show();
    return d.isOK();
  }

  protected void update(VcsContext vcsContext, Presentation presentation) {
    Project project = vcsContext.getProject();
    if (project == null) {
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
      CheckinEnvironment commonEnvironmentFor = getCommonEnvironmentFor(roots, project);
      if (commonEnvironmentFor == null) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }
    }

    String actionName = getActionName(vcsContext);
    if (shouldShowDialog(vcsContext) || OptionsDialog.shiftIsPressed(vcsContext.getModifiers())){
      actionName += "...";
    }

    presentation.setText(actionName);

    presentation.setEnabled(true);
    presentation.setVisible(true);
  }

  private FilePath[] filterRoots(final FilePath[] roots, final Project project) {
    final ArrayList<FilePath> result = new ArrayList<FilePath>();
    for (FilePath root : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, root);
      if (vcs != null) {
        if (!filterRootsBeforeAction() || vcs.fileIsUnderVcs(root)) {
          CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
          if (checkinEnvironment != null) {
            result.add(root);
          }
        }
      }
    }
    return result.toArray(new FilePath[result.size()]);
  }

  protected abstract boolean shouldShowDialog(VcsContext context);
  protected abstract boolean filterRootsBeforeAction();
}
