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
package org.jetbrains.plugins.coursecreator;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.format.*;

import java.io.File;
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
  public Course myCourse;
  public static final String COURSE_ELEMENT = "course";
  private static final Map<Document, StudyDocumentListener> myDocumentListeners = new HashMap<Document, StudyDocumentListener>();

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

  public static void drawTaskWindows(@NotNull final VirtualFile virtualFile, @NotNull final Editor editor, @NotNull final Course course) {
    VirtualFile taskDir = virtualFile.getParent();
    if (taskDir == null) {
      return;
    }
    String taskDirName = taskDir.getName();
    if (!taskDirName.contains("task")) {
      return;
    }
    VirtualFile lessonDir = taskDir.getParent();
    if (lessonDir == null) {
      return;
    }
    String lessonDirName = lessonDir.getName();
    if (!lessonDirName.contains("lesson")) {
      return;
    }
    Lesson lesson = course.getLessonsMap().get(lessonDirName);
    if (lesson == null) {
      return;
    }
    Task task = lesson.getTask(taskDirName);
    if (task == null) {
      return;
    }
    TaskFile taskFile = task.getTaskFile(virtualFile.getName());
    if (taskFile == null) {
      return;
    }
    List<TaskWindow> taskWindows = taskFile.getTaskWindows();
    for (TaskWindow taskWindow : taskWindows) {
      taskWindow.drawHighlighter(editor);
    }
  }

  public static void addDocumentListener(Document document, StudyDocumentListener listener) {
    myDocumentListeners.put(document, listener);
  }

  public static StudyDocumentListener getListener(Document document) {
    return myDocumentListeners.get(document);
  }

  public static void removeListener(Document document) {
    myDocumentListeners.remove(document);
  }

  public static boolean indexIsValid(int index, List<TaskWindow> collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }
}
