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

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.xmlb.XmlSerializationException;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.newproject.ui.EduCreateNewProjectDialog;
import com.jetbrains.edu.learning.newproject.ui.EduCreateNewStepikProjectDialog;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.jetbrains.edu.learning.StudyUtils.navigateToStep;
import static com.jetbrains.edu.learning.core.EduNames.STUDY_PROJECT_XML_PATH;
import static com.jetbrains.edu.learning.stepic.EduStepicNames.STEP_ID;

public class EduBuiltInServerUtils {
  public static boolean focusOpenProject(int courseId, int stepId) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      if (!project.isDefault()) {
        StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
        if (taskManager != null) {
          Course course = taskManager.getCourse();
          RemoteCourse remoteCourse = course instanceof RemoteCourse ? (RemoteCourse)course : null;
          if (remoteCourse != null && remoteCourse.getId() == courseId) {
            ApplicationManager.getApplication().invokeLater(() -> {
              requestFocus(project);
              navigateToStep(project, course, stepId);
            });
            return true;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  private static Project openProject(@NotNull String projectPath) {
    final Project[] project = {null};
    ApplicationManager.getApplication().invokeAndWait(() -> {
      TransactionGuard.getInstance().submitTransactionAndWait(() ->
        project[0] = ProjectUtil.openProject(projectPath, null, true));
      requestFocus(project[0]);
    });
    return project[0];
  }

  private static void requestFocus(@NotNull Project project) {
    ProjectUtil.focusProjectWindow(project, false);
  }

  public static boolean openRecentProject(int targetCourseId, int stepId) {
    RecentProjectsManagerBase recentProjectsManager = RecentProjectsManagerBase.getInstanceEx();

    if (recentProjectsManager == null) {
      return false;
    }

    RecentProjectsManagerBase.State state = recentProjectsManager.getState();

    if (state == null) {
      return false;
    }

    List<String> recentPaths = state.recentPaths;

    SAXBuilder parser = new SAXBuilder();

    for (String projectPath : recentPaths) {
      Element component = readComponent(parser, projectPath);
      if (component == null) {
        continue;
      }
      StudyTaskManager taskManager = getDefaultTaskManager();
      int courseId = getCourseId(taskManager, component);

      if (courseId == targetCourseId) {
        PropertiesComponent.getInstance().setValue(STEP_ID, stepId, 0);
        Project project = openProject(projectPath);
        if (project != null) {
          return true;
        }
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
    catch (IllegalStateException | XmlSerializationException ignored) {
    }
    return 0;
  }

  public static boolean createProject(int courseId, int stepId) {
    ApplicationManager.getApplication().invokeLater(() -> {
      PropertiesComponent.getInstance().setValue(STEP_ID, stepId, 0);
      EduCreateNewProjectDialog createNewProjectDlg = new EduCreateNewStepikProjectDialog(courseId);
      createNewProjectDlg.show();
    });

    return true;
  }
  @NotNull
  private static StudyTaskManager getDefaultTaskManager() {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    return StudyTaskManager.getInstance(defaultProject);
  }
}
