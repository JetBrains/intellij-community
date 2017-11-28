package com.jetbrains.edu.coursecreator.stepik;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.stepic.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.edu.learning.StudyUtils.showOAuthDialog;

public class CCStepicConnector {
  private static final Logger LOG = Logger.getInstance(CCStepicConnector.class.getName());
  private static final String FAILED_TITLE = "Failed to publish ";

  private CCStepicConnector() {
  }

  public static RemoteCourse getCourseInfo(String courseId) {
    final String url = EduStepicNames.COURSES + "/" + courseId;
    try {
      final StepicWrappers.CoursesContainer coursesContainer =
        EduStepicAuthorizedClient.getFromStepic(url, StepicWrappers.CoursesContainer.class);
      return coursesContainer == null ? null : coursesContainer.courses.get(0);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return null;
  }

  public static void postCourseWithProgress(final Project project, @NotNull final Course course) {
    ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Modal(project, "Uploading Course", true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        postCourse(project, course);
      }
    });
  }

  private static void postCourse(final Project project, @NotNull Course course) {
    if (!checkIfAuthorized(project, "post course")) return;

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText("Uploading course to " + EduStepicNames.STEPIC_URL);
      indicator.setIndeterminate(false);
    }
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/courses");

    final StepicUser currentUser = EduStepicAuthorizedClient.getCurrentUser();
    if (currentUser != null) {
      final List<StepicUser> courseAuthors = course.getAuthors();
      for (int i = 0; i < courseAuthors.size(); i++) {
        if (courseAuthors.size() > i) {
          final StepicUser courseAuthor = courseAuthors.get(i);
          currentUser.setFirstName(courseAuthor.getFirstName());
          currentUser.setLastName(courseAuthor.getLastName());
        }
      }
      course.setAuthors(Collections.singletonList(currentUser));
    }

    String requestBody = new Gson().toJson(new StepicWrappers.CourseWrapper(course));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
      if (client == null) {
        LOG.warn("Http client is null");
        return;
      }
      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        final String message = FAILED_TITLE + "course ";
        LOG.error(message + responseString);
        showErrorNotification(project, FAILED_TITLE, responseString);
        return;
      }
      final RemoteCourse postedCourse = new Gson().fromJson(responseString, StepicWrappers.CoursesContainer.class).courses.get(0);
      postedCourse.setLessons(course.getLessons(true));
      postedCourse.setAuthors(course.getAuthors());
      postedCourse.setCourseMode(CCUtils.COURSE_MODE);
      postedCourse.setLanguage(course.getLanguageID());
      final int sectionId = postModule(postedCourse.getId(), 1, String.valueOf(postedCourse.getName()), project);
      int position = 1;
      for (Lesson lesson : course.getLessons()) {
        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2("Publishing lesson " + lesson.getIndex());
        }
        final int lessonId = postLesson(project, lesson);
        postUnit(lessonId, position, sectionId, project);
        if (indicator != null) {
          indicator.setFraction((double)lesson.getIndex()/course.getLessons().size());
          indicator.checkCanceled();
        }
        position += 1;
      }
      ApplicationManager.getApplication().runReadAction(() -> postAdditionalFiles(course, project, postedCourse.getId()));
      StudyTaskManager.getInstance(project).setCourse(postedCourse);
      showNotification(project, "Course published");
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static boolean checkIfAuthorized(@NotNull Project project, @NotNull String failedActionName) {
    boolean isAuthorized = StudySettings.getInstance().getUser() != null;
    if (!isAuthorized) {
      showStepicNotification(project, NotificationType.ERROR, failedActionName);
      return false;
    }
    return true;
  }

  private static void postAdditionalFiles(Course course, @NotNull final Project project, int id) {
    final Lesson lesson = CCUtils.createAdditionalLesson(course, project);
    if (lesson != null) {
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setText2("Publishing additional files");
      }
      final int sectionId = postModule(id, 2, EduNames.PYCHARM_ADDITIONAL, project);
      final int lessonId = postLesson(project, lesson);
      postUnit(lessonId, 1, sectionId, project);
    }
  }

  public static void postUnit(int lessonId, int position, int sectionId, Project project) {
    if (!checkIfAuthorized(project, "postTask")) return;

    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.UNITS);
    final StepicWrappers.UnitWrapper unitWrapper = new StepicWrappers.UnitWrapper();
    final StepicWrappers.Unit unit = new StepicWrappers.Unit();
    unit.setLesson(lessonId);
    unit.setPosition(position);
    unit.setSection(sectionId);
    unitWrapper.setUnit(unit);

    String requestBody = new Gson().toJson(unitWrapper);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
      if (client == null) return;
      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error(FAILED_TITLE + responseString);
        showErrorNotification(project, FAILED_TITLE, responseString);
      }
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static int postModule(int courseId, int position, @NotNull final String title, Project project) {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/sections");
    final StepicWrappers.Section section = new StepicWrappers.Section();
    section.setCourse(courseId);
    section.setTitle(title);
    section.setPosition(position);
    final StepicWrappers.SectionWrapper sectionContainer = new StepicWrappers.SectionWrapper();
    sectionContainer.setSection(section);
    String requestBody = new Gson().toJson(sectionContainer);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
      if (client == null) return -1;
      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error(FAILED_TITLE + responseString);
        showErrorNotification(project, FAILED_TITLE, responseString);
        return -1;
      }
      final StepicWrappers.Section
        postedSection = new Gson().fromJson(responseString, StepicWrappers.SectionContainer.class).getSections().get(0);
      return postedSection.getId();
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static int updateTask(@NotNull final Project project, @NotNull final Task task) {
    if (!checkIfAuthorized(project, "update task")) return -1;
    final Lesson lesson = task.getLesson();
    final int lessonId = lesson.getId();

    final HttpPut request = new HttpPut(EduStepicNames.STEPIC_API_URL + "/step-sources/" + String.valueOf(task.getStepId()));
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().
      registerTypeAdapter(AnswerPlaceholder.class, new StudySerializationUtils.Json.StepicAnswerPlaceholderAdapter()).create();
    ApplicationManager.getApplication().invokeLater(() -> {
      task.addTestsTexts("tests.py", task.getTestsText(project));
      final String requestBody = gson.toJson(new StepicWrappers.StepSourceWrapper(project, task, lessonId));
      request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

      try {
        final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
        if (client == null) return;
        final CloseableHttpResponse response = client.execute(request);
        final HttpEntity responseEntity = response.getEntity();
        final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
        EntityUtils.consume(responseEntity);
        final StatusLine line = response.getStatusLine();
        if (line.getStatusCode() != HttpStatus.SC_OK) {
          final String message = "Failed to update task ";
          LOG.error(message + responseString);
          showErrorNotification(project, message, responseString);
        }
        else {
          showNotification(project, "Task updated");
        }
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
    });
    return -1;
  }

  public static int updateLesson(@NotNull final Project project, @NotNull final Lesson lesson) {
    if(!checkIfAuthorized(project, "update lesson")) return -1;

    final HttpPut request = new HttpPut(EduStepicNames.STEPIC_API_URL + EduStepicNames.LESSONS + String.valueOf(lesson.getId()));

    String requestBody = new Gson().toJson(new StepicWrappers.LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
      if (client == null) return -1;
      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_OK) {
        final String message = "Failed to update lesson ";
        LOG.error(message + responseString);
        showErrorNotification(project, message, responseString);
        return -1;
      }
      else {
        showNotification(project, "Lesson updated");
      }

      final Lesson postedLesson = new Gson().fromJson(responseString, RemoteCourse.class).getLessons().get(0);
      for (Integer step : postedLesson.steps) {
        deleteTask(step, project);
      }

      for (Task task : lesson.getTaskList()) {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.checkCanceled();
        }
        postTask(project, task, lesson.getId());
      }
      return lesson.getId();
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  private static void showErrorNotification(@NotNull Project project, String message, String responseString) {
    final JsonObject details = new JsonParser().parse(responseString).getAsJsonObject();
    final String detailString = details.get("detail").getAsString();
    final Notification notification =
      new Notification("Push.course", message, detailString, NotificationType.ERROR);
    notification.notify(project);
  }

  private static void showNotification(@NotNull Project project, String message) {
    final Notification notification =
      new Notification("Push.course", message, message, NotificationType.INFORMATION);
    notification.notify(project);
  }

  private static void showStepicNotification(@NotNull Project project,
                                             @NotNull NotificationType notificationType, @NotNull String failedActionName) {
    String text = "Log in to Stepik to " + failedActionName;
    Notification notification = new Notification("Stepik", "Failed to " + failedActionName, text, notificationType);
    notification.addAction(new AnAction("Log in") {

      @Override
      public void actionPerformed(AnActionEvent e) {
        EduStepicConnector.doAuthorize(() -> showOAuthDialog());
        notification.expire();
      }
    });

    notification.notify(project);
  }

  public static int postLesson(@NotNull final Project project, @NotNull final Lesson lesson) {
    if (!checkIfAuthorized(project, "postLesson")) return -1;

    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/lessons");

    String requestBody = new Gson().toJson(new StepicWrappers.LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
      if (client == null) return -1;
      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        final String message = FAILED_TITLE + "lesson ";
        LOG.error(message + responseString);
        showErrorNotification(project, message, responseString);
        return 0;
      }
      final Lesson postedLesson = new Gson().fromJson(responseString, RemoteCourse.class).getLessons(true).get(0);
      lesson.setId(postedLesson.getId());
      for (Task task : lesson.getTaskList()) {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.checkCanceled();
        }
        postTask(project, task, postedLesson.getId());
      }
      return postedLesson.getId();
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static void deleteTask(@NotNull final Integer task, Project project) {
    final HttpDelete request = new HttpDelete(EduStepicNames.STEPIC_API_URL + EduStepicNames.STEP_SOURCES + task);
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
        if (client == null) return;
        final CloseableHttpResponse response = client.execute(request);
        final HttpEntity responseEntity = response.getEntity();
        final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
        EntityUtils.consume(responseEntity);
        final StatusLine line = response.getStatusLine();
        if (line.getStatusCode() != HttpStatus.SC_NO_CONTENT) {
          LOG.error("Failed to delete task " + responseString);
          showErrorNotification(project, "Failed to delete task ", responseString);
        }
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
    });
  }

  public static void postTask(final Project project, @NotNull final Task task, final int lessonId) {
    if (!checkIfAuthorized(project, "postTask")) return;

    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/step-sources");
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().
      registerTypeAdapter(AnswerPlaceholder.class, new StudySerializationUtils.Json.StepicAnswerPlaceholderAdapter()).create();
    ApplicationManager.getApplication().invokeLater(() -> {
      final String requestBody = gson.toJson(new StepicWrappers.StepSourceWrapper(project, task, lessonId));
      request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

      try {
        final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
        if (client == null) return;
        final CloseableHttpResponse response = client.execute(request);
        final StatusLine line = response.getStatusLine();
        final HttpEntity responseEntity = response.getEntity();
        final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
        EntityUtils.consume(responseEntity);
        if (line.getStatusCode() != HttpStatus.SC_CREATED) {
          final String message = FAILED_TITLE + "task ";
          LOG.error(message + responseString);
          showErrorNotification(project, message, responseString);
          return;
        }

        final JsonObject postedTask = new Gson().fromJson(responseString, JsonObject.class);
        final JsonObject stepSource = postedTask.getAsJsonArray("step-sources").get(0).getAsJsonObject();
        task.setStepId(stepSource.getAsJsonPrimitive("id").getAsInt());
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
    });
  }
}
