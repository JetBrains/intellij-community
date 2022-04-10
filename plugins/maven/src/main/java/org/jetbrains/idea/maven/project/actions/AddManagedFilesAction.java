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
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider;

import static com.intellij.openapi.ui.UiUtils.getPresentablePath;

public class AddManagedFilesAction extends MavenAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = MavenActionUtil.getProject(e.getDataContext());
    if (project == null) {
      return;
    }
    MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(project);
    if (manager == null) {
      return;
    }
    FileChooserDescriptor singlePomSelection = new FileChooserDescriptor(true, true, false, false, false, false) {
      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        return super.isFileSelectable(file) && !manager.isManagedFile(file);
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!file.isDirectory() && !MavenActionUtil.isMavenProjectFile(file)) return false;
        return super.isFileVisible(file, showHiddenFiles);
      }
    };

    VirtualFile fileToSelect = e.getData(CommonDataKeys.VIRTUAL_FILE);

    VirtualFile[] files = FileChooser.chooseFiles(singlePomSelection, project, fileToSelect);
    if (files.length == 1) {
      VirtualFile projectFile = files[0];
      ReadAction.nonBlocking(() -> projectFile.isDirectory() ? projectFile.getChildren() : files).
        finishOnUiThread(ModalityState.defaultModalityState(), it -> {
          if (ContainerUtil.exists(it, MavenActionUtil::isMavenProjectFile)) {
            MavenOpenProjectProvider openProjectProvider = new MavenOpenProjectProvider();
            openProjectProvider.linkToExistingProject(projectFile, project);
          }
          else {
            String projectPath = getPresentablePath(projectFile.getPath());
            String message = projectFile.isDirectory()
                             ? MavenProjectBundle.message("maven.AddManagedFiles.warning.message.directory", projectPath)
                             : MavenProjectBundle.message("maven.AddManagedFiles.warning.message.file", projectPath);
            String title = MavenProjectBundle.message("maven.AddManagedFiles.warning.title");
            Messages.showWarningDialog(project, message, title);
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }
}
