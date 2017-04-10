package com.jetbrains.edu.coursecreator.stepik;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.stepic.EduStepicAuthorizedClient;
import com.jetbrains.edu.learning.stepic.EduStepicNames;
import com.jetbrains.edu.learning.stepic.StepicUser;
import com.jetbrains.edu.learning.stepic.StepicWrappers;
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

public class CCStepicConnector {
  private static final Logger LOG = Logger.getInstance(CCStepicConnector.class.getName());

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
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText("Uploading course to " + EduStepicNames.STEPIC_URL);
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
        LOG.error("Failed to push " + responseString);
        return;
      }
      final RemoteCourse postedCourse = new Gson().fromJson(responseString, StepicWrappers.CoursesContainer.class).courses.get(0);
      postedCourse.setLessons(course.getLessons(true));
      postedCourse.setAuthors(course.getAuthors());
      postedCourse.setCourseMode(CCUtils.COURSE_MODE);
      postedCourse.setLanguage(course.getLanguageID());
      final int sectionId = postModule(postedCourse.getId(), 1, String.valueOf(postedCourse.getName()));
      int position = 1;
      for (Lesson lesson : course.getLessons()) {
        if (indicator != null) {
          indicator.checkCanceled();
        }
        final int lessonId = postLesson(project, lesson);
        postUnit(lessonId, position, sectionId);
        position += 1;
      }
      ApplicationManager.getApplication().runReadAction(() -> postAdditionalFiles(course, project, postedCourse.getId()));
      StudyTaskManager.getInstance(project).setCourse(postedCourse);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static void postAdditionalFiles(Course course, @NotNull final Project project, int id) {
    final Lesson lesson = CCUtils.createAdditionalLesson(course, project);
    if (lesson != null) {
      final int sectionId = postModule(id, 2, EduNames.PYCHARM_ADDITIONAL);
      final int lessonId = postLesson(project, lesson);
      postUnit(lessonId, 1, sectionId);
    }
  }

  public static void postUnit(int lessonId, int position, int sectionId) {
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
        LOG.error("Failed to push " + responseString);
      }
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static int postModule(int courseId, int position, @NotNull final String title) {
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
        LOG.error("Failed to push " + responseString);
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
    final Lesson lesson = task.getLesson();
    final int lessonId = lesson.getId();

    final HttpPut request = new HttpPut(EduStepicNames.STEPIC_API_URL + "/step-sources/" + String.valueOf(task.getStepId()));
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
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
          LOG.error("Failed to push " + responseString);
        }
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
    });
    return -1;
  }

  public static int updateLesson(@NotNull final Project project, @NotNull final Lesson lesson) {
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
        LOG.error("Failed to push " + responseString);
        return -1;
      }
      final Lesson postedLesson = new Gson().fromJson(responseString, RemoteCourse.class).getLessons().get(0);
      for (Integer step : postedLesson.steps) {
        deleteTask(step);
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

  public static int postLesson(@NotNull final Project project, @NotNull final Lesson lesson) {
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
        LOG.error("Failed to push " + responseString);
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

  public static void deleteTask(@NotNull final Integer task) {
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
        }
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
    });
  }

  public static void postTask(final Project project, @NotNull final Task task, final int lessonId) {
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
          LOG.error("Failed to push " + responseString);
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
