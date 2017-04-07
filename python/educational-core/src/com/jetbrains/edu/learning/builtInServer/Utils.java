/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.builtInServer;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.intellij.generation.EduProjectGenerator;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author meanmail
 */
public class Utils {
  public static final String STUDY_PROJECT_XML_PATH = "/.idea/study_project.xml";

  public static boolean findOpenProjectAndFocus(int courseId) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
      if (taskManager != null) {
        Course course = taskManager.getCourse();
        RemoteCourse remoteCourse = course instanceof RemoteCourse ? (RemoteCourse)course : null;
        if (remoteCourse != null && remoteCourse.getId() == courseId) {
          ApplicationManager.getApplication().invokeLater(() -> requestFocus(project));
          return true;
        }
      }
    }
    return false;
  }

  private static boolean openProject(@NotNull String projectPath) {
    final boolean[] opened = {false};
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final Project[] project = new Project[1];
      TransactionGuard.getInstance().submitTransactionAndWait(() -> {
        project[0] = ProjectUtil.openProject(projectPath, null, true);
        opened[0] = project[0] != null;
      });
      requestFocus(project[0]);
    });
    return opened[0];
  }

  private static void requestFocus(@NotNull Project project) {
    IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
    if (frame instanceof Window) {
      ((Window)frame).toFront();
    }
  }

  public static boolean findRecentProjectAndOpen(int targetCourseId) {
    RecentProjectsManagerBase recentProjectsManager;
    recentProjectsManager = (RecentProjectsManagerBase)RecentProjectsManager.getInstance();

    if (recentProjectsManager == null) {
      return false;
    }

    RecentProjectsManagerBase.State state = recentProjectsManager.getState();

    if (state == null) {
      return false;
    }

    List<String> recentPaths = state.recentPaths;

    Project project = ProjectManager.getInstance().getDefaultProject();
    StudyTaskManager taskManager = new StudyTaskManager(project);
    SAXBuilder parser = new SAXBuilder();

    for (String projectPath : recentPaths) {
      Element component = readComponent(parser, projectPath);
      if (component == null) {
        continue;
      }
      int courseId = getCourseId(taskManager, component);

      if (courseId == targetCourseId && openProject(projectPath)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static Element readComponent(@NotNull SAXBuilder parser, @NotNull String projectPath) {
    Element component = null;
    try {
      String studyProjectXML = projectPath + STUDY_PROJECT_XML_PATH;
      Document xmlDoc = parser.build(new File(studyProjectXML));
      Element root = xmlDoc.getRootElement();
      component = root.getChild("component");
    }
    catch (JDOMException | IOException ignored) {
    }

    return component;
  }

  private static int getCourseId(@NotNull StudyTaskManager taskManager, @NotNull Element component) {
    try {
      taskManager.loadState(component);
      Course course = taskManager.getCourse();

      if (course instanceof RemoteCourse) {
        return ((RemoteCourse)course).getId();
      }
    }
    catch (IllegalStateException ignored) {
    }
    return 0;
  }

  public static boolean createProjectAndOpen(int courseId) {
    EduProjectGenerator generator = new EduProjectGenerator();
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    String title = "Getting Available Courses";
    List<Course> availableCourses = new ArrayList<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      List<Course> courses = generator.getCoursesUnderProgress(true, title, defaultProject);
      availableCourses.addAll(courses);
    });
    for (Course course : availableCourses) {
      if (course instanceof RemoteCourse && ((RemoteCourse)course).getId() == courseId) {
        return EduProjectCreator.createProject(course);
      }
    }
    return false;
  }
}
