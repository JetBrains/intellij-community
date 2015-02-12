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

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.xmlb.XmlSerializer;
import com.jetbrains.edu.coursecreator.format.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(name = "CCProjectService",
       storages = {
         @Storage(file = "$PROJECT_CONFIG_DIR$/course_service.xml")
       }
)
public class CCProjectService implements PersistentStateComponent<Element> {

  private static final Logger LOG = Logger.getInstance(CCProjectService.class.getName());
  private Course myCourse;
  public static final String COURSE_ELEMENT = "course";
  private static final Map<Document, CCDocumentListener> myDocumentListeners = new HashMap<Document, CCDocumentListener>();

  public void setCourse(@NotNull final Course course) {
    myCourse = course;
  }

  public Course getCourse() {
    return myCourse;
  }

  @Override
  public Element getState() {
    final Element el = new Element("CCProjectService");
    if (myCourse != null) {
      Element courseElement = new Element(COURSE_ELEMENT);
      XmlSerializer.serializeInto(myCourse, courseElement);
      el.addContent(courseElement);
    }
    return el;
  }

  @Override
  public void loadState(Element el) {
    myCourse = XmlSerializer.deserialize(el.getChild(COURSE_ELEMENT), Course.class);
    if (myCourse != null) {
      myCourse.init();
    }
  }

  public static CCProjectService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CCProjectService.class);
  }

  public static void deleteProjectFile(File file, @NotNull final Project project) {
    if (!file.delete()) {
      LOG.info("Failed to delete file " + file.getPath());
    }
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(project).refresh();
  }

  @Nullable
  public TaskFile getTaskFile(@NotNull final VirtualFile virtualFile) {
    VirtualFile taskDir = virtualFile.getParent();
    if (taskDir == null) {
      return null;
    }
    String taskDirName = taskDir.getName();
    if (!taskDirName.contains("task")) {
      return null;
    }
    VirtualFile lessonDir = taskDir.getParent();
    if (lessonDir == null) {
      return null;
    }
    String lessonDirName = lessonDir.getName();
    if (!lessonDirName.contains("lesson")) {
      return null;
    }
    Lesson lesson = myCourse.getLessonsMap().get(lessonDirName);
    if (lesson == null) {
      return null;
    }
    Task task = lesson.getTask(taskDirName);
    if (task == null) {
      return null;
    }
    return task.getTaskFile(virtualFile.getName());
  }

  public void drawTaskWindows(@NotNull final VirtualFile virtualFile, @NotNull final Editor editor) {
    TaskFile taskFile = getTaskFile(virtualFile);
    if (taskFile == null) {
      return;
    }
    List<AnswerPlaceholder> answerPlaceholders = taskFile.getTaskWindows();
    for (AnswerPlaceholder answerPlaceholder : answerPlaceholders) {
      answerPlaceholder.drawHighlighter(editor, false);
    }
  }

  public static void addDocumentListener(Document document, CCDocumentListener listener) {
    myDocumentListeners.put(document, listener);
  }

  public static CCDocumentListener getListener(Document document) {
    return myDocumentListeners.get(document);
  }

  public static void removeListener(Document document) {
    myDocumentListeners.remove(document);
  }

  public static boolean indexIsValid(int index, Collection collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }

  public boolean isTaskFile(VirtualFile file) {
    if (myCourse == null || file == null) {
      return false;
    }
    VirtualFile taskDir = file.getParent();
    if (taskDir != null) {
      String taskDirName = taskDir.getName();
      if (taskDirName.contains("task")) {
        VirtualFile lessonDir = taskDir.getParent();
        if (lessonDir != null) {
          String lessonDirName = lessonDir.getName();
          int lessonIndex = getIndex(lessonDirName, "lesson");
          List<Lesson> lessons = myCourse.getLessons();
          if (!indexIsValid(lessonIndex, lessons)) {
            return false;
          }
          Lesson lesson = lessons.get(lessonIndex);
          int taskIndex = getIndex(taskDirName, "task");
          List<Task> tasks = lesson.getTaskList();
          if (!indexIsValid(taskIndex, tasks)) {
            return false;
          }
          Task task = tasks.get(taskIndex);
          return task.isTaskFile(file.getName());
        }
      }
    }
    return false;
  }

  public static int getIndex(@NotNull final String fullName, @NotNull final String logicalName) {
    if (!fullName.contains(logicalName)) {
      throw new IllegalArgumentException();
    }
    return Integer.parseInt(fullName.substring(logicalName.length())) - 1;
  }
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
      CCUtils.enableAction(e, false);
      return false;
    }
    CCUtils.enableAction(e, true);
    return true;
  }
}
