/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.ActionExecuteHelper;
import com.intellij.vcsUtil.ActionStateConsumer;
import com.intellij.vcsUtil.ActionUpdateHelper;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.dialogs.SelectCreateExternalTargetDialog;
import org.jetbrains.idea.svn.properties.ExternalsDefinitionParser;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.jetbrains.idea.svn.update.UpdateClient;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/6/12
 * Time: 7:21 PM
 */
public class CreateExternalAction extends DumbAwareAction {
  public CreateExternalAction() {
    super(SvnBundle.message("svn.create.external.below.action"), SvnBundle.message("svn.create.external.below.description"), null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ActionExecuteHelper helper = new ActionExecuteHelper();
    checkState(e, helper);
    if (! helper.isOk()) return;

    final DataContext dc = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    final VirtualFile vf = CommonDataKeys.VIRTUAL_FILE.getData(dc);

    //1 select target
    final SelectCreateExternalTargetDialog dialog = new SelectCreateExternalTargetDialog(project, vf);
    dialog.show();
    if (DialogWrapper.OK_EXIT_CODE != dialog.getExitCode()) return;

    final String url = dialog.getSelectedURL();
    final boolean checkout = dialog.isCheckout();
    final String target = dialog.getLocalTarget().trim();

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Creating External", true, null) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        doInBackground(project, vf, url, checkout, target);
      }
    });
  }

  private void doInBackground(Project project, VirtualFile vf, String url, boolean checkout, String target) {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    try {
      final File ioFile = new File(vf.getPath());
      if (addToExternalProperty(vcs, ioFile, target, url)) return;
      final VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
      final FilePath filePath = VcsUtil.getFilePath(ioFile, true);
      dirtyScopeManager.fileDirty(filePath);
      if (checkout) {
        // +-
        final UpdateClient client = vcs.getFactory(ioFile).createUpdateClient();
        client.setEventHandler(new ProgressTracker() {
          @Override
          public void consume(ProgressEvent event) throws SVNException {
          }

          @Override
          public void checkCancelled() throws SVNCancelException {
            final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
            if (pi != null && pi.isCanceled()) throw new SVNCancelException();
          }
        });
        client.doUpdate(ioFile, SVNRevision.HEAD, Depth.UNKNOWN, false, false);
        vf.refresh(true, true, new Runnable() {
          @Override
          public void run() {
            dirtyScopeManager.dirDirtyRecursively(filePath);
          }
        });
      }
    }
    catch (SVNException e1) {
      AbstractVcsHelper.getInstance(project).showError(new VcsException(e1), "Create External");
    }
    catch (VcsException e1) {
      AbstractVcsHelper.getInstance(project).showError(e1, "Create External");
    }
  }

  public static boolean addToExternalProperty(@NotNull SvnVcs vcs, @NotNull File ioFile, String target, String url)
    throws SVNException, VcsException {
    ClientFactory factory = vcs.getFactory(ioFile);
    PropertyValue propertyValue = factory.createPropertyClient().getProperty(SvnTarget.fromFile(ioFile), SvnPropertyKeys.SVN_EXTERNALS,
                                                                              false, SVNRevision.UNDEFINED);
    String newValue;
    if (propertyValue != null && !StringUtil.isEmptyOrSpaces(propertyValue.toString())) {
      Map<String, String> externalsMap = ExternalsDefinitionParser.parseExternalsProperty(propertyValue.toString());
      String externalsForTarget = externalsMap.get(target);

      if (externalsForTarget != null) {
        AbstractVcsHelper.getInstance(vcs.getProject()).showError(
          new VcsException("Selected destination conflicts with existing: " + externalsForTarget), "Create External");
        return true;
      }
      final String string = createExternalDefinitionString(url, target);
      newValue = propertyValue.toString().trim() + "\n" + string;
    } else {
      newValue = createExternalDefinitionString(url, target);
    }
    factory.createPropertyClient().setProperty(ioFile, SvnPropertyKeys.SVN_EXTERNALS, PropertyValue.create(newValue), Depth.EMPTY, false);
    return false;
  }

  public static String createExternalDefinitionString(String url, String target) {
    return CommandUtil.escape(url) + " " + target;
  }

  @Override
  public void update(AnActionEvent e) {
    final ActionUpdateHelper helper = new ActionUpdateHelper();
    checkState(e, helper);
    helper.apply(e);
  }

  private void checkState(AnActionEvent e, final ActionStateConsumer sc) {
    final DataContext dc = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null) {
      sc.hide();
      return;
    }
    final ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
    if (!manager.checkVcsIsActive(SvnVcs.getKey().getName())) {
      sc.hide();
      return;
    }

    final VirtualFile vf = CommonDataKeys.VIRTUAL_FILE.getData(dc);
    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dc);
    if (vf == null || files == null || files.length != 1 || ! vf.isDirectory()) {
      sc.disable();
      return;
    }

    final AbstractVcs vcsFor = manager.getVcsFor(vf);
    if (vcsFor == null || ! SvnVcs.getKey().equals(vcsFor.getKeyInstanceMethod())) {
      sc.disable();
      return;
    }

    final FileStatus status = FileStatusManager.getInstance(project).getStatus(vf);
    if (status == null || FileStatus.DELETED.equals(status) || FileStatus.IGNORED.equals(status) ||
        FileStatus.MERGED_WITH_PROPERTY_CONFLICTS.equals(status) || FileStatus.OBSOLETE.equals(status) || FileStatus.UNKNOWN.equals(status)) {
      sc.disable();
      return;
    }
    sc.enable();
  }
}
