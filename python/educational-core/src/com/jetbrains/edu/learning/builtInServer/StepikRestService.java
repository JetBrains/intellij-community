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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.platform.ProjectSetReader;
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

  private static void openProject(String path) {
    String descriptor = String.format("{\"project\": \"%s\"}", path);
    JsonObject jsonObject = new JsonParser().parse(descriptor).getAsJsonObject();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      new ProjectSetReader().readDescriptor(jsonObject, null);
      activateLastFocusedFrame();
    });
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

        if (courseId == targetCourseId) {
          openProject(projectPath);
          RestService.sendOk(request, context);
          return null;
        }
      }
    }

    RestService.sendStatus(HttpResponseStatus.NOT_FOUND, false, context.channel());
    return "Unknown command";
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
