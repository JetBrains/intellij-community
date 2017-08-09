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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AppIcon;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.stepic.EduStepicAuthorizedClient;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import com.jetbrains.edu.learning.stepic.StepicWrappers;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.RestService;
import org.jetbrains.io.Responses;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.edu.learning.builtInServer.EduBuiltInServerUtils.*;
import static com.jetbrains.edu.learning.stepic.EduStepicNames.EDU_STEPIK_SERVICE_NAME;
import static com.jetbrains.edu.learning.stepic.EduStepicNames.LINK;

public class EduStepikRestService extends RestService {
  private static final Logger LOG = Logger.getInstance(EduStepikRestService.class.getName());
  private static final Pattern OPEN_COURSE_PATTERN = Pattern.compile("/" + EDU_STEPIK_SERVICE_NAME + "\\?link=.+");
  private static final Pattern COURSE_PATTERN = Pattern.compile("https://stepik\\.org/lesson/[a-zA-Z\\-]*-(\\d+)/step/(\\d+)");
  private static final Pattern
    OAUTH_CODE_PATTERN = Pattern.compile("/" + RestService.PREFIX + "/" + EDU_STEPIK_SERVICE_NAME + "/oauth" + "\\?code=(\\w+)");

  @NotNull
  private static String log(@NotNull String message) {
    LOG.info(message);
    return message;
  }

  @NotNull
  @Override
  protected String getServiceName() {
    return EDU_STEPIK_SERVICE_NAME;
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.GET;
  }

  @Override
  protected boolean isPrefixlessAllowed() {
    return true;
  }

  @Override
  protected boolean isHostTrusted(@NotNull FullHttpRequest request) throws InterruptedException, InvocationTargetException {
    String uri = request.uri();
    Matcher codeMatcher = OAUTH_CODE_PATTERN.matcher(uri);
    if (request.method() == HttpMethod.GET && codeMatcher.matches()) {
      return true;
    }
    return super.isHostTrusted(request);
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context)
    throws IOException {
    String uri = urlDecoder.uri();
    LOG.info("Request: " + uri);

    Matcher matcher = OPEN_COURSE_PATTERN.matcher(uri);
    if (matcher.matches()) {
      int courseId;
      int stepId;
      String link = getStringParameter(LINK, urlDecoder);

      if (link == null) {
        return log("The link parameter was not found");
      }

      LOG.info("Try to open a course: " + link);

      QueryStringDecoder linkDecoder = new QueryStringDecoder(link);

      matcher = COURSE_PATTERN.matcher(linkDecoder.path());

      if (!matcher.matches()) {
        return log("Unrecognized the link parameter");
      }

      int lessonId;
      int stepIndex;
      try {
        lessonId = Integer.parseInt(matcher.group(1));
        stepIndex = Integer.parseInt(matcher.group(2));
      } catch (NumberFormatException e) {
        return log("Unrecognized the link");
      }

      int unitId = getIntParameter("unit", linkDecoder);

      if (unitId == -1) {
        return log("Unrecognized the Unit id");
      }

      StepicWrappers.Unit unit = EduStepicConnector.getUnit(unitId);
      if (unit.getId() == 0) {
        return log("Unrecognized the Unit id");
      }

      StepicWrappers.Section section = EduStepicConnector.getSection(unit.getSection());
      courseId = section.getCourse();
      if (courseId == 0) {
        return log("Unrecognized the course id");
      }
      Lesson lesson = EduStepicConnector.getLesson(lessonId);
      List<Integer> stepIds = lesson.steps;

      if (stepIds.isEmpty()) {
        return log("Unrecognized the step id");
      }
      stepId = stepIds.get(stepIndex - 1);

      LOG.info(String.format("Try to open a course: courseId=%s, stepId=%s", courseId, stepId));

      if (focusOpenProject(courseId, stepId) || openRecentProject(courseId, stepId) || createProject(courseId, stepId)) {
        RestService.sendOk(request, context);
        LOG.info("Course opened: " + courseId);
        return null;
      }

      RestService.sendStatus(HttpResponseStatus.NOT_FOUND, false, context.channel());
      String message = "A project didn't found or created";
      LOG.info(message);
      return message;
    }

    Matcher codeMatcher = OAUTH_CODE_PATTERN.matcher(uri);
    if (codeMatcher.matches()) {
      String code = getStringParameter("code", urlDecoder);
      if (code != null) {
        StepicUser stepicUser = EduStepicAuthorizedClient.login(code, EduStepicConnector.getOAuthRedirectUrl());
        if (stepicUser != null) {
          StudySettings.getInstance().setUser(stepicUser);
          sendHtmlResponse(request, context, "/oauthResponsePages/okPage.html");
          showStepicNotification(NotificationType.INFORMATION,
                                 "Logged in as " + stepicUser.getFirstName() + " " + stepicUser.getLastName());
          focusOnApplicationWindow();
          return null;
        }
      }

      sendHtmlResponse(request, context, "/oauthResponsePages/errorPage.html");
      showStepicNotification(NotificationType.ERROR, "Failed to log in");
      return "Couldn't find code parameter for Stepik OAuth";
    }

    RestService.sendStatus(HttpResponseStatus.BAD_REQUEST, false, context.channel());
    String message = "Unknown command: " + uri;
    LOG.info(message);
    return message;
  }

  private static void focusOnApplicationWindow() {
    JFrame frame = WindowManager.getInstance().findVisibleFrame();
    ApplicationManager.getApplication().invokeLater(() -> {
      AppIcon.getInstance().requestFocus((IdeFrame)frame);
      frame.toFront();
    });
  }

  private void sendHtmlResponse(@NotNull HttpRequest request, @NotNull ChannelHandlerContext context, String pagePath) throws IOException {
    BufferExposingByteArrayOutputStream byteOut = new BufferExposingByteArrayOutputStream();
    InputStream pageTemplateStream = getClass().getResourceAsStream(pagePath);
    String pageTemplate = StreamUtil.readText(pageTemplateStream, Charset.forName("UTF-8"));
    try {
      String pageWithProductName = pageTemplate.replaceAll("%IDE_NAME", ApplicationNamesInfo.getInstance().getFullProductName());
      byteOut.write(StreamUtil.loadFromStream(new ByteArrayInputStream(pageWithProductName.getBytes(Charset.forName("UTF-8")))));
      HttpResponse response = Responses.response("text/html", Unpooled.wrappedBuffer(byteOut.getInternalBuffer(), 0, byteOut.size()));
      Responses.addNoCache(response);
      response.headers().set("X-Frame-Options", "Deny");
      Responses.send(response, context.channel(), request);
    }
    finally {
      byteOut.close();
      pageTemplateStream.close();
    }
  }

  private static void showStepicNotification(@NotNull NotificationType notificationType, @NotNull String text) {
    Notification notification = new Notification("Stepik", "Stepik", text, notificationType);
    notification.notify(null);
  }
}
