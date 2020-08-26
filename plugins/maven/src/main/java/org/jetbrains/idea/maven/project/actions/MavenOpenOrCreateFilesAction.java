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

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class MavenOpenOrCreateFilesAction extends MavenAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Presentation p = e.getPresentation();

    List<File> files = getFiles(e);
    if (files.isEmpty()) {
      p.setEnabled(false);
      return;
    }

    List<VirtualFile> virtualFiles = collectVirtualFiles(files);

    String text;
    boolean enabled = true;

    if (files.size() == 1 && virtualFiles.isEmpty()) {
      p.setText(MavenProjectBundle.message("action.Maven.create.file", files.get(0).getName()));
    }
    else {
      enabled = virtualFiles.size() == files.size();
      p.setText(MavenProjectBundle.message("action.Maven.open.file", files.get(0).getName()));
    }


    p.setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = MavenActionUtil.getProject(e.getDataContext());
    if(project == null) return;
    final List<File> files = getFiles(e);
    final List<VirtualFile> virtualFiles = collectVirtualFiles(files);

    if (files.size() == 1 && virtualFiles.isEmpty()) {
      WriteCommandAction.writeCommandAction(project).withName(e.getPresentation().getText()).run(() -> {
        File file = files.get(0);
        try {
          final VirtualFile virtualFile = VfsUtil.createDirectoryIfMissing(file.getParent());
          if (virtualFile != null) {
            VirtualFile newFile = virtualFile.createChildData(this, file.getName());
            virtualFiles.add(newFile);
            MavenUtil.runFileTemplate(project, newFile, getFileTemplate());
          }
        }
        catch (IOException ex) {
          MavenUtil.showError(project, MavenProjectBundle.message("notification.title.cannot.create", file.getName()), ex);
        }
      });
      return;
    }

    for (VirtualFile each : virtualFiles) {
      PsiNavigationSupport.getInstance().createNavigatable(project, each, -1).navigate(true);
    }
  }

  private static List<VirtualFile> collectVirtualFiles(List<File> files) {
    List<VirtualFile> result = new ArrayList<>();
    for (File each : files) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(each);
      if (virtualFile != null) result.add(virtualFile);
    }
    return result;
  }

  protected abstract List<File> getFiles(AnActionEvent e);

  protected abstract String getFileTemplate();
}
