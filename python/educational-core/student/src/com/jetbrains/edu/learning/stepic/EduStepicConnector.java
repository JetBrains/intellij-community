package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
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
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

public class EduStepicConnector {
  private static final Logger LOG = Logger.getInstance(EduStepicConnector.class.getName());
  private static final String stepicUrl = "https://stepic.org/";
  private static String ourCSRFToken = "";
  private static CloseableHttpClient ourClient;

  //this prefix indicates that course can be opened by educational plugin
  public static final String PYCHARM_PREFIX = "pycharm";
  private static BasicCookieStore ourCookieStore;

  private EduStepicConnector() {
  }

  public static StepicUser login(@NotNull final String username, @NotNull final String password) {
    initializeClient();
    if (postCredentials(username, password)) {
      final StepicWrappers.AuthorWrapper stepicUserWrapper = getCurrentUser();
      if (stepicUserWrapper != null && stepicUserWrapper.users.size() == 1) {
        return stepicUserWrapper.users.get(0);
      }
    }
    return null;
  }
  
  @NotNull
  public static List<Integer> getEnrolledCoursesIds() {
    try {
      final URI enrolledCoursesUri = new URIBuilder(EduStepicNames.COURSES).addParameter("enrolled", "true").build();
      final List<CourseInfo> courses = getFromStepic(enrolledCoursesUri.toString(), StepicWrappers.CoursesContainer.class).courses;
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

  @Nullable
  public static StepicWrappers.AuthorWrapper getCurrentUser() {
    try {
      return getFromStepic(EduStepicNames.CURRENT_USER, StepicWrappers.AuthorWrapper.class);
    }
    catch (IOException e) {
      LOG.warn("Couldn't get author info");
    }
    return null;
  }

  public static boolean createUser(@NotNull final String user, @NotNull final String password) {
    final HttpPost userRequest = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.USERS);
    initializeClient();
    setHeaders(userRequest, "application/json");
    String requestBody = new Gson().toJson(new StepicWrappers.UserWrapper(user, password));
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

  public static void initializeClient() {
    if (ourClient == null) {
      final HttpGet request = new HttpGet(EduStepicNames.STEPIC_URL);
      request.addHeader(new BasicHeader("referer", EduStepicNames.STEPIC_URL));
      request.addHeader(new BasicHeader("content-type", EduStepicNames.CONTENT_TYPE_APPL_JSON));


      HttpClientBuilder builder =
        HttpClients.custom().setSslcontext(CertificateManager.getInstance().getSslContext()).setMaxConnPerRoute(100000).
          setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);
      ourCookieStore = new BasicCookieStore();

      try {
        // Create a trust manager that does not validate certificate for this connection
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
          public X509Certificate[] getAcceptedIssuers() {
            return null;
          }

          public void checkClientTrusted(X509Certificate[] certs, String authType) {
          }

          public void checkServerTrusted(X509Certificate[] certs, String authType) {
          }
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
    String url = EduStepicNames.STEPIC_URL + EduStepicNames.LOGIN;
    final HttpPost request = new HttpPost(url);
    List <NameValuePair> nvps = new ArrayList <NameValuePair>();
    nvps.add(new BasicNameValuePair("csrfmiddlewaretoken", ourCSRFToken));
    nvps.add(new BasicNameValuePair("login", user));
    nvps.add(new BasicNameValuePair("next", "/"));
    nvps.add(new BasicNameValuePair("password", password));
    nvps.add(new BasicNameValuePair("remember", "on"));

    request.setEntity(new UrlEncodedFormEntity(nvps, Consts.UTF_8));

    setHeaders(request, "application/x-www-form-urlencoded");

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      saveCSRFToken();
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY) {
        final HttpEntity responseEntity = response.getEntity();
        final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
        LOG.warn("Failed to login: " + line.getStatusCode() + line.getReasonPhrase());
        LOG.debug("Failed to login " + responseString);
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

  static <T> T getFromStepic(String link, final Class<T> container) throws IOException {
    final HttpGet request = new HttpGet(EduStepicNames.STEPIC_API_URL + link);
    if (ourClient == null) {
      initializeClient();
    }
    setHeaders(request, EduStepicNames.CONTENT_TYPE_APPL_JSON);

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
  public static CloseableHttpClient getHttpClient(@NotNull final Project project) {
    if (ourClient == null) {
      login(project);
      initializeClient();
    }
    return ourClient;
  }

  public static boolean enrollToCourse(final int courseId) {
    HttpPost post = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ENROLLMENTS);
    try {
      final StepicWrappers.EnrollmentWrapper enrollment = new StepicWrappers.EnrollmentWrapper(String.valueOf(courseId));
      post.setEntity(new StringEntity(new GsonBuilder().create().toJson(enrollment)));
      setHeaders(post, EduStepicNames.CONTENT_TYPE_APPL_JSON);
      if (ourClient == null) {
        initializeClient();
      }
      CloseableHttpResponse response = ourClient.execute(post);
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
    return Collections.singletonList(CourseInfo.INVALID_COURSE);
  }

  private static boolean addCoursesFromStepic(List<CourseInfo> result, int pageNumber) throws IOException {
    final String url = pageNumber == 0 ? EduStepicNames.COURSES : EduStepicNames.COURSES_FROM_PAGE + String.valueOf(pageNumber);
    final StepicWrappers.CoursesContainer coursesContainer = getFromStepic(url, StepicWrappers.CoursesContainer.class);
    final List<CourseInfo> courseInfos = coursesContainer.courses;
    for (CourseInfo info : courseInfos) {
      final String courseType = info.getType();
      if (!info.isAdaptive() && StringUtil.isEmptyOrSpaces(courseType)) continue;
      final List<String> typeLanguage = StringUtil.split(courseType, " ");
      // TODO: should adaptive course be of PyCharmType ?
      if (info.isAdaptive() || (typeLanguage.size() == 2 && PYCHARM_PREFIX.equals(typeLanguage.get(0)))) {
        for (Integer instructor : info.instructors) {
          final StepicUser author = getFromStepic(EduStepicNames.USERS + "/" + String.valueOf(instructor), StepicWrappers.AuthorWrapper.class).users.get(0);
          info.addAuthor(author);
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
    course.setId(info.id);
    course.setUpToDate(true);  // TODO: get from stepic
    
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
      }

      return course;
    }
    return null;
  }

  public static List<Lesson> getLessons(int sectionId) throws IOException {
    final StepicWrappers.SectionContainer
      sectionContainer = getFromStepic(EduStepicNames.SECTIONS + String.valueOf(sectionId), StepicWrappers.SectionContainer.class);
    List<Integer> unitIds = sectionContainer.sections.get(0).units;
    final List<Lesson> lessons = new ArrayList<Lesson>();
    for (Integer unitId : unitIds) {
      StepicWrappers.UnitContainer
        unit = getFromStepic(EduStepicNames.UNITS + "/" + String.valueOf(unitId), StepicWrappers.UnitContainer.class);
      int lessonID = unit.units.get(0).lesson;
      StepicWrappers.LessonContainer
        lesson = getFromStepic(EduStepicNames.LESSONS + String.valueOf(lessonID), StepicWrappers.LessonContainer.class);
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
    final StepicWrappers.Step step = getStep(stepicId);
    if (!step.name.equals(PYCHARM_PREFIX)) return;
    final Task task = new Task();
    task.setStepicId(stepicId);
    task.setName(step.options != null ? step.options.title : PYCHARM_PREFIX);
    task.setText(step.text);
    for (StepicWrappers.TestFileWrapper wrapper : step.options.test) {
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

  public static StepicWrappers.Step getStep(Integer step) throws IOException {
    return getFromStepic(EduStepicNames.STEPS + "/" + String.valueOf(step), StepicWrappers.StepContainer.class).steps.get(0).block;
  }


  public static boolean showLoginDialog() {
    final boolean[] logged = {false};
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final LoginDialog dialog = new LoginDialog();
      dialog.show();
      logged[0] = dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE;
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
        if (login(login, password) == null) return;
      }
    }

    final HttpPost attemptRequest = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ATTEMPTS);
    setHeaders(attemptRequest, "application/json");
    String attemptRequestBody = new Gson().toJson(new StepicWrappers.AttemptWrapper(task.getStepicId()));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse attemptResponse = ourClient.execute(attemptRequest);
      final HttpEntity responseEntity = attemptResponse.getEntity();
      final String attemptResponseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = attemptResponse.getStatusLine();
      if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to make attempt " + attemptResponseString);
      }
      final StepicWrappers.AttemptWrapper.Attempt attempt = new Gson().fromJson(attemptResponseString, StepicWrappers.AttemptContainer.class).attempts.get(0);

      final Map<String, TaskFile> taskFiles = task.getTaskFiles();
      final ArrayList<StepicWrappers.SolutionFile> files = new ArrayList<StepicWrappers.SolutionFile>();
      for (TaskFile fileEntry : taskFiles.values()) {
        files.add(new StepicWrappers.SolutionFile(fileEntry.name, fileEntry.text));
      }
      postSubmission(passed, attempt, files);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  private static void postSubmission(boolean passed, StepicWrappers.AttemptWrapper.Attempt attempt, ArrayList<StepicWrappers.SolutionFile> files) throws IOException {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.SUBMISSIONS);
    setHeaders(request, EduStepicNames.CONTENT_TYPE_APPL_JSON);

    String requestBody = new Gson().toJson(new StepicWrappers.SubmissionWrapper(attempt.id, passed ? "1" : "0", files));
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
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "courses");
    if (ourClient == null || !relogin) {
      if (!login(project)) return;
    }
    final StepicWrappers.AuthorWrapper user = getCurrentUser();
    if (user != null) {
      course.setAuthors(user.users);
    }

    setHeaders(request, EduStepicNames.CONTENT_TYPE_APPL_JSON);
    String requestBody = new Gson().toJson(new StepicWrappers.CourseWrapper(course));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        if (!relogin) {
          login(project);
          postCourse(project, course, true, indicator);
        }
        LOG.error("Failed to push " + responseString);
        return;
      }
      final CourseInfo postedCourse = new Gson().fromJson(responseString, StepicWrappers.CoursesContainer.class).courses.get(0);

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

  private static boolean login(@NotNull final Project project) {
    final String login = StudyTaskManager.getInstance(project).getLogin();
    if (StringUtil.isEmptyOrSpaces(login)) {
      return showLoginDialog();
    }
    else {
      if (login(login, StudyTaskManager.getInstance(project).getPassword()) == null) {
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
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.UNITS);
    setHeaders(request, EduStepicNames.CONTENT_TYPE_APPL_JSON);
    final StepicWrappers.UnitWrapper unitWrapper = new StepicWrappers.UnitWrapper();
    unitWrapper.unit = new StepicWrappers.Unit();
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
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "sections");
    setHeaders(request, "application/json");
    final StepicWrappers.Section section = new StepicWrappers.Section();
    section.course = courseId;
    section.title = title;
    section.position = position;
    final StepicWrappers.SectionWrapper sectionContainer = new StepicWrappers.SectionWrapper();
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
      final StepicWrappers.Section
        postedSection = new Gson().fromJson(responseString, StepicWrappers.SectionContainer.class).sections.get(0);
      return postedSection.id;
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static int updateLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPut request = new HttpPut(EduStepicNames.STEPIC_API_URL + EduStepicNames.LESSONS + String.valueOf(lesson.id));
    if (ourClient == null) {
      if (!login(project)) {
        LOG.error("Failed to push lesson");
        return 0;
      }
    }

    setHeaders(request, "application/json");
    String requestBody = new Gson().toJson(new StepicWrappers.LessonWrapper(lesson));
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

  public static int postLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.LESSONS);
    if (ourClient == null) {
      login(project);
    }

    setHeaders(request, "application/json");
    String requestBody = new Gson().toJson(new StepicWrappers.LessonWrapper(lesson));
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
    final HttpDelete request = new HttpDelete(EduStepicNames.STEPIC_API_URL + EduStepicNames.STEP_SOURCES + task);
    setHeaders(request, "application/json");
    ApplicationManager.getApplication().invokeLater(() -> {
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
    });
  }

  public static void postTask(final Project project, @NotNull final Task task, final int lessonId) {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.STEP_SOURCES);
    setHeaders(request, "application/json");
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    ApplicationManager.getApplication().invokeLater(() -> {
      final String requestBody = gson.toJson(new StepicWrappers.StepSourceWrapper(project, task, lessonId));
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
    });
  }

  static void setHeaders(@NotNull final HttpRequestBase request, String contentType) {
    request.addHeader(new BasicHeader("referer", stepicUrl));
    request.addHeader(new BasicHeader("X-CSRFToken", ourCSRFToken));
    request.addHeader(new BasicHeader("content-type", contentType));
  }
}
