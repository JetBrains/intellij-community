/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.stepik;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Using postToStepik via ourClient garant authorization
public class StepikConnectorPost {
  private static final Logger LOG = Logger.getInstance(StepikConnectorPost.class.getName());
  static final private Gson GSON =
    new GsonBuilder().registerTypeAdapter(TaskFile.class, new StudySerializationUtils.Json.StepikTaskFileAdapter())
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

  // TODO All methods must be rewrite by this, else NPE from ourClient
  static boolean postToStepik(String link, AbstractHttpEntity entity) throws IOException {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + link);
    request.setEntity(entity);

    final CloseableHttpResponse response = StepikConnectorLogin.getHttpClient().execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepik returned non 200 status code " + responseString);
    }
    return true;
  }

  private static <T> T postToStepik(String link, final Class<T> container, String requestBody) throws IOException {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + link);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));


    final CloseableHttpResponse response = StepikConnectorLogin.getHttpClient().execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
      throw new IOException("Stepik returned non " + HttpStatus.SC_CREATED + " status code " + responseString);
    }
    //LOG.info("request "+requestBody);
    //LOG.info("response "+responseString);
    return GSON.fromJson(responseString, container);
  }

  private static void postToStepikVoid(String link, final Class<?> container, String requestBody) throws IOException {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + link);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    final CloseableHttpResponse response = StepikConnectorLogin.getHttpClient().execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
      throw new IOException("Stepik returned non " + HttpStatus.SC_CREATED + " status code " + responseString);
    }
    //LOG.info("request "+requestBody);
    //LOG.info("response "+responseString);
    return;
  }


  public static StepikWrappers.AttemptContainer getAttempt(int stepId) {
    String requestBody = new Gson().toJson(new StepikWrappers.AttemptWrapper(stepId));
    try {
      return postToStepik(EduStepikNames.ATTEMPTS, StepikWrappers.AttemptContainer.class, requestBody);
    }
    catch (IOException e) {
      LOG.warn("Can not get Attempt\n" + e.toString());
      throw new NullPointerException(e.getMessage());
      //      return null;
    }
  }

  @Deprecated
  public static StepikWrappers.SubmissionContainer postSubmission(String text, String attemptId) {
    String requestBody = new Gson().toJson(new StepikWrappers.SubmissionToPostWrapper(attemptId, "java8", text));
    try {
      return postToStepik(EduStepikNames.SUBMISSIONS, StepikWrappers.SubmissionContainer.class, requestBody);
    }
    catch (IOException e) {
      LOG.warn("Can not post Submission\n" + e.toString());
      return null;
    }
  }

  public static StepikWrappers.SubmissionContainer postSubmission(StepikWrappers.SubmissionToPostWrapper submissionToPostWrapper) {
    String requestBody = new Gson().toJson(submissionToPostWrapper);
    try {
      return postToStepik(EduStepikNames.SUBMISSIONS, StepikWrappers.SubmissionContainer.class, requestBody);
    }
    catch (IOException e) {
      LOG.warn("Can not post Submission\n" + e.toString());
      return null;
    }
  }

  // TODO realise
  public static void postAttempt(@NotNull final Task task) {
    if (task.getStepikId() <= 0) {
      return;
    }

    final HttpPost attemptRequest = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.ATTEMPTS);
    String attemptRequestBody = new Gson().toJson(new StepikWrappers.AttemptWrapper(task.getStepikId()));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse attemptResponse = StepikConnectorLogin.getHttpClient().execute(attemptRequest);
      final HttpEntity responseEntity = attemptResponse.getEntity();
      final String attemptResponseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = attemptResponse.getStatusLine();
      if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to make attempt " + attemptResponseString);
      }
      final StepikWrappers.AttemptWrapper.Attempt attempt =
        new Gson().fromJson(attemptResponseString, StepikWrappers.AttemptContainer.class).attempts.get(0);

      final Map<String, TaskFile> taskFiles = task.getTaskFiles();
      final ArrayList<StepikWrappers.SolutionFile> files = new ArrayList<StepikWrappers.SolutionFile>();
      for (TaskFile fileEntry : taskFiles.values()) {
        files.add(new StepikWrappers.SolutionFile(fileEntry.name, fileEntry.text));
      }
      //postSubmission(passed, attempt, files);
      postSubmission(true, attempt, files);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  // TODO realise
  private static void postSubmission(boolean passed,
                                     StepikWrappers.AttemptWrapper.Attempt attempt,
                                     ArrayList<StepikWrappers.SolutionFile> files) throws IOException {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.SUBMISSIONS);

    String requestBody = new Gson().toJson(new StepikWrappers.SubmissionContainer(attempt.id, passed ? "1" : "0", files));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
    final CloseableHttpResponse response = StepikConnectorLogin.getHttpClient().execute(request);
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    final StatusLine line = response.getStatusLine();
    if (line.getStatusCode() != HttpStatus.SC_CREATED) {
      LOG.error("Failed to make submission " + responseString);
    }
  }

  public static boolean enrollToCourse(final int courseId) {

    final StepikWrappers.EnrollmentWrapper enrollment = new StepikWrappers.EnrollmentWrapper(String.valueOf(courseId));
    try {
      return postToStepik(EduStepikNames.ENROLLMENTS, new StringEntity(new GsonBuilder().create().toJson(enrollment)));
    }
    catch (IOException e) {
      LOG.warn("EnrollToCourse error\n" + e.getMessage());
    }
    return false;
  }

  // used by StudyCheckTask
  @Deprecated
  public static void postAttempt(@NotNull final Task task, boolean passed, @Nullable String login, @Nullable String password) {
    //if (task.getStepikId() <= 0) {
    //  return;
    //}
    //if (ourClient == null) {
    //  if (StringUtil.isEmptyOrSpaces(login) || StringUtil.isEmptyOrSpaces(password)) {
    //    return;
    //  }
    //  else {
    //    if (login(login, password) == null) return;
    //  }
    //}

    final HttpPost attemptRequest = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.ATTEMPTS);
    //setHeaders(attemptRequest, "application/json");
    String attemptRequestBody = new Gson().toJson(new StepikWrappers.AttemptWrapper(task.getStepikId()));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse attemptResponse = StepikConnectorLogin.getHttpClient().execute(attemptRequest);
      final HttpEntity responseEntity = attemptResponse.getEntity();
      final String attemptResponseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = attemptResponse.getStatusLine();
      if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to make attempt " + attemptResponseString);
      }
      final StepikWrappers.AttemptWrapper.Attempt attempt =
        new Gson().fromJson(attemptResponseString, StepikWrappers.AttemptContainer.class).attempts.get(0);

      final Map<String, TaskFile> taskFiles = task.getTaskFiles();
      final ArrayList<StepikWrappers.SolutionFile> files = new ArrayList<StepikWrappers.SolutionFile>();
      for (TaskFile fileEntry : taskFiles.values()) {
        files.add(new StepikWrappers.SolutionFile(fileEntry.name, fileEntry.text));
      }
      postSubmission(passed, attempt, files);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  public static void postCourseWithProgress(final Project project, @NotNull final Course course) {
    postCourseWithProgress(project, course, false);
  }

  public static void postCourseWithProgress(final Project project, @NotNull final Course course, final boolean relogin) {
    ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Modal(project, "Uploading Course", true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        postCourse(project, course, relogin, indicator);
      }
    });
  }

  //TODO rewrite with postToStepik
  private static void postCourse(final Project project,
                                 @NotNull Course course,
                                 boolean relogin,
                                 @NotNull final ProgressIndicator indicator) {
    indicator.setText("Uploading course to " + EduStepikNames.STEPIK_URL);
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + "courses");
    CloseableHttpClient ourClient = StepikConnectorLogin.getHttpClient();
    if (ourClient == null || !relogin) {
      if (!StepikConnectorLogin.loginFromDialog(project)) return;
    }
    final StepikWrappers.AuthorWrapper user = StepikConnectorGet.getCurrentUser();
    if (user != null) {
      course.setAuthors(user.users);
    }

    String requestBody = new Gson().toJson(new StepikWrappers.CourseWrapper(course));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        if (!relogin) {
          StepikConnectorLogin.loginFromDialog(project);
          postCourse(project, course, true, indicator);
        }
        LOG.error("Failed to push " + responseString);
        return;
      }
      final CourseInfo postedCourse = new Gson().fromJson(responseString, StepikWrappers.CoursesContainer.class).courses.get(0);

      final int sectionId = postModule(postedCourse.id, 1, String.valueOf(postedCourse.getName()));
      int position = 1;
      for (Lesson lesson : course.getLessons()) {
        indicator.checkCanceled();
        final int lessonId = postLesson(project, lesson, indicator);
        postUnit(lessonId, position, sectionId);
        position += 1;
      }
      ApplicationManager.getApplication().runReadAction(() -> postAdditionalFiles(project, postedCourse.id, indicator));
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static void postAdditionalFiles(@NotNull final Project project, int id, ProgressIndicator indicator) {
    final VirtualFile baseDir = project.getBaseDir();
    final List<VirtualFile> files = VfsUtil.getChildren(baseDir, new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        final String name = file.getName();
        return !name.contains(EduNames.LESSON) && !name.equals(EduNames.COURSE_META_FILE) && !name.equals(EduNames.HINTS) &&
               !"pyc".equals(file.getExtension()) && !file.isDirectory() && !name.equals(EduNames.TEST_HELPER) && !name.startsWith("");
      }
    });

    if (!files.isEmpty()) {
      final int sectionId = postModule(id, 2, EduNames.PYCHARM_ADDITIONAL);
      final Lesson lesson = new Lesson();
      lesson.setName(EduNames.PYCHARM_ADDITIONAL);
      final Task task = new Task();
      task.setLesson(lesson);
      task.setName(EduNames.PYCHARM_ADDITIONAL);
      task.setIndex(1);
      task.setText(EduNames.PYCHARM_ADDITIONAL);
      for (VirtualFile file : files) {
        try {
          if (file != null) {
            if (EduUtils.isImage(file.getName())) {
              task.addTestsTexts(file.getName(), Base64.encodeBase64URLSafeString(FileUtil.loadBytes(file.getInputStream())));
            }
            else {
              task.addTestsTexts(file.getName(), FileUtil.loadTextAndClose(file.getInputStream()));
            }
          }
        }
        catch (IOException e) {
          LOG.error("Can't find file " + file.getPath());
        }
      }
      lesson.addTask(task);
      lesson.setIndex(1);
      final int lessonId = postLesson(project, lesson, indicator);
      postUnit(lessonId, 1, sectionId);
    }
  }

  private static void postUnit(int lessonId, int position, int sectionId) {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.UNITS);
    final StepikWrappers.UnitWrapper unitWrapper = new StepikWrappers.UnitWrapper();
    unitWrapper.unit = new StepikWrappers.Unit();
    unitWrapper.unit.lesson = lessonId;
    unitWrapper.unit.position = position;
    unitWrapper.unit.section = sectionId;

    String requestBody = new Gson().toJson(unitWrapper);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = StepikConnectorLogin.getHttpClient().execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to push " + responseString);
      }
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static int postModule(int courseId, int position, @NotNull final String title) {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + "sections");
    final StepikWrappers.Section section = new StepikWrappers.Section();
    section.course = courseId;
    section.title = title;
    section.position = position;
    final StepikWrappers.SectionWrapper sectionContainer = new StepikWrappers.SectionWrapper();
    sectionContainer.section = section;
    String requestBody = new Gson().toJson(sectionContainer);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = StepikConnectorLogin.getHttpClient().execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to push " + responseString);
      }
      final StepikWrappers.Section
        postedSection = new Gson().fromJson(responseString, StepikWrappers.SectionContainer.class).sections.get(0);
      return postedSection.id;
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static int postLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.LESSONS);
    CloseableHttpClient ourClient = StepikConnectorLogin.getHttpClient();
    if (ourClient == null) {
      StepikConnectorLogin.loginFromDialog(project);
    }

    String requestBody = new Gson().toJson(new StepikWrappers.LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to push " + responseString);
        return 0;
      }
      final Lesson postedLesson = new Gson().fromJson(responseString, Course.class).getLessons().get(0);
      lesson.setId(postedLesson.getId());
      for (Task task : lesson.getTaskList()) {
        indicator.checkCanceled();
        postTask(project, task, postedLesson.getId());
      }
      return postedLesson.getId();
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static void postTask(final Project project, @NotNull final Task task, final int lessonId) {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.STEP_SOURCES);
    //setHeaders(request, "application/json");
    //TODO: register type adapter for task files here?
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    ApplicationManager.getApplication().invokeLater(() -> {
      final String requestBody = gson.toJson(new StepikWrappers.StepSourceWrapper(project, task, lessonId));
      request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

      try {
        final CloseableHttpResponse response = StepikConnectorLogin.getHttpClient().execute(request);
        final StatusLine line = response.getStatusLine();
        if (line.getStatusCode() != HttpStatus.SC_CREATED) {
          final HttpEntity responseEntity = response.getEntity();
          final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
          LOG.error("Failed to push " + responseString);
        }
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
    });
  }

  public static int updateLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPut request = new HttpPut(EduStepikNames.STEPIK_API_URL + EduStepikNames.LESSONS + String.valueOf(lesson.getId()));
    CloseableHttpClient ourClient = StepikConnectorLogin.getHttpClient();
    if (ourClient == null) {
      if (!StepikConnectorLogin.loginFromDialog(project)) {
        LOG.error("Failed to push lesson");
        return 0;
      }
    }

    String requestBody = new Gson().toJson(new StepikWrappers.LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_OK) {
        LOG.error("Failed to push " + responseString);
        return 0;
      }
      final Lesson postedLesson = new Gson().fromJson(responseString, Course.class).getLessons().get(0);
      for (Integer step : postedLesson.steps) {
        deleteTask(step);
      }

      for (Task task : lesson.getTaskList()) {
        indicator.checkCanceled();
        postTask(project, task, lesson.getId());
      }
      return lesson.getId();
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static void deleteTask(@NotNull final Integer task) {
    final HttpDelete request = new HttpDelete(EduStepikNames.STEPIK_API_URL + EduStepikNames.STEP_SOURCES + task);
    //setHeaders(request, "application/json");
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        final CloseableHttpResponse response = StepikConnectorLogin.getHttpClient().execute(request);
        final StatusLine line = response.getStatusLine();
        if (line.getStatusCode() != HttpStatus.SC_NO_CONTENT) {
          final HttpEntity responseEntity = response.getEntity();
          final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
          LOG.error("Failed to delete task " + responseString);
        }
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
    });
  }

  public static void postMetric(StepikWrappers.MetricsWrapper metric) {
    String requestBody = GSON.toJson(metric);
    LOG.info(requestBody.toString());
    try {
//      postToStepikVoid(EduStepikNames.METRICS, StepikWrappers.MetricsWrapper.class, requestBody);
//      return;
      throw new IOException();
    }
    catch (IOException e) {
      LOG.warn("Can't post a metric\n" + e.toString());
      return;
    }
  }

}
