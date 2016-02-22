/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.edu.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(name = "CCProjectService", storages = @Storage("course_service.xml"))
public class CCProjectService implements PersistentStateComponent<CCProjectService> {
  private Course myCourse;

  private static final Map<Document, EduDocumentListener> myDocumentListeners = new HashMap<Document, EduDocumentListener>();

  @Nullable
  public TaskFile getTaskFile(@NotNull final VirtualFile virtualFile) {
    VirtualFile taskDir = virtualFile.getParent();
    if (taskDir == null) {
      return null;
    }
    String taskDirName = taskDir.getName();
    if (!taskDirName.contains(EduNames.TASK)) {
      return null;
    }
    VirtualFile lessonDir = taskDir.getParent();
    if (lessonDir == null) {
      return null;
    }
    String lessonDirName = lessonDir.getName();
    if (!lessonDirName.contains(EduNames.LESSON)) {
      return null;
    }
    Lesson lesson = myCourse.getLesson(lessonDirName);
    if (lesson == null) {
      return null;
    }
    Task task = lesson.getTask(taskDir.getName());
    if (task == null) {
      return null;
    }
    String fileName = getRealTaskFileName(virtualFile.getName());
    return task.getTaskFile(fileName);
  }

  public void drawAnswerPlaceholders(@NotNull final VirtualFile virtualFile, @NotNull final Editor editor) {
    TaskFile taskFile = getTaskFile(virtualFile);
    if (taskFile == null) {
      return;
    }
    List<AnswerPlaceholder> answerPlaceholders = taskFile.getAnswerPlaceholders();
    for (AnswerPlaceholder answerPlaceholder : answerPlaceholders) {
      EduAnswerPlaceholderPainter.drawAnswerPlaceholder(editor, answerPlaceholder, false, JBColor.BLUE);
    }
  }

  public static void addDocumentListener(Document document, EduDocumentListener listener) {
    myDocumentListeners.put(document, listener);
  }

  public static EduDocumentListener getListener(Document document) {
    return myDocumentListeners.get(document);
  }

  public static void removeListener(Document document) {
    myDocumentListeners.remove(document);
  }

  @Nullable
  public Task getTask(VirtualFile file) {
    if (myCourse == null || file == null) {
      return null;
    }
    VirtualFile taskDir = file.getParent();
    if (taskDir != null) {
      String taskDirName = taskDir.getName();
      if (taskDirName.contains(EduNames.TASK)) {
        VirtualFile lessonDir = taskDir.getParent();
        if (lessonDir != null) {
          String lessonDirName = lessonDir.getName();
          int lessonIndex = EduUtils.getIndex(lessonDirName, EduNames.LESSON);
          List<Lesson> lessons = myCourse.getLessons();
          if (!EduUtils.indexIsValid(lessonIndex, lessons)) {
            return null;
          }
          Lesson lesson = lessons.get(lessonIndex);
          int taskIndex = EduUtils.getIndex(taskDirName, EduNames.TASK);
          List<Task> tasks = lesson.getTaskList();
          if (!EduUtils.indexIsValid(taskIndex, tasks)) {
            return null;
          }
          return tasks.get(taskIndex);
        }
      }
    }
    return null;
  }

  public boolean isAnswerFile(VirtualFile file) {
    Task task = getTask(file);
    String fileName = getRealTaskFileName(file.getName());
    return task != null && fileName != null && task.isTaskFile(fileName);
  }

  public boolean isTaskFile(VirtualFile file) {
    Task task = getTask(file);
    return task != null && task.isTaskFile(file.getName());
  }

  @Nullable
  public static String getRealTaskFileName(String name) {
    String nameWithoutExtension = FileUtil.getNameWithoutExtension(name);
    String extension = FileUtilRt.getExtension(name);
    if (!nameWithoutExtension.endsWith(".answer")) {
      return null;
    }
    int nameEnd = name.indexOf(".answer");
    return name.substring(0, nameEnd) + "." + extension;
  }

  public static boolean setCCActionAvailable(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return false;
    }
    if (getInstance(project).getCourse() == null) {
      EduUtils.enableAction(e, false);
      return false;
    }
    EduUtils.enableAction(e, true);
    return true;
  }

  public Course getCourse() {
    return myCourse;
  }

  public void setCourse(@NotNull final Course course) {
    myCourse = course;
  }

  @Override
  public CCProjectService getState() {
    return this;
  }

  @Override
  public void loadState(CCProjectService state) {
    XmlSerializerUtil.copyBean(state, this);
    myCourse.initCourse(true);
  }

  public static CCProjectService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CCProjectService.class);
  }
}
