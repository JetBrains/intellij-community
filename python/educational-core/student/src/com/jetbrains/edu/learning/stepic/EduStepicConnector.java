package com.jetbrains.edu.learning.stepic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class EduStepicConnector {
  private static final Logger LOG = Logger.getInstance(EduStepicConnector.class.getName());

  //this prefix indicates that course can be opened by educational plugin
  public static final String PYCHARM_PREFIX = "pycharm";
  private static final String ADAPTIVE_NOTE =
    "\n\nInitially, the adaptive system may behave somewhat randomly, but the more problems you solve, the smarter it become!";

  private EduStepicConnector() {
  }

  public static boolean enrollToCourse(final int courseId) {
    HttpPost post = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ENROLLMENTS);
    try {
      final StepicWrappers.EnrollmentWrapper enrollment = new StepicWrappers.EnrollmentWrapper(String.valueOf(courseId));
      post.setEntity(new StringEntity(new GsonBuilder().create().toJson(enrollment)));
      final CloseableHttpClient client = EduStepicClient.getHttpClient();
      CloseableHttpResponse response = client.execute(post);
      StatusLine line = response.getStatusLine();
      return line.getStatusCode() == HttpStatus.SC_CREATED;
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    return false;
  }

  @NotNull
  public static List<CourseInfo> getCourses() {
    try {
      List<CourseInfo> result = new ArrayList<>();
      int pageNumber = 1;
      while (addCoursesFromStepic(result, pageNumber)) {
        pageNumber += 1;
      }
      return result;
    }
    catch (IOException e) {
      LOG.error("Cannot load course list " + e.getMessage());
    }
    return Collections.singletonList(CourseInfo.INVALID_COURSE);
  }

  public static CourseInfo getCourseInfo(Project project, String courseId) {
    final String url = EduStepicNames.COURSES + "/" + courseId;
    try {
      final StepicWrappers.CoursesContainer coursesContainer =
        EduStepicAuthorizedClient.getFromStepic(url, StepicWrappers.CoursesContainer.class, project);
      return coursesContainer.courses.get(0);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return null;
  }

  public static Date getCourseUpdateDate(final int courseId) {
    final String url = EduStepicNames.COURSES + "/" + courseId;
    try {
      final List<CourseInfo> courses = EduStepicClient.getFromStepic(url, StepicWrappers.CoursesContainer.class).courses;
      if (!courses.isEmpty()) {
        return courses.get(0).getUpdateDate();
      }
    }
    catch (IOException e) {
      LOG.warn("Could not retrieve course with id=" + courseId);
    }

    return null;
  }

  public static Date getLessonUpdateDate(final int lessonId) {
    final String url = EduStepicNames.LESSONS + "/" + lessonId;
    try {
      List<Lesson> lessons = EduStepicClient.getFromStepic(url, StepicWrappers.LessonContainer.class).lessons;
      if (!lessons.isEmpty()) {
        return lessons.get(0).getUpdateDate();
      }
    }
    catch (IOException e) {
      LOG.warn("Could not retrieve course with id=" + lessonId);
    }

    return null;
  }

  public static Date getTaskUpdateDate(final int taskId) {
    final String url = EduStepicNames.STEPS + "/" + String.valueOf(taskId);
    try {
      List<StepicWrappers.StepSource> steps = EduStepicClient.getFromStepic(url, StepicWrappers.StepContainer.class).steps;
      if (!steps.isEmpty()) {
        return steps.get(0).update_date;
      }
    }
    catch (IOException e) {
      LOG.warn("Could not retrieve course with id=" + taskId);
    }

    return null;
  }

  private static boolean addCoursesFromStepic(List<CourseInfo> result, int pageNumber) throws IOException {
    final URI url;
    try {
      url = new URIBuilder(EduStepicNames.COURSES).addParameter("is_idea_compatible", "true").
          addParameter("page", String.valueOf(pageNumber)).build();
    }
    catch (URISyntaxException e) {
      LOG.error(e.getMessage());
      return false;
    }
    final StepicWrappers.CoursesContainer coursesContainer = EduStepicClient.getFromStepic(url.toString(), StepicWrappers.CoursesContainer.class);
    final List<CourseInfo> courseInfos = coursesContainer.courses;
    for (CourseInfo info : courseInfos) {
      final String courseType = info.getType();
      if (!info.isAdaptive() && StringUtil.isEmptyOrSpaces(courseType)) continue;
      final List<String> typeLanguage = StringUtil.split(courseType, " ");
      if (info.isAdaptive() || (typeLanguage.size() == 2 && PYCHARM_PREFIX.equals(typeLanguage.get(0)))) {
        for (Integer instructor : info.instructors) {
          final StepicUser author = EduStepicClient.getFromStepic(EduStepicNames.USERS + "/" + String.valueOf(instructor),
                                                  StepicWrappers.AuthorWrapper.class).users.get(0);
          info.addAuthor(author);
        }
        
        if (info.isAdaptive()) {
          info.setDescription("This is a Stepik Adaptive course.\n\n" + info.getDescription() + ADAPTIVE_NOTE);
        }
        
        result.add(info);
      }
    }
    return coursesContainer.meta.containsKey("has_next") && coursesContainer.meta.get("has_next") == Boolean.TRUE;
  }

  public static Course getCourse(@NotNull final Project project, @NotNull final CourseInfo info) {
    final Course course = new Course();
    course.setAuthors(info.getAuthors());
    course.setDescription(info.getDescription());
    course.setAdaptive(info.isAdaptive());
    course.setId(info.getId());
    course.setUpdateDate(getCourseUpdateDate(info.getId()));
    
    if (!course.isAdaptive()) {
      String courseType = info.getType();
      course.setName(info.getName());
      course.setLanguage(courseType.substring(PYCHARM_PREFIX.length() + 1));
      try {
        for (Integer section : info.sections) {
          course.addLessons(getLessons(section));
        }
        return course;
      }
      catch (IOException e) {
        LOG.error("IOException " + e.getMessage());
      }
    }
    else {
      final Lesson lesson = new Lesson();
      course.setName(info.getName());
      //TODO: more specific name?
      lesson.setName("Adaptive");
      course.addLesson(lesson);
      final Task recommendation = EduAdaptiveStepicConnector.getNextRecommendation(project, course);
      if (recommendation != null) {
        lesson.addTask(recommendation);
        return course;
      }
      else {
        return null;
      }
    }
    return null;
  }

  public static List<Lesson> getLessons(int sectionId) throws IOException {
    final StepicWrappers.SectionContainer
      sectionContainer = EduStepicClient.getFromStepic(EduStepicNames.SECTIONS + String.valueOf(sectionId), StepicWrappers.SectionContainer.class);
    List<Integer> unitIds = sectionContainer.sections.get(0).units;
    final List<Lesson> lessons = new ArrayList<>();
    for (Integer unitId : unitIds) {
      StepicWrappers.UnitContainer
        unit = EduStepicClient.getFromStepic(EduStepicNames.UNITS + "/" + String.valueOf(unitId), StepicWrappers.UnitContainer.class);
      int lessonID = unit.units.get(0).lesson;
      StepicWrappers.LessonContainer
        lessonContainer = EduStepicClient.getFromStepic(EduStepicNames.LESSONS + String.valueOf(lessonID), StepicWrappers.LessonContainer.class);
      Lesson lesson = lessonContainer.lessons.get(0);
      lesson.taskList = new ArrayList<>();
      for (Integer s : lesson.steps) {
        createTask(lesson, s);
      }
      if (!lesson.taskList.isEmpty())
        lessons.add(lesson);
    }

    return lessons;
  }

  private static void createTask(Lesson lesson, Integer stepicId) throws IOException {
    final StepicWrappers.StepSource step = getStep(stepicId);
    final StepicWrappers.Step block = step.block;
    if (!block.name.equals(PYCHARM_PREFIX)) return;
    final Task task = new Task();
    task.setStepicId(stepicId);
    task.setUpdateDate(step.update_date);
    task.setName(block.options != null ? block.options.title : PYCHARM_PREFIX);
    task.setText(block.text);
    for (StepicWrappers.TestFileWrapper wrapper : block.options.test) {
      task.addTestsTexts(wrapper.name, wrapper.text);
    }

    task.taskFiles = new HashMap<>();      // TODO: it looks like we don't need taskFiles as map anymore
    if (block.options.files != null) {
      for (TaskFile taskFile : block.options.files) {
        task.taskFiles.put(taskFile.name, taskFile);
      }
    }
    lesson.taskList.add(task);
  }

  public static StepicWrappers.StepSource getStep(Integer step) throws IOException {
    return EduStepicClient.getFromStepic(EduStepicNames.STEPS + "/" + String.valueOf(step), StepicWrappers.StepContainer.class).steps.get(0);
  }

  public static void postAttempt(@NotNull final Task task, boolean passed, @NotNull final Project project) {
    if (task.getStepicId() <= 0) {
      return;
    }

    final HttpPost attemptRequest = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ATTEMPTS);
    String attemptRequestBody = new Gson().toJson(new StepicWrappers.AttemptWrapper(task.getStepicId()));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
      final CloseableHttpResponse attemptResponse = client.execute(attemptRequest);
      final HttpEntity responseEntity = attemptResponse.getEntity();
      final String attemptResponseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = attemptResponse.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.warn("Failed to make attempt " + attemptResponseString);
      }
      final StepicWrappers.AttemptWrapper.Attempt attempt = new Gson().fromJson(attemptResponseString, StepicWrappers.AttemptContainer.class).attempts.get(0);

      final Map<String, TaskFile> taskFiles = task.getTaskFiles();
      final ArrayList<StepicWrappers.SolutionFile> files = new ArrayList<>();
      for (TaskFile fileEntry : taskFiles.values()) {
        files.add(new StepicWrappers.SolutionFile(fileEntry.name, fileEntry.text));
      }
      postSubmission(passed, attempt, project, files);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static void postSubmission(boolean passed, StepicWrappers.AttemptWrapper.Attempt attempt,
                                     Project project, ArrayList<StepicWrappers.SolutionFile> files) throws IOException {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.SUBMISSIONS);

    String requestBody = new Gson().toJson(new StepicWrappers.SubmissionWrapper(attempt.id, passed ? "1" : "0", files));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
    final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
    final CloseableHttpResponse response = client.execute(request);
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    final StatusLine line = response.getStatusLine();
    EntityUtils.consume(responseEntity);
    if (line.getStatusCode() != HttpStatus.SC_CREATED) {
      LOG.error("Failed to make submission " + responseString);
    }
  }

  public static void postCourseWithProgress(final Project project, @NotNull final Course course) {
    ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Modal(project, "Uploading Course", true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        postCourse(project, course, indicator);
      }
    });
  }

  private static void postCourse(final Project project, @NotNull Course course, @NotNull final ProgressIndicator indicator) {
    indicator.setText("Uploading course to " + EduStepicNames.STEPIC_URL);
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
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to push " + responseString);
        return;
      }
      final CourseInfo postedCourse = new Gson().fromJson(responseString, StepicWrappers.CoursesContainer.class).courses.get(0);
      course.setId(postedCourse.id);
      final int sectionId = postModule(project, postedCourse.id, 1, String.valueOf(postedCourse.getName()));
      int position = 1;
      for (Lesson lesson : course.getLessons()) {
        indicator.checkCanceled();
        final int lessonId = postLesson(project, lesson, indicator);
        postUnit(project, lessonId, position, sectionId);
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
          !"pyc".equals(file.getExtension()) && !file.isDirectory() && !name.equals(EduNames.TEST_HELPER) && !name.startsWith(".");
      }
    });

    if (!files.isEmpty()) {
      final int sectionId = postModule(project, id, 2, EduNames.PYCHARM_ADDITIONAL);
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
      postUnit(project, lessonId, 1, sectionId);
    }
  }

  public static void postUnit(@NotNull final Project project, int lessonId, int position, int sectionId) {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.UNITS);
    final StepicWrappers.UnitWrapper unitWrapper = new StepicWrappers.UnitWrapper();
    unitWrapper.unit = new StepicWrappers.Unit();
    unitWrapper.unit.lesson = lessonId;
    unitWrapper.unit.position = position;
    unitWrapper.unit.section = sectionId;

    String requestBody = new Gson().toJson(unitWrapper);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
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

  private static int postModule(@NotNull final Project project, int courseId, int position, @NotNull final String title) {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/sections");
    final StepicWrappers.Section section = new StepicWrappers.Section();
    section.course = courseId;
    section.title = title;
    section.position = position;
    final StepicWrappers.SectionWrapper sectionContainer = new StepicWrappers.SectionWrapper();
    sectionContainer.section = section;
    String requestBody = new Gson().toJson(sectionContainer);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
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
        postedSection = new Gson().fromJson(responseString, StepicWrappers.SectionContainer.class).sections.get(0);
      return postedSection.id;
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static int updateTask(@NotNull final Project project, @NotNull final Task task) {
    final Lesson lesson = task.getLesson();
    final int lessonId = lesson.getId();

    final HttpPut request = new HttpPut(EduStepicNames.STEPIC_API_URL + "/step-sources/" + String.valueOf(task.getStepicId()));
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().
      registerTypeAdapter(AnswerPlaceholder.class, new StudySerializationUtils.Json.StepicAnswerPlaceholderAdapter()).create();
    ApplicationManager.getApplication().invokeLater(() -> {
      task.addTestsTexts("tests.py", task.getTestsText(project));
      final String requestBody = gson.toJson(new StepicWrappers.StepSourceWrapper(project, task, lessonId));
      request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

      try {
        final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
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

  public static int updateLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPut request = new HttpPut(EduStepicNames.STEPIC_API_URL + EduStepicNames.LESSONS + String.valueOf(lesson.getId()));

    String requestBody = new Gson().toJson(new StepicWrappers.LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_OK) {
        LOG.error("Failed to push " + responseString);
        return -1;
      }
      final Lesson postedLesson = new Gson().fromJson(responseString, Course.class).getLessons().get(0);
      for (Integer step : postedLesson.steps) {
        deleteTask(step, project);
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

  public static int postLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/lessons");

    String requestBody = new Gson().toJson(new StepicWrappers.LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
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

  public static void deleteTask(@NotNull final Integer task, Project project) {
    final HttpDelete request = new HttpDelete(EduStepicNames.STEPIC_API_URL + EduStepicNames.STEP_SOURCES + task);
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
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
    //TODO: register type adapter for task files here?
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().
      registerTypeAdapter(AnswerPlaceholder.class, new StudySerializationUtils.Json.StepicAnswerPlaceholderAdapter()).create();
    ApplicationManager.getApplication().invokeLater(() -> {
      final String requestBody = gson.toJson(new StepicWrappers.StepSourceWrapper(project, task, lessonId));
      request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

      try {
        final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(project);
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
        task.setStepicId(stepSource.getAsJsonPrimitive("id").getAsInt());
      }
      catch (IOException e) {
        LOG.error(e.getMessage());
      }
    });
  }
}
