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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.VcsOperation;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.impl.FileViewManagerImpl;
import com.intellij.openapi.vcs.ui.CheckinDialog;
import com.intellij.openapi.vcs.ui.CheckinFileDialog;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.impl.CheckinProjectPanelImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;


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

    boolean enforceOldCommit = false;
    final AbstractVcs[] activeVcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for (AbstractVcs activeVcs : activeVcss) {
      if (activeVcs.getChangeProvider() == null) {
        enforceOldCommit = true;
      }
    }

    if (enforceOldCommit) {
      oldActionPerformed(roots, project, context);
    }
    else {
      if (ChangeListManager.getInstance(project).ensureUpToDate(true)) {
        CommitChangeListDialog.commitPaths(project, Arrays.asList(roots));
      }
    }
  }

  private void oldActionPerformed(final FilePath[] roots, final Project project, final VcsContext context) {
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
      checkinFiles(project, context, roots, env);
    }
  }

  @Nullable
  protected static CheckinEnvironment getCommonEnvironmentFor(FilePath[] roots, Project project) {
    if (roots.length == 0) return null;
    AbstractVcs firstVcs = VcsUtil.getVcsFor(project, roots[0]);
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

  private void checkinFiles(final Project project, final VcsContext context, FilePath[] roots, CheckinEnvironment checkinEnvironment) {
    final boolean showDialog = ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.CHECKIN)
      .getValue() || OptionsDialog.shiftIsPressed(context.getModifiers());

    if (showDialog) {

      CheckinFileDialog dialog = new CheckinFileDialog(project, getActionName(context), checkinEnvironment, roots);
      dialog.show();
      if (!dialog.isOK()) return;

      checkinFiles(checkinEnvironment, roots, project, dialog.getPreparedComment(checkinEnvironment), context, dialog.getHandlers());

    }
    else {
      final List<CheckinHandler> handlers = new ArrayList<CheckinHandler>();
      final List<CheckinHandlerFactory> factories = ProjectLevelVcsManager.getInstance(project).getRegisteredCheckinHandlerFactories();

      final CheckinProjectPanel checkinPanel = createMockPanel(getVirtualFiles(roots), getFiles(roots), project,
                                                               checkinEnvironment.prepareCheckinMessage(
                                                                 CheckinDialog.getInitialMessage(roots, project)),
                                                               getVcs(checkinEnvironment, project));

      for (CheckinHandlerFactory factory : factories) {
        handlers.add(factory.createHandler(checkinPanel));
      }


      for (CheckinHandler handler : handlers) {
        final CheckinHandler.ReturnResult returnResult = handler.beforeCheckin();
        if (returnResult != CheckinHandler.ReturnResult.COMMIT) {
          return;
        }
      }

      checkinFiles(checkinEnvironment, roots, project, checkinPanel.getCommitMessage(), context, handlers);
    }

  }

  @Nullable
  private static AbstractVcs getVcs(final CheckinEnvironment checkinEnvironment, final Project project) {
    final AbstractVcs[] abstractVcses = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for (AbstractVcs vcs : abstractVcses) {
      if (vcs.getCheckinEnvironment() == checkinEnvironment) {
        return vcs;
      }
    }

    return null;
  }

  private static Collection<File> getFiles(final FilePath[] roots) {
    final ArrayList<File> result = new ArrayList<File>();
    for (FilePath root : roots) {
      result.add(root.getIOFile());
    }
    return result;
  }

  private CheckinProjectPanel createMockPanel(final Collection<VirtualFile> virtualFiles,
                                              final Collection<File> files,
                                              final Project project,
                                              final String message,
                                              final AbstractVcs abstractVcs) {
    return new MockCheckinProjectPanel(virtualFiles, files, project, message, abstractVcs);
  }                                  

  private void checkinFiles(final CheckinEnvironment checkinEnvironment,
                            final FilePath[] roots,
                            final Project project,
                            final String message,
                            final VcsContext context,
                            final List<CheckinHandler> handlers) {
    Runnable checkinFilesAction = new Runnable() {
      public void run() {
        List<VcsException> vcsExceptions = checkinEnvironment.commit(roots, project, message);

        try {
          if (!vcsExceptions.isEmpty()) {
            AbstractVcsHelper.getInstance(project).showErrors(vcsExceptions, getActionName(context));
          }

          final LvcsAction lvcsAction = LocalVcs.getInstance(project).startAction(getActionName(context), "", true);
          VcsUtil.refreshFiles(roots, new Runnable() {
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
        }
        finally {
          commitCompleted(vcsExceptions, VcsConfiguration.getInstance(project), handlers);
        }


      }
    };

    AbstractVcsHelper.getInstance(project)
      .optimizeImportsAndReformatCode(getVirtualFiles(roots), VcsConfiguration.getInstance(project), checkinFilesAction, true);


  }

  private static Collection<VirtualFile> getVirtualFiles(final FilePath[] roots) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (FilePath root : roots) {
      final VirtualFile virtualFile = root.getVirtualFile();
      if (virtualFile != null) {
        result.add(virtualFile);
      }
    }
    return result;
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

                  try {
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
                  finally {
                    commitCompleted(vcsExceptions, VcsConfiguration.getInstance(project), dialog.getHandlers());
                  }
                }
              };
              ProgressManager.getInstance().runProcessWithProgressSynchronously(checkinAction, getActionName(context), true, project);
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
      Messages.showErrorDialog(VcsBundle.message("message.text.cannot.analyze.changes", e.getLocalizedMessage()),
                               VcsBundle.message("message.title.analizing.changes"));
    }

  }

  private static void commitCompleted(final List<VcsException> allExceptions,
                                      VcsConfiguration config,
                                      final List<CheckinHandler> checkinHandlers) {


    final List<VcsException> errors = collectErrors(allExceptions);
    final int errorsSize = errors.size();
    final int warningsSize = allExceptions.size() - errorsSize;

    if (errorsSize == 0) {
      for (CheckinHandler handler : checkinHandlers) {
        handler.checkinSuccessful();
      }
    }
    else {
      for (CheckinHandler handler : checkinHandlers) {
        handler.checkinFailed(errors);
      }
    }


    config.ERROR_OCCURED = errorsSize > 0;


    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (errorsSize > 0 && warningsSize > 0) {
          Messages.showErrorDialog(VcsBundle.message("message.text.commit.failed.with.errors.and.warnings"),
                                   VcsBundle.message("message.title.commit"));
        }
        else if (errorsSize > 0) {
          Messages.showErrorDialog(VcsBundle.message("message.text.commit.failed.with.errors"), VcsBundle.message("message.title.commit"));
        }
        else if (warningsSize > 0) {
          Messages
            .showErrorDialog(VcsBundle.message("message.text.commit.finished.with.warnings"), VcsBundle.message("message.title.commit"));
        }

      }
    }, ModalityState.NON_MODAL);

  }

  private static List<VcsException> collectErrors(final List<VcsException> vcsExceptions) {
    final ArrayList<VcsException> result = new ArrayList<VcsException>();
    for (VcsException vcsException : vcsExceptions) {
      if (!vcsException.isWarning()) {
        result.add(vcsException);
      }
    }
    return result;
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

  private static Collection<String> asPathList(FilePath[] roots) {
    ArrayList<String> result = new ArrayList<String>();
    for (FilePath root : roots) {
      result.add(root.getPath());
    }
    return result;
  }

  protected abstract String getActionName(VcsContext dataContext);

  protected abstract FilePath[] getRoots(VcsContext project);

  protected static void refreshFileView(Project project) {
    if (project == null) return;
    FileViewManagerImpl.getInstance(project).refreshFileView();
  }

  private static boolean shouldCheckin(CheckinProjectDialogImplementer d, Project project) {
    if (!d.hasDiffs()) {
      Messages.showMessageDialog(project, VcsBundle.message("message.text.nothing.was.found.to.commit"),
                                 VcsBundle.message("message.title.nothing.was.found.to.commit"), Messages.getInformationIcon());
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
      CheckinEnvironment commonEnvironmentFor = getCommonEnvironmentFor(roots, project);
      if (commonEnvironmentFor == null) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }
    }

    String actionName = getActionName(vcsContext);
    if (shouldShowDialog(vcsContext) || OptionsDialog.shiftIsPressed(vcsContext.getModifiers())) {
      actionName += "...";
    }

    presentation.setText(actionName);

    presentation.setEnabled(!ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning());
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

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected abstract boolean shouldShowDialog(VcsContext context);

  protected abstract boolean filterRootsBeforeAction();

  private static class MockCheckinProjectPanel implements CheckinProjectPanel {
    private String myMessage;
    private final Project myProject;
    private final Collection<VirtualFile> myVirtualFiles;
    private final Collection<File> myFiles;
    private final AbstractVcs myVcs;


    public MockCheckinProjectPanel(final Collection<VirtualFile> virtualFiles,
                                   final Collection<File> files,
                                   final Project project,
                                   final String message,
                                   AbstractVcs vcs) {
      myMessage = message;
      myProject = project;
      myVirtualFiles = virtualFiles;
      myFiles = files;
      myVcs = vcs;
    }

    public JComponent getComponent() {
      return null;
    }

    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    public List<AbstractVcs> getAffectedVcses() {
      return Collections.singletonList(myVcs);
    }

    public boolean hasDiffs() {
      return true;
    }

    public void addSelectionChangeListener(SelectionChangeListener listener) {
    }

    public void removeSelectionChangeListener(SelectionChangeListener listener) {
    }

    public Collection<VirtualFile> getVirtualFiles() {
      return myVirtualFiles;
    }

    public Collection<File> getFiles() {
      return myFiles;
    }

    public Project getProject() {
      return myProject;
    }

    public List<VcsOperation> getCheckinOperations(CheckinEnvironment checkinEnvironment) {
      return null;
    }

    public Collection<VirtualFile> getRoots() {
      return myVirtualFiles;
    }

    public void setCommitMessage(final String currentDescription) {
      myMessage = currentDescription;
    }

    public String getCommitMessage() {
      return myMessage;
    }

    public void refresh() {
    }

    public void saveState() {
    }

    public void restoreState() {
    }
  }
}
