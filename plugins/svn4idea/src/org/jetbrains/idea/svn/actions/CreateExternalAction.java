/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnProgressCanceller;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.dialogs.SelectCreateExternalTargetDialog;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.jetbrains.idea.svn.update.UpdateClient;

import java.io.File;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getVcsForFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.UtilKt.getIfSingle;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.commandLine.CommandUtil.escape;
import static org.jetbrains.idea.svn.properties.ExternalsDefinitionParser.parseExternalsProperty;

public class CreateExternalAction extends DumbAwareAction {
  public CreateExternalAction() {
    super(message("svn.create.external.below.action"), message("svn.create.external.below.description"), null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = notNull(getIfSingle(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM)));
    SelectCreateExternalTargetDialog dialog = new SelectCreateExternalTargetDialog(project, file);

    if (dialog.showAndGet()) {
      String url = dialog.getSelectedURL();
      boolean checkout = dialog.isCheckout();
      String target = dialog.getLocalTarget().trim();

      new Task.Backgroundable(project, "Creating External") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          doInBackground(project, file, url, checkout, target);
        }
      }.queue();
    }
  }

  private static void doInBackground(@NotNull Project project, @NotNull VirtualFile file, String url, boolean checkout, String target) {
    SvnVcs vcs = SvnVcs.getInstance(project);
    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    File ioFile = virtualToIoFile(file);

    try {
      addToExternalProperty(vcs, ioFile, target, url);
      dirtyScopeManager.fileDirty(file);

      if (checkout) {
        UpdateClient client = vcs.getFactory(ioFile).createUpdateClient();
        client.setEventHandler(new SvnProgressCanceller());
        client.doUpdate(ioFile, Revision.HEAD, Depth.UNKNOWN, false, false);
        file.refresh(true, true, () -> dirtyScopeManager.dirDirtyRecursively(file));
      }
    }
    catch (VcsException e) {
      AbstractVcsHelper.getInstance(project).showError(e, "Create External");
    }
  }

  public static void addToExternalProperty(@NotNull SvnVcs vcs, @NotNull File ioFile, String target, String url) throws VcsException {
    ClientFactory factory = vcs.getFactory(ioFile);
    PropertyValue propertyValue =
      factory.createPropertyClient().getProperty(Target.on(ioFile), SvnPropertyKeys.SVN_EXTERNALS, false, Revision.UNDEFINED);
    boolean hasExternals = propertyValue != null && !isEmptyOrSpaces(propertyValue.toString());
    String newExternals = "";

    if (hasExternals) {
      String externalsForTarget = parseExternalsProperty(propertyValue.toString()).get(target);

      if (externalsForTarget != null) {
        throw new VcsException("Selected destination conflicts with existing: " + externalsForTarget);
      }

      newExternals = propertyValue.toString().trim() + "\n";
    }

    newExternals += escape(url) + " " + target;
    factory.createPropertyClient()
      .setProperty(ioFile, SvnPropertyKeys.SVN_EXTERNALS, PropertyValue.create(newExternals), Depth.EMPTY, false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean visible = project != null && isSvnActive(project);
    boolean enabled = visible && isEnabled(project, getIfSingle(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM)));

    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isSvnActive(@NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME);
  }

  private static boolean isEnabled(@NotNull Project project, @Nullable VirtualFile file) {
    return file != null &&
           file.isDirectory() &&
           getVcsForFile(file, project) instanceof SvnVcs &&
           isEnabled(FileStatusManager.getInstance(project).getStatus(file));
  }

  private static boolean isEnabled(@Nullable FileStatus status) {
    return status != null &&
           !FileStatus.DELETED.equals(status) &&
           !FileStatus.IGNORED.equals(status) &&
           !FileStatus.MERGED_WITH_PROPERTY_CONFLICTS.equals(status) &&
           !FileStatus.OBSOLETE.equals(status) &&
           !FileStatus.UNKNOWN.equals(status);
  }
}
