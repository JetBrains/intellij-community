/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.Arrays;

public class AddManagedFilesAction extends MavenAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e.getDataContext());
    FileChooserDescriptor singlePomSelection = new FileChooserDescriptor(true, false, false, false, false, true) {
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

    Project project = MavenActionUtil.getProject(e.getDataContext());
    VirtualFile fileToSelect = e.getData(PlatformDataKeys.VIRTUAL_FILE);

    FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(singlePomSelection, project);
    VirtualFile[] files = dialog.choose(fileToSelect, project);
    if (files.length == 0) return;

    manager.addManagedFiles(Arrays.asList(files));
  }
}
