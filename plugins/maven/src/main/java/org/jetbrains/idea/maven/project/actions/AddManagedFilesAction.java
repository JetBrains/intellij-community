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
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider;

import java.util.List;

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
    FileChooserDescriptor singlePomSelection = new FileChooserDescriptor(true, true, false, false, false, true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
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
      MavenOpenProjectProvider openProjectProvider = new MavenOpenProjectProvider();
      openProjectProvider.linkToExistingProject(files[0], project);
    }
    else if (files.length > 1) {
      List<VirtualFile> mavenFiles = ContainerUtil.filter(files, it -> !it.isDirectory());
      if (!mavenFiles.isEmpty()) {
        manager.addManagedFilesOrUnignore(mavenFiles);
      }
    }
  }
}
