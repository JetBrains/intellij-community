package com.jetbrains.edu.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.net.ssl.CertificateManager;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

public class EduStepicConnector {
  private static final String stepicUrl = "https://stepic.org/";
  private static final String stepicApiUrl = stepicUrl + "api/";
  private static final Logger LOG = Logger.getInstance(EduStepicConnector.class.getName());
  private static String ourCSRFToken = "";
  private static CloseableHttpClient ourClient;

  //this prefix indicates that course can be opened by educational plugin
  public static final String PYCHARM_PREFIX = "pycharm";
  private static BasicCookieStore ourCookieStore;

  private EduStepicConnector() {
  }

  public static boolean login(@NotNull final String user, @NotNull final String password) {
    initializeClient();
    return postCredentials(user, password);
  }

  @Nullable
  public static AuthorWrapper getCurrentUser() {
    try {
      return getFromStepic("stepics/1", AuthorWrapper.class);
    }
    catch (IOException e) {
      LOG.warn("Couldn't get author info");
    }
    return null;
  }

  public static boolean createUser(@NotNull final String user, @NotNull final String password) {
    final HttpPost userRequest = new HttpPost(stepicApiUrl + "users");
    initializeClient();
    setHeaders(userRequest, "application/json");
    String requestBody = new Gson().toJson(new UserWrapper(user, password));
    userRequest.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(userRequest);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to create user " + responseString);
        return false;
      }
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return true;
  }

  private static void initializeClient() {
    final HttpGet request = new HttpGet(stepicUrl);
    request.addHeader(new BasicHeader("referer", "https://stepic.org"));
    request.addHeader(new BasicHeader("content-type", "application/json"));


    HttpClientBuilder builder = HttpClients.custom().setSslcontext(CertificateManager.getInstance().getSslContext()).setMaxConnPerRoute(100000).
      setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);
    ourCookieStore = new BasicCookieStore();

    try {
      // Create a trust manager that does not validate certificate for this connection
      TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() { return null; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
      }};
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, new SecureRandom());
      ourClient = builder.setDefaultCookieStore(ourCookieStore).setSslcontext(sslContext).build();

      ourClient.execute(request);
      saveCSRFToken();
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error(e.getMessage());
    }
    catch (KeyManagementException e) {
      LOG.error(e.getMessage());
    }
  }

  private static void saveCSRFToken() {
    if (ourCookieStore == null) return;
    final List<Cookie> cookies = ourCookieStore.getCookies();
    for (Cookie cookie : cookies) {
      if (cookie.getName().equals("csrftoken")) {
        ourCSRFToken = cookie.getValue();
      }
    }
  }

  private static boolean postCredentials(String user, String password) {
    String url = stepicUrl + "accounts/login/";
    final HttpPost request = new HttpPost(url);
    List <NameValuePair> nvps = new ArrayList <NameValuePair>();
    nvps.add(new BasicNameValuePair("csrfmiddlewaretoken", ourCSRFToken));
    nvps.add(new BasicNameValuePair("login", user));
    nvps.add(new BasicNameValuePair("next", "/"));
    nvps.add(new BasicNameValuePair("password", password));
    nvps.add(new BasicNameValuePair("remember", "on"));

    try {
      request.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e.getMessage());
      ourClient = null;
      return false;
    }

    setHeaders(request, "application/x-www-form-urlencoded");

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      saveCSRFToken();
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY) {
        final HttpEntity responseEntity = response.getEntity();
        final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
        LOG.error("Failed to login " + responseString);
        ourClient = null;
        return false;
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
      ourClient = null;
      return false;
    }
    return true;
  }

  private static <T> T getFromStepic(String link, final Class<T> container) throws IOException {
    final HttpGet request = new HttpGet(stepicApiUrl + link);
    if (ourClient == null) {
      initializeClient();
    }
    setHeaders(request, "application/json");

    final CloseableHttpResponse response = ourClient.execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepic returned non 200 status code " + responseString);
    }
    Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    return gson.fromJson(responseString, container);
  }

  @NotNull
  public static List<CourseInfo> getCourses() {
    try {
      List<CourseInfo> result = new ArrayList<CourseInfo>();
      int pageNumber = 1;
      while (addCoursesFromStepic(result, pageNumber)) {
        pageNumber += 1;
      }
      return result;
    }
    catch (IOException e) {
      LOG.error("Cannot load course list " + e.getMessage());
    }
    return Collections.emptyList();
  }

  private static boolean addCoursesFromStepic(List<CourseInfo> result, int pageNumber) throws IOException {
    final String url = pageNumber == 0 ? "courses" : "courses?page=" + String.valueOf(pageNumber);
    final CoursesContainer coursesContainer = getFromStepic(url, CoursesContainer.class);
    final List<CourseInfo> courseInfos = coursesContainer.courses;
    for (CourseInfo info : courseInfos) {
      final String courseType = info.getType();
      if (StringUtil.isEmptyOrSpaces(courseType)) continue;
      final List<String> typeLanguage = StringUtil.split(courseType, " ");
      if (typeLanguage.size() == 2 && PYCHARM_PREFIX.equals(typeLanguage.get(0))) {

        for (Integer instructor : info.instructors) {
          final CourseInfo.Author author = getFromStepic("users/" + String.valueOf(instructor), AuthorWrapper.class).users.get(0);
          info.addAuthor(author);
        }

        result.add(info);
      }
    }
    return coursesContainer.meta.containsKey("has_next") && coursesContainer.meta.get("has_next") == Boolean.TRUE;
  }

  public static Course getCourse(@NotNull final CourseInfo info) {
    final Course course = new Course();
    course.setAuthors(info.getAuthors());
    course.setDescription(info.getDescription());
    course.setName(info.getName());
    String courseType = info.getType();
    course.setLanguage(courseType.substring(PYCHARM_PREFIX.length() + 1));
    course.setUpToDate(true);  // TODO: get from stepic
    try {
      for (Integer section : info.sections) {
        course.addLessons(getLessons(section));
      }
      return course;
    }
    catch (IOException e) {
      LOG.error("IOException " + e.getMessage());
    }
    return null;
  }

  public static List<Lesson> getLessons(int sectionId) throws IOException {
    final SectionContainer sectionContainer = getFromStepic("sections/" + String.valueOf(sectionId), SectionContainer.class);
    List<Integer> unitIds = sectionContainer.sections.get(0).units;
    final List<Lesson> lessons = new ArrayList<Lesson>();
    for (Integer unitId : unitIds) {
      UnitContainer unit = getFromStepic("units/" + String.valueOf(unitId), UnitContainer.class);
      int lessonID = unit.units.get(0).lesson;
      LessonContainer lesson = getFromStepic("lessons/" + String.valueOf(lessonID), LessonContainer.class);
      Lesson realLesson = lesson.lessons.get(0);
      realLesson.taskList = new ArrayList<Task>();
      for (Integer s : realLesson.steps) {
        createTask(realLesson, s);
      }
      if (!realLesson.taskList.isEmpty())
        lessons.add(realLesson);
    }

    return lessons;
  }

  private static void createTask(Lesson lesson, Integer stepicId) throws IOException {
    final Step step = getStep(stepicId);
    if (!step.name.equals(PYCHARM_PREFIX)) return;
    final Task task = new Task();
    task.setStepicId(stepicId);
    task.setName(step.options != null ? step.options.title : PYCHARM_PREFIX);
    task.setText(step.text);
    for (TestFileWrapper wrapper : step.options.test) {
      task.addTestsTexts(wrapper.name, wrapper.text);
    }

    task.taskFiles = new HashMap<String, TaskFile>();      // TODO: it looks like we don't need taskFiles as map anymore
    if (step.options.files != null) {
      for (TaskFile taskFile : step.options.files) {
        task.taskFiles.put(taskFile.name, taskFile);
      }
    }
    lesson.taskList.add(task);
  }

  public static Step getStep(Integer step) throws IOException {
    return getFromStepic("steps/" + String.valueOf(step), StepContainer.class).steps.get(0).block;
  }


  public static boolean showLoginDialog() {
    final boolean[] logged = {false};
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        final LoginDialog dialog = new LoginDialog();
        dialog.show();
        logged[0] = dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE;
      }
    }, ModalityState.defaultModalityState());
    return logged[0];
  }

  public static void postAttempt(@NotNull final Task task, boolean passed, @Nullable String login, @Nullable String password) {
    if (task.getStepicId() <= 0) {
      return;
    }
    if (ourClient == null) {
      if (StringUtil.isEmptyOrSpaces(login) || StringUtil.isEmptyOrSpaces(password)) {
        return;
      }
      else {
        final boolean success = login(login, password);
        if (!success) return;
      }
    }

    final HttpPost attemptRequest = new HttpPost(stepicApiUrl + "attempts");
    setHeaders(attemptRequest, "application/json");
    String attemptRequestBody = new Gson().toJson(new AttemptWrapper(task.getStepicId()));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse attemptResponse = ourClient.execute(attemptRequest);
      final HttpEntity responseEntity = attemptResponse.getEntity();
      final String attemptResponseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = attemptResponse.getStatusLine();
      if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to make attempt " + attemptResponseString);
      }
      final AttemptWrapper.Attempt attempt = new Gson().fromJson(attemptResponseString, AttemptContainer.class).attempts.get(0);

      final Map<String, TaskFile> taskFiles = task.getTaskFiles();
      final ArrayList<SolutionFile> files = new ArrayList<SolutionFile>();
      for (TaskFile fileEntry : taskFiles.values()) {
        files.add(new SolutionFile(fileEntry.name, fileEntry.text));
      }
      postSubmission(passed, attempt, files);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static void postSubmission(boolean passed, AttemptWrapper.Attempt attempt, ArrayList<SolutionFile> files) throws IOException {
    final HttpPost request = new HttpPost(stepicApiUrl + "submissions");
    setHeaders(request, "application/json");

    String requestBody = new Gson().toJson(new SubmissionWrapper(attempt.id, passed ? "1" : "0", files));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
    final CloseableHttpResponse response = ourClient.execute(request);
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    final StatusLine line = response.getStatusLine();
    if (line.getStatusCode() != HttpStatus.SC_CREATED) {
      LOG.error("Failed to make submission " + responseString);
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

  private static void postCourse(final Project project, @NotNull Course course, boolean relogin, @NotNull final ProgressIndicator indicator) {
    indicator.setText("Uploading course to " + stepicUrl);
    final HttpPost request = new HttpPost(stepicApiUrl + "courses");
    if (ourClient == null || !relogin) {
      if (!login()) return;
    }
    final AuthorWrapper user = getCurrentUser();
    if (user != null) {
      course.setAuthors(user.users);
    }

    setHeaders(request, "application/json");
    String requestBody = new Gson().toJson(new CourseWrapper(course));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        if (!relogin) {
          login();
          postCourse(project, course, true, indicator);
        }
        LOG.error("Failed to push " + responseString);
        return;
      }
      final CourseInfo postedCourse = new Gson().fromJson(responseString, CoursesContainer.class).courses.get(0);

      final int sectionId = postModule(postedCourse.id, 1, String.valueOf(postedCourse.getName()));
      int position = 1;
      for (Lesson lesson : course.getLessons()) {
        indicator.checkCanceled();
        final int lessonId = postLesson(project, lesson, indicator);
        postUnit(lessonId, position, sectionId);
        position += 1;
      }
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          postAdditionalFiles(project, postedCourse.id, indicator);
        }
      });
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static boolean login() {
    final String login = StudySettings.getInstance().getLogin();
    if (StringUtil.isEmptyOrSpaces(login)) {
      return showLoginDialog();
    }
    else {
      boolean success = login(login, StudySettings.getInstance().getPassword());
      if (!success) {
        return showLoginDialog();
      }
    }
    return true;
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
    final HttpPost request = new HttpPost(stepicApiUrl + "units");
    setHeaders(request, "application/json");
    final UnitWrapper unitWrapper = new UnitWrapper();
    unitWrapper.unit = new Unit();
    unitWrapper.unit.lesson = lessonId;
    unitWrapper.unit.position = position;
    unitWrapper.unit.section = sectionId;

    String requestBody = new Gson().toJson(unitWrapper);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
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
    final HttpPost request = new HttpPost(stepicApiUrl + "sections");
    setHeaders(request, "application/json");
    final Section section = new Section();
    section.course = courseId;
    section.title = title;
    section.position = position;
    final SectionWrapper sectionContainer = new SectionWrapper();
    sectionContainer.section = section;
    String requestBody = new Gson().toJson(sectionContainer);
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to push " + responseString);
      }
      final Section postedSection = new Gson().fromJson(responseString, SectionContainer.class).sections.get(0);
      return postedSection.id;
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static int updateLesson(Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPut request = new HttpPut(stepicApiUrl + "lessons/" + String.valueOf(lesson.id));
    if (ourClient == null) {
      if (!login()) {
        LOG.error("Failed to push lesson");
        return 0;
      }
    }

    setHeaders(request, "application/json");
    String requestBody = new Gson().toJson(new LessonWrapper(lesson));
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
        postTask(project, task, lesson.id);
      }
      return lesson.id;
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static int postLesson(Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPost request = new HttpPost(stepicApiUrl + "lessons");
    if (ourClient == null) {
      login();
    }

    setHeaders(request, "application/json");
    String requestBody = new Gson().toJson(new LessonWrapper(lesson));
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
      lesson.id = postedLesson.id;
      for (Task task : lesson.getTaskList()) {
        indicator.checkCanceled();
        postTask(project, task, postedLesson.id);
      }
      return postedLesson.id;
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static void deleteTask(@NotNull final Integer task) {
    final HttpDelete request = new HttpDelete(stepicApiUrl + "step-sources/" + task);
    setHeaders(request, "application/json");
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          final CloseableHttpResponse response = ourClient.execute(request);
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
      }
    });
  }

  public static void postTask(final Project project, @NotNull final Task task, final int lessonId) {
    final HttpPost request = new HttpPost(stepicApiUrl + "step-sources");
    setHeaders(request, "application/json");
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final String requestBody = gson.toJson(new StepSourceWrapper(project, task, lessonId));
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        try {
          final CloseableHttpResponse response = ourClient.execute(request);
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
      }
    });
  }

  private static void setHeaders(@NotNull final HttpRequestBase request, String contentType) {
    request.addHeader(new BasicHeader("referer", stepicUrl));
    request.addHeader(new BasicHeader("X-CSRFToken", ourCSRFToken));
    request.addHeader(new BasicHeader("content-type", contentType));
  }

  private static class StepContainer {
    List<StepSource> steps;
  }

  private static class Step {
    @Expose StepOptions options;
    @Expose String text;
    @Expose String name = "pycharm";
    @Expose StepOptions source;

    public static Step fromTask(Project project, @NotNull final Task task) {
      final Step step = new Step();
      step.text = task.getTaskText(project);
      step.source = StepOptions.fromTask(project, task);
      return step;
    }
  }

  private static class StepOptions {
    @Expose List<TestFileWrapper> test;
    @Expose String title;
    @Expose List<TaskFile> files;
    @Expose String text;

    public static StepOptions fromTask(final Project project, @NotNull final Task task) {
      final StepOptions source = new StepOptions();
      setTests(task, source, project);
      source.files = new ArrayList<TaskFile>();
      source.title = task.getName();
      for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
        final TaskFile taskFile = new TaskFile();
        TaskFile.copy(entry.getValue(), taskFile);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            final VirtualFile taskDir = task.getTaskDir(project);
            assert taskDir != null;
            EduUtils.createStudentFileFromAnswer(project, taskDir, taskDir, entry.getKey(), taskFile);
          }
        });
        taskFile.name = entry.getKey();

        final VirtualFile taskDirectory = task.getTaskDir(project);
        if (taskDirectory == null) return null;
        final VirtualFile file = taskDirectory.findChild(taskFile.name);
        try {
          if (file != null) {
            if (EduUtils.isImage(taskFile.name)) {
              taskFile.text = Base64.encodeBase64URLSafeString(FileUtil.loadBytes(file.getInputStream()));
            }
            else {
              taskFile.text = FileUtil.loadTextAndClose(file.getInputStream());
            }
          }
        }
        catch (IOException e) {
          LOG.error("Can't find file " + file.getPath());
        }

        source.files.add(taskFile);
      }
      return source;
    }

    private static void setTests(@NotNull final Task task, @NotNull final StepOptions source, @NotNull final Project project) {
      final Map<String, String> testsText = task.getTestsText();
      if (testsText.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            source.test = Collections.singletonList(new TestFileWrapper(EduNames.TESTS_FILE, task.getTestsText(project)));
          }
        });
      }
      else {
        source.test = new ArrayList<TestFileWrapper>();
        for (Map.Entry<String, String> entry : testsText.entrySet()) {
          source.test.add(new TestFileWrapper(entry.getKey(), entry.getValue()));
        }
      }
    }
  }

  private static class CoursesContainer {
    public List<CourseInfo> courses;
    public Map meta;
  }

  static class StepSourceWrapper {
    @Expose
    StepSource stepSource;

    public StepSourceWrapper(Project project, Task task, int lessonId) {
      stepSource = new StepSource(project, task, lessonId);
    }
  }

  static class CourseWrapper {
    CourseInfo course;

    public CourseWrapper(Course course) {
      this.course = new CourseInfo();
      this.course.setName(course.getName());
      this.course.setDescription(course.getDescription());
      this.course.setAuthors(course.getAuthors());
    }
  }

  static class LessonWrapper {
    Lesson lesson;

    public LessonWrapper(Lesson lesson) {
      this.lesson = new Lesson();
      this.lesson.setName(lesson.getName());
      this.lesson.id = lesson.id;
      this.lesson.steps = new ArrayList<Integer>();
    }
  }

  static class LessonContainer {
    List<Lesson> lessons;
  }

  static class StepSource {
    @Expose Step block;
    @Expose int position = 0;
    @Expose int lesson = 0;

    public StepSource(Project project, Task task, int lesson) {
      this.lesson = lesson;
      position = task.getIndex();
      block = Step.fromTask(project, task);
    }
  }

  static class TestFileWrapper {
    @Expose private final String name;
    @Expose private final String text;

    public TestFileWrapper(String name, String text) {
      this.name = name;
      this.text = text;
    }
  }

  static class Section {
    List<Integer> units;
    int course;
    String title;
    int position;
    int id;
  }

  static class SectionWrapper {
    Section section;
  }

  static class SectionContainer {
    List<Section> sections;
    List<Lesson> lessons;

    List<Unit> units;
  }

  static class Unit {
    int id;
    int section;
    int lesson;
    int position;
  }

  static class UnitContainer {

    List<Unit> units;
  }

  static class UnitWrapper{
    Unit unit;
  }


  static class AttemptWrapper {
    static class Attempt {
      public Attempt(int step) {
        this.step = step;
      }

      int step;
      int id;
    }
    public AttemptWrapper(int step) {
      attempt = new Attempt(step);
    }

    Attempt attempt;
  }

  static class AttemptContainer {
    List<AttemptWrapper.Attempt> attempts;
  }

  static class SolutionFile {
    String name;
    String text;

    public SolutionFile(String name, String text) {
      this.name = name;
      this.text = text;
    }
  }

  static class AuthorWrapper {
    List<CourseInfo.Author> users;
  }

  static class SubmissionWrapper {
    Submission submission;


    public SubmissionWrapper(int attempt, String score, ArrayList<SolutionFile> files) {
      submission = new Submission(score, attempt, files);
    }

    static class Submission {
      int attempt;

      private final Reply reply;

      public Submission(String score, int attempt, ArrayList<SolutionFile> files) {
        reply = new Reply(files, score);
        this.attempt = attempt;
      }

      static class Reply {
        String score;
        List<SolutionFile> solution;

        public Reply(ArrayList<SolutionFile> files, String score) {
          this.score = score;
          solution = files;
        }
      }
    }

  }

  static class User {
    String first_name;
    String last_name;
    String email;
    String password;

    public User(String user, String password) {
      email = user;
      this.password = password;
      this.first_name = user;
      this.last_name = user;
    }
  }

  static class UserWrapper {
    User user;

    public UserWrapper(String user, String password) {
      this.user = new User(user, password);
    }
  }

}
