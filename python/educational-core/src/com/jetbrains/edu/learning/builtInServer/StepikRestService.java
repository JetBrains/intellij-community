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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.RestService;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author meanmail
 */
public class StepikRestService extends RestService {
  public static final String STUDY_PROJECT_XML_PATH = "/.idea/study_project.xml";
  private static final Logger LOG = Logger.getInstance(StepikRestService.class.getName());
  private static final String SERVICE_NAME = "edu/stepik";
  private static final Pattern OPEN_COURSE = Pattern.compile("/" + SERVICE_NAME + "/course/[^/]*-(\\d+)(?:$|\\?|/.*)");

  private static boolean openProject(String projectPath) {
    final boolean[] opened = {false};
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final Project[] project = new Project[1];
      TransactionGuard.getInstance().submitTransactionAndWait(() -> {
        project[0] = ProjectUtil.openProject(projectPath, null, true);
        opened[0] = project[0] != null;
      });
      requestDefaultFocus(project[0]);
    });
    return opened[0];
  }

  private static void requestDefaultFocus(@NotNull Project project) {
    IdeFocusManager.getInstance(project).requestDefaultFocus(true);
  }

  @NotNull
  @Override
  protected String getServiceName() {
    return SERVICE_NAME;
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.GET;
  }

  @Override
  protected boolean isPrefixlessAllowed() {
    return true;
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context)
    throws IOException {
    LOG.info("Request: " + urlDecoder.path());

    String path = urlDecoder.path();
    Matcher matcher = OPEN_COURSE.matcher(path);
    if (matcher.matches()) {
      int targetCourseId = Integer.parseInt(matcher.group(1));
      LOG.info("Open course: " + targetCourseId);

      if (findOpenedProjectAndFocus(targetCourseId)) {
        RestService.sendOk(request, context);
        return null;
      }

      RecentProjectsManagerBase recentProjectsManager = (RecentProjectsManagerBase)RecentProjectsManager.getInstance();

      if (recentProjectsManager == null) {
        return null;
      }

      RecentProjectsManagerBase.State state = recentProjectsManager.getState();

      if (state == null) {
        return null;
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
          RestService.sendOk(request, context);
          return null;
        }
      }
      RestService.sendStatus(HttpResponseStatus.NOT_FOUND, false, context.channel());
      return "Didn't found or create a project";
    }

    RestService.sendStatus(HttpResponseStatus.BAD_REQUEST, false, context.channel());
    return "Unknown command";
  }

  private static boolean findOpenedProjectAndFocus(int targetCourseId) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
      if (taskManager != null) {
        Course course = taskManager.getCourse();
        RemoteCourse remoteCourse = course != null && course instanceof RemoteCourse ? (RemoteCourse)course : null;
        if (remoteCourse != null && remoteCourse.getId() == targetCourseId) {
          ApplicationManager.getApplication().invokeLater(() -> requestDefaultFocus(project));
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

      if ((course instanceof RemoteCourse)) {
        return ((RemoteCourse)course).getId();
      }
    }
    catch (IllegalStateException ignored) {
    }
    return 0;
  }
}
