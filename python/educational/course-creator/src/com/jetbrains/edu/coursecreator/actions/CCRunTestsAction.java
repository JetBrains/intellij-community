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
package com.jetbrains.edu.coursecreator.actions;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.coursecreator.CCLanguageManager;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.CCUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

public abstract class CCRunTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(CCRunTestsAction.class.getName());

  public CCRunTestsAction() {
    getTemplatePresentation().setIcon(AllIcons.Actions.Lightning);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setText("");
    presentation.setVisible(false);
    presentation.setEnabled(false);

    final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());
    Location location = context.getLocation();
    if (location == null) {
      return;
    }
    PsiElement psiElement = location.getPsiElement();
    PsiFile psiFile = psiElement.getContainingFile();
    Project project = e.getProject();
    if (project == null || psiFile == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    final PsiDirectory taskDir = psiFile.getContainingDirectory();
    final PsiDirectory lessonDir = taskDir.getParent();
    if (lessonDir == null) return;
    if (course == null) return;
    final Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) return;
    final Task task = lesson.getTask(taskDir.getName());
    if (task == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    TaskFile taskFile = service.getTaskFile(psiFile.getVirtualFile());
    if (taskFile == null) {
      LOG.info("could not find task file");
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    if (psiFile.getName().contains(".answer")) {
      presentation.setEnabled(true);
      presentation.setVisible(true);
      presentation.setText("Run tests from '" + psiFile.getName() + "'");
    }
    else {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    final ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());
    run(context);
  }

  private void run(final @NotNull ConfigurationContext context) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final Project project = context.getProject();
        PsiElement location = context.getPsiLocation();
        final Course course = CCProjectService.getInstance(project).getCourse();
        if (course == null || location == null) {
          return;
        }
        PsiFile psiFile = location.getContainingFile();
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final VirtualFile taskDir = virtualFile.getParent();
        if (taskDir == null) {
          return;
        }
        VirtualFile lessonDir = taskDir.getParent();
        Lesson lesson = course.getLesson(lessonDir.getName());
        if (lesson == null) {
          return;
        }
        final Task task = lesson.getTask(taskDir.getName());
        if (task == null) {
          return;
        }
        clearTestEnvironment(taskDir, project);
        for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
          final String name = entry.getKey();
          CCLanguageManager manager = CCUtils.getStudyLanguageManager(course);
          if (manager == null) {
            return;
          }
          createTestEnvironment(taskDir, name, entry.getValue(), project);
          FileTemplate testsTemplate = manager.getTestsTemplate(project);
          if (testsTemplate == null) {
            return;
          }
          VirtualFile testFile = taskDir.findChild(testsTemplate.getName() + "." + testsTemplate.getExtension());
          if (testFile == null) {
            return;
          }
          executeTests(project, virtualFile, taskDir, testFile);
        }
      }
    });
  }

  private static void createTestEnvironment(@NotNull final VirtualFile taskDir, final String fileName, @NotNull final TaskFile taskFile,
                                            @NotNull final Project project) {
    try {
      String answerFileName = FileUtil.getNameWithoutExtension(fileName) + ".answer";
      final String extension = FileUtilRt.getExtension(fileName);
      final VirtualFile answerFile = taskDir.findChild(answerFileName + "." + extension);
      if (answerFile == null) {
        LOG.debug("could not find answer file " + answerFileName);
        return;
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          final FileDocumentManager documentManager = FileDocumentManager.getInstance();
          documentManager.saveAllDocuments();
        }
      });
      final VirtualFile oldTaskFile = taskDir.findChild(fileName);
      if (oldTaskFile != null) {
        oldTaskFile.delete(project);
      }
      answerFile.copy(project, taskDir, fileName);
      EduUtils.flushWindows(taskFile, answerFile, false);
      createResourceFiles(answerFile, project);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static void clearTestEnvironment(@NotNull final VirtualFile taskDir, @NotNull final Project project) {
    try {
      VirtualFile ideaDir = project.getBaseDir().findChild(".idea");
      if (ideaDir == null) {
        LOG.debug("idea directory doesn't exist");
        return;
      }
      VirtualFile courseResourceDir = ideaDir.findChild(EduNames.COURSE);
      if (courseResourceDir != null) {
        courseResourceDir.delete(project);
      }
      VirtualFile[] taskDirChildren = taskDir.getChildren();
      for (VirtualFile file : taskDirChildren) {
        if (file.getName().contains("_windows")) {
          file.delete(project);
        }
        if (CCProjectService.getInstance(project).isTaskFile(file)) {
          file.delete(project);
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  protected abstract void executeTests(@NotNull final Project project,
                                   @NotNull final VirtualFile virtualFile,
                                   @NotNull final VirtualFile taskDir,
                                   @NotNull final VirtualFile testFile);

  //some tests could compare task files after user modifications with initial task files
  private static void createResourceFiles(@NotNull final VirtualFile file, @NotNull final Project project) {
    VirtualFile taskDir = file.getParent();
    int index = EduUtils.getIndex(taskDir.getName(), EduNames.TASK);
    VirtualFile lessonDir = taskDir.getParent();
    int lessonIndex = EduUtils.getIndex(lessonDir.getName(), EduNames.LESSON);
    Course course = CCProjectService.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    VirtualFile ideaDir = project.getBaseDir().findChild(".idea");
    assert ideaDir != null;
    try {
      VirtualFile courseResourceDir = findOrCreateDir(project, ideaDir, EduNames.COURSE);
      VirtualFile lessonResourceDir = findOrCreateDir(project, courseResourceDir, lessonDir.getName());
      VirtualFile taskResourceDir = findOrCreateDir(project, lessonResourceDir, taskDir.getName());
      if (EduUtils.indexIsValid(lessonIndex, course.getLessons())) {
        Lesson lesson = course.getLessons().get(lessonIndex);
        if (EduUtils.indexIsValid(index, lesson.getTaskList())) {
          Task task = lesson.getTaskList().get(index);
          for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
            TaskFile taskFileCopy = new TaskFile();
            TaskFile.copy(entry.getValue(), taskFileCopy);
            EduUtils.createStudentFileFromAnswer(project, taskResourceDir, taskDir,
                                                 new AbstractMap.SimpleEntry<String, TaskFile>(entry.getKey(), taskFileCopy));
          }
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static VirtualFile findOrCreateDir(@NotNull final Project project, @NotNull final VirtualFile dir, String name) throws IOException {
    VirtualFile targetDir = dir.findChild(name);
    if (targetDir == null) {
      targetDir = dir.createChildDirectory(project, name);
    }
    return targetDir;
  }

}
