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

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.AbstractVcsAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.ui.OptionsDialog;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.vcsUtil.VcsUtil;

import java.util.*;

public class AbstractCommonUpdateAction extends AbstractVcsAction {

  private final ActionInfo myActionInfo;
  private final ScopeInfo myScopeInfo;

  protected AbstractCommonUpdateAction(ActionInfo actionInfo, ScopeInfo scopeInfo) {
    myActionInfo = actionInfo;
    myScopeInfo = scopeInfo;
  }

  protected final String getCompleteActionName(VcsContext dataContext) {
    return myActionInfo.getActionName() + " " + myScopeInfo.getScopeName(dataContext);
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

        final FilePath[] roots = filterDescindingFiles(myScopeInfo.getRoots(context), project);

        final Map<UpdateEnvironment, Collection<FilePath>> updateEnvToVirtualFiles = createEnvToFilesMap(roots, project);


        if (showUpdateOptions || OptionsDialog.shiftIsPressed(context.getModifiers())) {
          showOptionsDialog(updateEnvToVirtualFiles, project);
        }
        final ArrayList<VcsException> vcsExceptions = new ArrayList<VcsException>();
        final List<UpdateSession> updateSessions = new ArrayList<UpdateSession>();
        ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {

            ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            for (Iterator<UpdateEnvironment> iterator = updateEnvToVirtualFiles.keySet().iterator(); iterator.hasNext();) {
              UpdateEnvironment updateEnvironment = iterator.next();
              updateEnvironment.fillGroups(updatedFiles);
              Collection<FilePath> files = updateEnvToVirtualFiles.get(updateEnvironment);
              UpdateSession updateSession = updateEnvironment.updateDirectories(files.toArray(new FilePath[files.size()]),
                                                                                updatedFiles,
                                                                                progressIndicator);
              vcsExceptions.addAll(updateSession.getExceptions());
              updateSessions.add(updateSession);
            }

            if (progressIndicator != null) {
              progressIndicator.setText("Synchronizing files...");
              progressIndicator.setText2("");
            }

            final Semaphore semaphore = new Semaphore();
            semaphore.down();

            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                VirtualFileManager.getInstance().refresh(true, new Runnable() {
                  public void run() {
                    semaphore.up();
                  }
                });
              }
            });
            semaphore.waitFor();
            if (!someSessionWasCanceled(updateSessions)) {
              for (Iterator<UpdateSession> iterator = updateSessions.iterator(); iterator.hasNext();) {
                iterator.next().onRefreshFilesCompleted();
              }
            }
          }

        }, getCompleteActionName(context), true, project);

        if (!someSessionWasCanceled(updateSessions)) {

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              if (!vcsExceptions.isEmpty()) {
                AbstractVcsHelper.getInstance(project).showErrors(vcsExceptions, getCompleteActionName(context) + " Errors");
              }
              if (updatedFiles.isEmpty() && vcsExceptions.isEmpty()) {
                Messages.showMessageDialog(getAllFilesAreUpToDateMessage(roots),
                                           getCompleteActionName(context),
                                           Messages.getInformationIcon());

                return;
              }
              else if (!updatedFiles.isEmpty()) {
                RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(project);
                restoreUpdateTree.registerUpdateInformation(updatedFiles, myActionInfo);
                showUpdateProjectInfo(project, updatedFiles, getCompleteActionName(context), myActionInfo);

              }
            }
          });
        }
      }
      catch (ProcessCanceledException e1) {

      }
    }
  }

  private boolean someSessionWasCanceled(List<UpdateSession> updateSessions) {
    for (Iterator<UpdateSession> iterator = updateSessions.iterator(); iterator.hasNext();) {
      UpdateSession updateSession = iterator.next();
      if (updateSession.isCanceled()) {
        return true;
      }
    }
    return false;
  }

  private String getAllFilesAreUpToDateMessage(FilePath[] roots) {
    if (roots.length == 1 && !roots[0].isDirectory()) {
      return "File is up-to-date";
    }
    else {
      return "All files are up-to-date";
    }
  }

  public static void showUpdateProjectInfo(final Project project,
                                           final UpdatedFiles updatedFiles,
                                           String actionName,
                                           ActionInfo actionInfo) {
    ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).getContentManager();
    final UpdateInfoTree updateInfoTree = new UpdateInfoTree(contentManager, null, project, updatedFiles, actionName, actionInfo);
    Content content = PeerFactory.getInstance().getContentFactory().createContent(updateInfoTree, actionInfo.getActionName() + " Info",
                                                                                  true);
    contentManager.addContent(content);
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS).activate(null);
    contentManager.setSelectedContent(content);
    updateInfoTree.expandRootChildren();
  }

  private void showOptionsDialog(final Map<UpdateEnvironment, Collection<FilePath>> updateEnvToVirtualFiles, final Project project) {
    LinkedHashMap<Configurable, UpdateEnvironment> envToConfMap = createConfigurableToEnvMap(updateEnvToVirtualFiles);
    if (!envToConfMap.isEmpty()) {
      UpdateOrStatusOptionsDialog dialogOrStatus = myActionInfo.createOptionsDialog(project, envToConfMap);
      dialogOrStatus.show();
      if (!dialogOrStatus.isOK()) {
        throw new ProcessCanceledException();
      }
    }
  }

  private LinkedHashMap<Configurable, UpdateEnvironment> createConfigurableToEnvMap(
    Map<UpdateEnvironment, Collection<FilePath>> updateEnvToVirtualFiles) {
    LinkedHashMap<Configurable, UpdateEnvironment> envToConfMap = new LinkedHashMap<Configurable, UpdateEnvironment>();
    for (Iterator<UpdateEnvironment> iterator = updateEnvToVirtualFiles.keySet().iterator(); iterator.hasNext();) {
      UpdateEnvironment updateEnvironment = iterator.next();
      Configurable configurable = updateEnvironment.createConfigurable(updateEnvToVirtualFiles.get(updateEnvironment));
      if (configurable != null) {
        envToConfMap.put(configurable, updateEnvironment);
      }
    }
    return envToConfMap;
  }

  private Map<UpdateEnvironment,Collection<FilePath>> createEnvToFilesMap(FilePath[] roots, Project project) {
    HashMap<UpdateEnvironment, Collection<FilePath>> result = new HashMap<UpdateEnvironment, Collection<FilePath>>();

    for (int i = 0; i < roots.length; i++) {
      FilePath file = roots[i];
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      if (vcs != null) {
        UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
        if (updateEnvironment != null) {
          if (!result.containsKey(updateEnvironment)) result.put(updateEnvironment, new HashSet<FilePath>());
          if (vcs.fileIsUnderVcs(file)) {
            result.get(updateEnvironment).add(file);
          }
        }
      }
    }

    for (Iterator<Collection<FilePath>> iterator = result.values().iterator(); iterator.hasNext();) {
      filterSubDirectories(iterator.next());
    }

    return result;
  }

  private void filterSubDirectories(Collection<FilePath> virtualFiles) {
    FilePath[] array = virtualFiles.toArray(new FilePath[virtualFiles.size()]);
    for (int i = 0; i < array.length; i++) {
      FilePath file = array[i];
      if (containsParent(array, file)) {
        virtualFiles.remove(file);
      }
    }
  }

  private boolean containsParent(FilePath[] array, FilePath file) {
    for (int i = 0; i < array.length; i++) {
      FilePath virtualFile = array[i];
      if (virtualFile == file) continue;
      if (VfsUtil.isAncestor(virtualFile.getIOFile(), file.getIOFile(), false)) return true;
    }
    return false;
  }

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

      FilePath[] roots = myScopeInfo.getRoots(vcsContext);
      if (roots == null || roots.length == 0) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }
      if (roots != null && roots.length > 0) {
        for (int i = 0; i < roots.length; i++) {
          FilePath file = roots[i];
          AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
          if (vcs == null) {
            presentation.setVisible(false);
            presentation.setEnabled(false);
            return;
          }
          UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
          if (updateEnvironment == null) {
            presentation.setVisible(false);
            presentation.setEnabled(false);
            return;
          }

        }
      }
    } else {
      presentation.setVisible(false);
      presentation.setEnabled(false);      
    }

  }

}
