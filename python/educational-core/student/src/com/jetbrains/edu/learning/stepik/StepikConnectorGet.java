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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class StepikConnectorGet {
  private static final Logger LOG = Logger.getInstance(StepikConnectorGet.class.getName());
  private static final String stepikUrl = "https://stepik.org/";
  public static final String PYCHARM_PREFIX = "pycharm";
  public static final String CODE_PREFIX = "code";
  public static final String PYTHON27 = "python27";
  public static final String PYTHON3 = "python3";

  static final private Gson GSON =
    new GsonBuilder().registerTypeAdapter(TaskFile.class, new StudySerializationUtils.Json.StepikTaskFileAdapter())
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();


  static <T> T getFromStepik(String link, final Class<T> container) throws IOException {
    return getFromStepik(link, container, StepikConnectorLogin.getHttpClient());
  }

  static <T> T getFromStepikUnLogin(String link, final Class<T> container) throws IOException {
    return getFromStepik(link, container, StepikConnectorInit.getHttpClient());
  }

  private static <T> T getFromStepik(String link, final Class<T> container, CloseableHttpClient client) throws IOException {
    final HttpGet request = new HttpGet(EduStepikNames.STEPIK_API_URL + link);

    final CloseableHttpResponse response = client.execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepik returned non 200 status code " + responseString);
    }
    return GSON.fromJson(responseString, container);
    //    return getFromStepik(link, container, client, new ArrayList<NameValuePair>());
  }

  private static <T> T getFromStepik(String link, final Class<T> container, CloseableHttpClient client, List<NameValuePair> nvps)
    throws IOException {
    URI uri;
    try {
      uri = new URIBuilder(EduStepikNames.STEPIK_API_URL + link).addParameters(nvps).build();
    }
    catch (URISyntaxException e) {
      LOG.warn(e.getMessage());
      return null;
    }
    final HttpGet request = new HttpGet(uri);

    final CloseableHttpResponse response = client.execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepik returned non 200 status code " + responseString);
    }
    return GSON.fromJson(responseString, container);
  }

  @NotNull
  // un login
  public static List<CourseInfo> getCourses() {
    try {
      List<CourseInfo> result = new ArrayList<CourseInfo>();
      int pageNumber = 1;
      while (addCoursesFromStepik(result, pageNumber)) {
        pageNumber += 1;
      }
      return result;
    }
    catch (IOException e) {
      LOG.error("Cannot load course list " + e.getMessage());
    }
    return Collections.singletonList(CourseInfo.INVALID_COURSE);
  }

  public static List<CourseInfo> getEnrolledCourses() {
    try {
      List<CourseInfo> result = new ArrayList<CourseInfo>();
      int pageNumber = 1;
      List<NameValuePair> nvps = new ArrayList<>();
      nvps.add(new BasicNameValuePair("enrolled", "true"));
      while (addCoursesFromStepik(result, pageNumber, nvps)) {
        pageNumber += 1;
      }
      return result;
    }
    catch (IOException e) {
      LOG.error("Cannot load course list " + e.getMessage());
    }
    return Collections.singletonList(CourseInfo.INVALID_COURSE);
  }

  @NotNull
  public static CourseInfo getDefaultCourse() {
    CourseInfo courseInfo = null;
    try {
      courseInfo = getFromStepik(EduStepikNames.COURSES + "/217", StepikWrappers.CoursesContainer.class).courses.get(0);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return courseInfo;
  }

  private static boolean addCoursesFromStepik(List<CourseInfo> result, int pageNumber) throws IOException {
    final String url = pageNumber == 0 ? EduStepikNames.COURSES : EduStepikNames.COURSES_FROM_PAGE + String.valueOf(pageNumber);
    final StepikWrappers.CoursesContainer coursesContainer = getFromStepikUnLogin(url, StepikWrappers.CoursesContainer.class);
    final List<CourseInfo> courseInfos = coursesContainer.courses;
    for (CourseInfo info : courseInfos) {
      final String courseType = info.getType();
      if (!info.isAdaptive() && StringUtil.isEmptyOrSpaces(courseType)) continue;
      final List<String> typeLanguage = StringUtil.split(courseType, " ");
      // TODO: should adaptive course be of PyCharmType ?
      if (info.isAdaptive() || (typeLanguage.size() == 2 && PYCHARM_PREFIX.equals(typeLanguage.get(0)))) {
        for (Integer instructor : info.instructors) {
          final StepikUser author =
            getFromStepikUnLogin(EduStepikNames.USERS + "/" + String.valueOf(instructor), StepikWrappers.AuthorWrapper.class).users.get(0);
          info.addAuthor(author);
        }

        String name = info.getName().replaceAll("[^a-zA-Z0-9\\s]", "");
        info.setName(name.trim());

        result.add(info);
      }
    }
    return coursesContainer.meta.containsKey("has_next") && coursesContainer.meta.get("has_next") == Boolean.TRUE;
  }

  public static boolean addCoursesFromStepik(List<CourseInfo> result, int pageNumber, List<NameValuePair> nvps) throws IOException {
    final String url = pageNumber == 0 ? EduStepikNames.COURSES : EduStepikNames.COURSES_FROM_PAGE + String.valueOf(pageNumber);
    final StepikWrappers.CoursesContainer coursesContainer =
      getFromStepik(url, StepikWrappers.CoursesContainer.class, StepikConnectorLogin.getHttpClient(), nvps);
    final List<CourseInfo> courseInfos = coursesContainer.courses;
    for (CourseInfo info : courseInfos) {
      final String courseType = info.getType();
      //if (!info.isAdaptive() && StringUtil.isEmptyOrSpaces(courseType)) continue;
      final List<String> typeLanguage = StringUtil.split(courseType, " ");
      // TODO: should adaptive course be of PyCharmType ?
      //if (info.isAdaptive() || (typeLanguage.size() == 2 && PYCHARM_PREFIX.equals(typeLanguage.get(0)))) {
      for (Integer instructor : info.instructors) {
        final StepikUser author =
          getFromStepik(EduStepikNames.USERS + "/" + String.valueOf(instructor), StepikWrappers.AuthorWrapper.class,
                        StepikConnectorInit.getHttpClient()).users.get(0);
        info.addAuthor(author);
      }

      //      String name = info.getName().replaceAll("[^a-zA-Z0-9\\s]", "");
      //      info.setName(name.trim());

      result.add(info);
      //}
    }
    return coursesContainer.meta.containsKey("has_next") && coursesContainer.meta.get("has_next") == Boolean.TRUE;
  }

  public static Course getCourse(@NotNull final Project project, @NotNull final CourseInfo info) {
    Course course = new Course();
    course.setAuthors(info.getAuthors());
    course.setDescription(info.getDescription());
    course.setAdaptive(info.isAdaptive());
    course.setId(info.id);
    course.setUpToDate(true);  // TODO: get from stepik

    if (course.isAdaptive()) {
      course = getAdaptiveCourse(project, course, info);
    }
    else {
      course = getRegularCourse(project, course, info);
    }
    return course;
  }

  private static Course getRegularCourse(@NotNull final Project project, Course course, @NotNull final CourseInfo info) {
    String courseType = info.getType();
    course.setName(info.getName());
    course.setCourseType(info.getType().startsWith("pycharm") ? info.getType() : "stepik");

    //    what for?
    course.setLanguage(courseType.substring(PYCHARM_PREFIX.length() + 1));
    try {
      for (Integer section : info.sections) {
        switch (course.getCourseType()) {
          case ("stepik"):
            course.addLessons(getLessonsWithSectionNames(section));
            break;
          default:
            course.addLessons(getLessons(section));
        }
      }
      return course;
    }
    catch (IOException e) {
      LOG.error("IOException " + e.getMessage());
      return null;
    }
  }

  private static Course getAdaptiveCourse(@NotNull final Project project, Course course, @NotNull final CourseInfo info) {
    final Lesson lesson = new Lesson();
    course.setName(info.getName());
    //TODO: more specific name?
    lesson.setName("Adaptive");
    course.addLesson(lesson);
    final Task recommendation = EduAdaptiveStepikConnector.getNextRecommendation(project, course);
    if (recommendation != null) {
      lesson.addTask(recommendation);
      return course;
    }
    else {
      return null;
    }
  }

  public static List<Lesson> getLessons(int sectionId) throws IOException {
    final StepikWrappers.SectionContainer
      sectionContainer = getFromStepik(EduStepikNames.SECTIONS + String.valueOf(sectionId), StepikWrappers.SectionContainer.class);
    List<Integer> unitIds = sectionContainer.sections.get(0).units;

    StepikWrappers.UnitContainer
      unitContainer = getFromStepik(EduStepikNames.UNITS + "/" + getIdQuery(unitIds), StepikWrappers.UnitContainer.class);
    List<Integer> lessonsIds = new ArrayList<>();
    unitContainer.units.forEach(x -> lessonsIds.add(x.lesson));
    StepikWrappers.LessonContainer
      lessonContainer = getFromStepik(EduStepikNames.LESSONS + getIdQuery(lessonsIds), StepikWrappers.LessonContainer.class);

    final List<Lesson> lessons = new ArrayList<Lesson>();
    for (Lesson lesson : lessonContainer.lessons) {
      createTasks(lesson, lesson.steps);
      if (!lesson.taskList.isEmpty()) {
        lessons.add(lesson);
      }
    }

    return lessons;
  }

  public static List<Lesson> getLessonsWithSectionNames(int sectionId) throws IOException {
    final StepikWrappers.SectionContainer
      sectionContainer = getFromStepik(EduStepikNames.SECTIONS + String.valueOf(sectionId), StepikWrappers.SectionContainer.class);
    List<Integer> unitIds = sectionContainer.sections.get(0).units;
    sectionContainer.sections.forEach(x -> x.title = x.position + "." + x.title);

    StepikWrappers.UnitContainer
      unitContainer = getFromStepik(EduStepikNames.UNITS + "/" + getIdQuery(unitIds), StepikWrappers.UnitContainer.class);
    List<Integer> lessonsIds = new ArrayList<>();
    unitContainer.units.forEach(x -> lessonsIds.add(x.lesson));
    StepikWrappers.LessonContainer
      lessonContainer = getFromStepik(EduStepikNames.LESSONS + getIdQuery(lessonsIds), StepikWrappers.LessonContainer.class);

    final int[] i = {1};
    lessonContainer.lessons.forEach(x -> x.setName(i[0]++ + "." + x.getName()));

    String sectionName = sectionContainer.sections.get(0).title;
    final List<Lesson> lessons = new ArrayList<Lesson>();
    for (Lesson lesson : lessonContainer.lessons) {
      lesson.setName(sectionName + EduNames.SEPARATOR + lesson.getName());
      createTasks(lesson, lesson.steps);
      if (!lesson.taskList.isEmpty()) {
        lessons.add(lesson);
      }
    }
    return lessons;
  }

  private static void createTasks(Lesson lesson, List<Integer> stepikIds) throws IOException {
    final StepikWrappers.StepContainer stepContainer = getSteps(stepikIds);
    for (StepikWrappers.StepSource stepSource : stepContainer.steps) {
      if (supported(stepSource.block.name)) {
        final Task task = new Task();
        task.setStepikId(stepSource.id);
        task.setPosition(stepSource.position);

        switch (stepSource.block.name) {
          case (CODE_PREFIX):
            createCodeTask(task, stepSource.block);
            break;
          case (PYCHARM_PREFIX):
            createPyCharmTask(task, stepSource.block);
            break;
        }
        lesson.taskList.add(task);
      }
    }
  }

  private static boolean supported(String name) {
    return CODE_PREFIX.equals(name) || PYCHARM_PREFIX.equals(name);
  }

  private static void createPyCharmTask(Task task, StepikWrappers.Step step) {
    task.setName(step.options != null ? step.options.title : PYCHARM_PREFIX);
    task.setText(step.text);
    for (StepikWrappers.TestFileWrapper wrapper : step.options.test) {
      task.addTestsTexts(wrapper.name, wrapper.text);
    }

    task.taskFiles = new HashMap<String, TaskFile>();      // TODO: it looks like we don't need taskFiles as map anymore
    if (step.options.files != null) {
      for (TaskFile taskFile : step.options.files) {
        task.taskFiles.put(taskFile.name, taskFile);
      }
    }
  }

  private static void createCodeTask(Task task, StepikWrappers.Step step) {
    task.setName("step" + task.getPosition());
    if (step.options.samples != null) {
      final StringBuilder builder = new StringBuilder();
      for (List<String> sample : step.options.samples) {
        if (sample.size() == 2) {
          builder.append("<b>Sample Input:</b><br>");
          builder.append(StringUtil.replace(sample.get(0), "\n", "<br>"));
          builder.append("<br>");
          builder.append("<b>Sample Output:</b><br>");
          builder.append(StringUtil.replace(sample.get(1), "\n", "<br>"));
          builder.append("<br><br>");
        }
      }
      task.setText(step.text + "<br>" + builder.toString());
    }

    if (step.options.executionMemoryLimit != null && step.options.executionTimeLimit != null) {
      String builder = "<b>Memory limit</b>: " +
                       step.options.executionMemoryLimit + " Mb" +
                       "<br>" +
                       "<b>Time limit</b>: " +
                       step.options.executionTimeLimit + "s" +
                       "<br><br>";
      task.setText(task.getText() + builder);
    }

    String templateForTask;
    templateForTask = step.options.codeTemplates.getTemplateForLanguage("java8");
    if (templateForTask != null) {
      final TaskFile taskFile = new TaskFile();
      taskFile.name = "Main.java";
      taskFile.text = templateForTask;
      task.taskFiles.put(taskFile.name, taskFile);
    }

    templateForTask = step.options.codeTemplates.getTemplateForLanguage("python3");
    if (templateForTask != null) {
      final TaskFile taskFile = new TaskFile();
      taskFile.name = "main.py";
      taskFile.text = templateForTask;
      task.taskFiles.put(taskFile.name, taskFile);
    }
  }

  public static StepikWrappers.StepContainer getSteps(List<Integer> steps) throws IOException {
    return getFromStepik(EduStepikNames.STEPS + "/" + getIdQuery(steps), StepikWrappers.StepContainer.class);
  }

  public static StepikWrappers.Step getStep(Integer step) throws IOException {
    return getFromStepik(EduStepikNames.STEPS + "/" + String.valueOf(step), StepikWrappers.StepContainer.class).steps.get(0).block;
  }

  public static StepikWrappers.AuthorWrapper getCurrentUser() {
    try {
      return getFromStepik(EduStepikNames.CURRENT_USER, StepikWrappers.AuthorWrapper.class);
    }
    catch (IOException e) {
      LOG.warn("Couldn't get author info");
    }
    return null;
  }

  public static StepikWrappers.ResultSubmissionWrapper getStatus(int submissionID) {
    final String url = EduStepikNames.SUBMISSIONS + "/" + submissionID;
    try {
      return getFromStepik(url, StepikWrappers.ResultSubmissionWrapper.class);
    }
    catch (IOException e) {
      LOG.warn("Couldn't get Submission");
      return null;
    }
  }

  private static String getIdQuery(List<Integer> list) {
    StringBuilder sb = new StringBuilder();
    sb.append("?");
    for (Integer id : list) {
      sb.append("ids[]=" + id + "&");
    }
    return sb.toString();
  }

  @NotNull
  public static List<Integer> getEnrolledCoursesIds() {
    try {
      final URI enrolledCoursesUri = new URIBuilder(EduStepikNames.COURSES).addParameter("enrolled", "true").build();
      final List<CourseInfo> courses = getFromStepik(enrolledCoursesUri.toString(), StepikWrappers.CoursesContainer.class).courses;
      final ArrayList<Integer> ids = new ArrayList<>();
      for (CourseInfo course : courses) {
        ids.add(course.getId());
      }
      return ids;
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    catch (URISyntaxException e) {
      LOG.warn(e.getMessage());
    }
    return Collections.emptyList();
  }

  public static StepikWrappers.SubmissionContainer getSubmissions(List<NameValuePair> nvps) {
    try {
      return getFromStepik(EduStepikNames.SUBMISSIONS, StepikWrappers.SubmissionContainer.class, StepikConnectorLogin.getHttpClient(),
                           nvps);
    }
    catch (IOException e) {
      LOG.warn("Can't get submissions\n" + e.getMessage());
      return null;
    }
  }

  public static StepikWrappers.UnitContainer getUnits(String unitId) {
    try {
      return getFromStepik(EduStepikNames.UNITS + "/" + unitId, StepikWrappers.UnitContainer.class, StepikConnectorLogin.getHttpClient());
    }
    catch (IOException e) {
      LOG.warn("Can't get Units\n" + e.getMessage());
      return null;
    }
  }

  public static StepikWrappers.SectionContainer getSections(String sectionId) {
    try {
      return getFromStepik(EduStepikNames.SECTIONS + sectionId, StepikWrappers.SectionContainer.class,
                           StepikConnectorLogin.getHttpClient());
    }
    catch (IOException e) {
      LOG.warn("Can't get Sections\n" + e.getMessage());
      return null;
    }
  }

  public static StepikWrappers.CoursesContainer getCourseInfos(String courseId) {
    try {
      return getFromStepik(EduStepikNames.COURSES + "/" + courseId, StepikWrappers.CoursesContainer.class,
                           StepikConnectorLogin.getHttpClient());
    }
    catch (IOException e) {
      LOG.warn("Can't get courses Info\n" + e.getMessage());
      return null;
    }
  }
}
