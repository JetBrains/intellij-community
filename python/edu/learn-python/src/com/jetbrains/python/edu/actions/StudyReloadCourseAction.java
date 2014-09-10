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
package com.jetbrains.python.edu.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.tree.TreeUtil;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.Lesson;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Map;

public class StudyReloadCourseAction extends DumbAwareAction {

  public StudyReloadCourseAction() {
    super("Reload Course", "Reload Course", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project != null) {
      Course course = StudyTaskManager.getInstance(project).getCourse();
      if (course != null) {
        presentation.setVisible(true);
        presentation.setEnabled(true);
      }
    }
    presentation.setVisible(false);
    presentation.setEnabled(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    reloadCourse(project);
  }

  public static void reloadCourse(@NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            Course course = StudyTaskManager.getInstance(project).getCourse();
            if (course == null) {
              return;
            }
            for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
              FileEditorManager.getInstance(project).closeFile(file);
            }
            JTree tree = ProjectView.getInstance(project).getCurrentProjectViewPane().getTree();
            TreePath path = TreeUtil.getFirstNodePath(tree);
            tree.collapsePath(path);
            List<Lesson> lessons = course.getLessons();
            for (Lesson lesson : lessons) {
              List<Task> tasks = lesson.getTaskList();
              VirtualFile lessonDir = project.getBaseDir().findChild(Lesson.LESSON_DIR + (lesson.getIndex() + 1));
              if (lessonDir == null) {
                continue;
              }
              for (Task task : tasks) {
                VirtualFile taskDir = lessonDir.findChild(Task.TASK_DIR + (task.getIndex() + 1));
                if (taskDir == null) {
                  continue;
                }
                Map<String, TaskFile> taskFiles = task.getTaskFiles();
                for (Map.Entry<String, TaskFile> entry : taskFiles.entrySet()) {
                  String name = entry.getKey();
                  TaskFile taskFile = entry.getValue();
                  VirtualFile file = taskDir.findChild(name);
                  if (file == null) {
                    continue;
                  }
                  Document document = FileDocumentManager.getInstance().getDocument(file);
                  if (document == null) {
                    continue;
                  }
                  StudyRefreshTaskFileAction.resetTaskFile(document, project, course, taskFile, name, task);
                }
              }
            }
            Lesson firstLesson = StudyUtils.getFirst(lessons);
            if (firstLesson == null) {
              return;
            }
            Task firstTask = StudyUtils.getFirst(firstLesson.getTaskList());
            VirtualFile lessonDir = project.getBaseDir().findChild(Lesson.LESSON_DIR + (firstLesson.getIndex() + 1));
            if (lessonDir != null) {
              VirtualFile taskDir = lessonDir.findChild(Task.TASK_DIR + (firstTask.getIndex() + 1));
              if (taskDir != null) {
                ProjectView.getInstance(project).select(taskDir, taskDir, true);
              }
            }
          }
        });
      }
    });
  }
}
