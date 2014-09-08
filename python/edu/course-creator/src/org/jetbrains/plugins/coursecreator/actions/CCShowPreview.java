/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.*;

import java.util.Map;

public class CCShowPreview extends DumbAwareAction {
  public CCShowPreview() {
    super("Show preview","Show preview", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(false);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file != null && file.getName().contains(".answer")) {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file == null || !file.getName().contains(".answer")) {
      return;
    }
    final PsiDirectory taskDir = file.getContainingDirectory();
    if (taskDir == null) {
      return;
    }
    PsiDirectory lessonDir = taskDir.getParentDirectory();
    if (lessonDir == null) {
      return;
    }
    Course course = CCProjectService.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    Lesson lesson = course.getLesson(lessonDir.getName());
    Task task = lesson.getTask(taskDir.getName());
    TaskFile taskFile = task.getTaskFile(file.getName());
    final Map<TaskFile, TaskFile> taskFilesCopy = new HashMap<TaskFile, TaskFile>();
    for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      if (entry.getValue() == taskFile) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            CreateCourseArchive.createUserFile(project, taskFilesCopy, taskDir.getVirtualFile(), taskDir.getVirtualFile(), entry);
          }
        });
      }
    }
    String userFileName = FileUtil.getNameWithoutExtension(file.getName()) + ".py";
    VirtualFile userFile = taskDir.getVirtualFile().findChild(userFileName);
    if (userFile != null) {
      FileEditorManager.getInstance(project).openFile(userFile, true);
      Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      for (TaskWindow taskWindow : taskFile.getTaskWindows()) {
        taskWindow.drawHighlighter(editor, true);
      }
      CreateCourseArchive.resetTaskFiles(taskFilesCopy);
    }
  }
}