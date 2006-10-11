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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.AbstractVcsAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentsUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.vcsUtil.VcsUtil;

import java.util.*;

public abstract class AbstractCommonUpdateAction extends AbstractVcsAction {

  private final ActionInfo myActionInfo;
  private final ScopeInfo myScopeInfo;

  protected AbstractCommonUpdateAction(ActionInfo actionInfo, ScopeInfo scopeInfo) {
    myActionInfo = actionInfo;
    myScopeInfo = scopeInfo;
  }

  protected final String getCompleteActionName(VcsContext dataContext) {
    return myActionInfo.getActionName(myScopeInfo.getScopeName(dataContext, myActionInfo));
  }

  protected void actionPerformed(final VcsContext context) {
    final Project project = context.getProject();

    boolean showUpdateOptions = myActionInfo.showOptions(project);

    final UpdatedFiles updatedFiles = UpdatedFiles.create();
    if (project != null) {
      try {

        if (ApplicationManager.getApplication().isDispatchThread()) {
          ApplicationManager.getApplication().saveAll();
        }

        final FilePath[] roots = filterDescindingFiles(filterRoots(myScopeInfo.getRoots(context, myActionInfo), context), project);

        final Map<AbstractVcs, Collection<FilePath>> vcsToVirtualFiles = createVcsToFilesMap(roots, project);


        if (showUpdateOptions || OptionsDialog.shiftIsPressed(context.getModifiers())) {
          showOptionsDialog(vcsToVirtualFiles, project, context);
        }
        final ArrayList<VcsException> vcsExceptions = new ArrayList<VcsException>();
        final List<UpdateSession> updateSessions = new ArrayList<UpdateSession>();
        final Runnable updateProcess = new Runnable() {
          public void run() {
            ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();
            ProjectLevelVcsManager.getInstance(project).startBackgroundVcsOperation();
            ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            int toBeProcessed = vcsToVirtualFiles.size();
            int processed = 0;
            for (AbstractVcs vcs : vcsToVirtualFiles.keySet()) {
              final UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
              updateEnvironment.fillGroups(updatedFiles);
              Collection<FilePath> files = vcsToVirtualFiles.get(vcs);
              UpdateSession updateSession =
                updateEnvironment.updateDirectories(files.toArray(new FilePath[files.size()]), updatedFiles, progressIndicator);
              processed++;
              if (progressIndicator != null) {
                progressIndicator.setFraction((double)processed / (double)toBeProcessed);
              }
              vcsExceptions.addAll(updateSession.getExceptions());
              updateSessions.add(updateSession);
            }

            if (progressIndicator != null) {
              progressIndicator.setText(VcsBundle.message("progress.text.synchronizing.files"));
              progressIndicator.setText2("");
            }

            final Semaphore semaphore = new Semaphore();
            semaphore.down();

            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                VcsUtil.refreshFiles(roots, new Runnable() {
                  public void run() {
                    semaphore.up();
                  }
                });
              }
            });
            semaphore.waitFor();
          }
        };

        Runnable finishRunnable = new Runnable() {
          public void run() {
            if (!someSessionWasCanceled(updateSessions)) {
              for (final UpdateSession updateSession : updateSessions) {
                updateSession.onRefreshFilesCompleted();
              }
            }

            if (!someSessionWasCanceled(updateSessions)) {

              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  if (!vcsExceptions.isEmpty()) {
                    AbstractVcsHelper.getInstance(project).showErrors(vcsExceptions, VcsBundle.message("message.title.vcs.update.errors",
                                                                                                       getTemplatePresentation().getText()));
                  }
                  else {
                    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                    if (indicator != null) {
                      indicator.setText(VcsBundle.message("progress.text.updating.done"));
                    }
                  }
                  
                  if (updatedFiles.isEmpty() && vcsExceptions.isEmpty()) {
                    Messages.showMessageDialog(getAllFilesAreUpToDateMessage(roots),
                                               getTemplatePresentation().getText(),
                                               Messages.getInformationIcon());

                  }
                  else if (!updatedFiles.isEmpty()) {
                    RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(project);
                    restoreUpdateTree.registerUpdateInformation(updatedFiles, myActionInfo);
                    showUpdateProjectInfo(project, updatedFiles, getTemplatePresentation().getText(), myActionInfo);

                  }

                  ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
                }
              });
            }
            ProjectLevelVcsManager.getInstance(project).stopBackgroundVcsOperation();
          }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(project, getTemplatePresentation().getText(), updateProcess,
                                                                           finishRunnable, finishRunnable,
                                                                           VcsConfiguration.getInstance(project).getUpdateOption());
      }
      catch (ProcessCanceledException e1) {
        //ignore
      }
    }
  }

  private static boolean someSessionWasCanceled(List<UpdateSession> updateSessions) {
    for (UpdateSession updateSession : updateSessions) {
      if (updateSession.isCanceled()) {
        return true;
      }
    }
    return false;
  }

  private static String getAllFilesAreUpToDateMessage(FilePath[] roots) {
    if (roots.length == 1 && !roots[0].isDirectory()) {
      return VcsBundle.message("message.text.file.is.up.to.date");
    }
    else {
      return VcsBundle.message("message.text.all.files.are.up.to.date");
    }
  }

  public static void showUpdateProjectInfo(final Project project,
                                           final UpdatedFiles updatedFiles,
                                           String actionName,
                                           ActionInfo actionInfo) {
    ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).getContentManager();
    final UpdateInfoTree updateInfoTree = new UpdateInfoTree(contentManager, null, project, updatedFiles, actionName, actionInfo);
    Content content = PeerFactory.getInstance().getContentFactory().createContent(updateInfoTree, VcsBundle.message(
      "toolwindow.title.update.action.info", actionInfo.getActionName()),
                                                                                  true);
    ContentsUtil.addOrReplaceContent(contentManager, content, true);
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS).activate(null);
    updateInfoTree.expandRootChildren();
  }

  private void showOptionsDialog(final Map<AbstractVcs, Collection<FilePath>> updateEnvToVirtualFiles, final Project project,
                                 final VcsContext dataContext) {
    LinkedHashMap<Configurable, AbstractVcs> envToConfMap = createConfigurableToEnvMap(updateEnvToVirtualFiles);
    if (!envToConfMap.isEmpty()) {
      UpdateOrStatusOptionsDialog dialogOrStatus = myActionInfo.createOptionsDialog(project, envToConfMap,
                                                                                    myScopeInfo.getScopeName(dataContext,
                                                                                                             myActionInfo));
      dialogOrStatus.show();
      if (!dialogOrStatus.isOK()) {
        throw new ProcessCanceledException();
      }
    }
  }

  private LinkedHashMap<Configurable, AbstractVcs> createConfigurableToEnvMap(Map<AbstractVcs, Collection<FilePath>> updateEnvToVirtualFiles) {
    LinkedHashMap<Configurable, AbstractVcs> envToConfMap = new LinkedHashMap<Configurable, AbstractVcs>();
    for (AbstractVcs vcs : updateEnvToVirtualFiles.keySet()) {
      Configurable configurable = myActionInfo.getEnvironment(vcs).createConfigurable(updateEnvToVirtualFiles.get(vcs));
      if (configurable != null) {
        envToConfMap.put(configurable, vcs);
      }
    }
    return envToConfMap;
  }

  private Map<AbstractVcs,Collection<FilePath>> createVcsToFilesMap(FilePath[] roots, Project project) {
    HashMap<AbstractVcs, Collection<FilePath>> result = new HashMap<AbstractVcs, Collection<FilePath>>();

    for (FilePath file : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      if (vcs != null) {
        UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
        if (updateEnvironment != null) {
          if (!result.containsKey(vcs)) result.put(vcs, new HashSet<FilePath>());
          result.get(vcs).add(file);
        }
      }
    }

    for (final Collection<FilePath> filePaths : result.values()) {
      filterSubDirectories(filePaths);
    }

    return result;
  }

  private static void filterSubDirectories(Collection<FilePath> virtualFiles) {
    FilePath[] array = virtualFiles.toArray(new FilePath[virtualFiles.size()]);
    for (FilePath file : array) {
      if (containsParent(array, file)) {
        virtualFiles.remove(file);
      }
    }
  }

  private static boolean containsParent(FilePath[] array, FilePath file) {
    for (FilePath virtualFile : array) {
      if (virtualFile == file) continue;
      if (VfsUtil.isAncestor(virtualFile.getIOFile(), file.getIOFile(), false)) return true;
    }
    return false;
  }

  private FilePath[] filterRoots(FilePath[] roots, VcsContext vcsContext) {
    final ArrayList<FilePath> result = new ArrayList<FilePath>();
    for (FilePath file : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(vcsContext.getProject(), file);
      if (vcs != null) {
        if (vcs.fileExistsInVcs(file)) {
          UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
          if (updateEnvironment != null) {
            result.add(file);
          }
        } else {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null && virtualFile.isDirectory()) {
            final VirtualFile[] children = virtualFile.getChildren();
            if (children != null) {
              final FilePath[] childrenAsPaths = createFilePathsOn(children);
              result.addAll(Arrays.asList(filterRoots(childrenAsPaths, vcsContext)));
            }
          }
        }
      }
    }
    return result.toArray(new FilePath[result.size()]);
  }

  private static FilePath[] createFilePathsOn(final VirtualFile[] children) {
    final VcsContextFactory vcsContextFactory = PeerFactory.getInstance().getVcsContextFactory();
    final FilePath[] result = new FilePath[children.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = vcsContextFactory.createFilePathOn(children[i]);
    }
    return result;
  }

  protected abstract boolean filterRootsBeforeAction();

  protected void update(VcsContext vcsContext, Presentation presentation) {
    Project project = vcsContext.getProject();

    if (project != null) {

      String actionName = getCompleteActionName(vcsContext);
      if (myActionInfo.showOptions(project) || OptionsDialog.shiftIsPressed(vcsContext.getModifiers())) {
        actionName += "...";
      }

      presentation.setText(actionName);

      presentation.setVisible(true);
      presentation.setEnabled(true);

      if (supportingVcsesAreEmpty(vcsContext.getProject(), myActionInfo)) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
      }

      if (filterRootsBeforeAction()) {
        FilePath[] roots = filterRoots(myScopeInfo.getRoots(vcsContext, myActionInfo), vcsContext);
        if ( roots == null || roots.length == 0) {
          presentation.setVisible(false);
          presentation.setEnabled(false);
        }
      }

      if (presentation.isVisible() && presentation.isEnabled() &&
          ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
        presentation.setEnabled(false);
      }
    } else {
      presentation.setVisible(false);
      presentation.setEnabled(false);
    }
 }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  private static boolean supportingVcsesAreEmpty(final Project project, final ActionInfo actionInfo) {
    if (project == null) return true;
    final AbstractVcs[] allActiveVcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for (AbstractVcs activeVcs : allActiveVcss) {
      if (actionInfo.getEnvironment(activeVcs) != null) return false;
    }
    return true;
  }
}
