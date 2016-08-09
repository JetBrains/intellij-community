package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.ssl.CertificateManager;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

import static com.jetbrains.edu.learning.stepic.EduStepicNames.CONTENT_TYPE_APPL_JSON;

public class EduStepicConnector {
  private static final Logger LOG = Logger.getInstance(EduStepicConnector.class.getName());
  private static String ourCSRFToken = "";
  private static CloseableHttpClient ourClient;

  //this prefix indicates that course can be opened by educational plugin
  public static final String PYCHARM_PREFIX = "pycharm";
  private static BasicCookieStore ourCookieStore;

  private static final String ADAPTIVE_NOTE =
    "\n\nInitially, the adaptive system may behave somewhat randomly, but the more problems you solve, the smarter it become!";

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
    setHeaders(userRequest, CONTENT_TYPE_APPL_JSON);
    String requestBody = new Gson().toJson(new StepicWrappers.UserWrapper(user, password));
    userRequest.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(userRequest);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = response.getStatusLine();
      EntityUtils.consume(responseEntity);
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
      setHeaders(request, CONTENT_TYPE_APPL_JSON);

      HttpClientBuilder builder =
        HttpClients.custom().setSslcontext(CertificateManager.getInstance().getSslContext()).setMaxConnPerRoute(100000).
          setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);

      final HttpConfigurable proxyConfigurable = HttpConfigurable.getInstance();
      final List<Proxy> proxies = proxyConfigurable.getOnlyBySettingsSelector().select(URI.create(EduStepicNames.STEPIC_URL));
      final InetSocketAddress address = proxies.size() > 0 ? (InetSocketAddress)proxies.get(0).address() : null;
      if (address != null) {
        builder.setProxy(new HttpHost(address.getHostName(), address.getPort()));
      }
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
    List <NameValuePair> nvps = new ArrayList<>();
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
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY) {
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
    if (!link.startsWith("/")) link = "/" + link;
    final HttpGet request = new HttpGet(EduStepicNames.STEPIC_API_URL + link);
    if (ourClient == null) {
      initializeClient();
    }
    setHeaders(request, CONTENT_TYPE_APPL_JSON);

    final CloseableHttpResponse response = ourClient.execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    EntityUtils.consume(responseEntity);
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepic returned non 200 status code " + responseString);
    }
    Gson gson = new GsonBuilder().registerTypeAdapter(TaskFile.class, new StudySerializationUtils.Json.StepicTaskFileAdapter()).
      registerTypeAdapter(AnswerPlaceholder.class, new StudySerializationUtils.Json.StepicAnswerPlaceholderAdapter()).
      setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").
      setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    return gson.fromJson(responseString, container);
  }

  @NotNull
  public static CloseableHttpClient getHttpClient(@NotNull final Project project) {
    if (ourClient == null) {
      login(project);
    }
    return ourClient;
  }

  public static boolean enrollToCourse(final int courseId) {
    HttpPost post = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ENROLLMENTS);
    try {
      final StepicWrappers.EnrollmentWrapper enrollment = new StepicWrappers.EnrollmentWrapper(String.valueOf(courseId));
      post.setEntity(new StringEntity(new GsonBuilder().create().toJson(enrollment)));
      setHeaders(post, CONTENT_TYPE_APPL_JSON);
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
      login(project);
      final StepicWrappers.CoursesContainer coursesContainer = getFromStepic(url, StepicWrappers.CoursesContainer.class);
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
      final List<CourseInfo> courses = getFromStepic(url, StepicWrappers.CoursesContainer.class).courses;
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
      List<Lesson> lessons = getFromStepic(url, StepicWrappers.LessonContainer.class).lessons;
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
      List<StepicWrappers.StepSource> steps = getFromStepic(url, StepicWrappers.StepContainer.class).steps;
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
    final StepicWrappers.CoursesContainer coursesContainer = getFromStepic(url.toString(), StepicWrappers.CoursesContainer.class);
    final List<CourseInfo> courseInfos = coursesContainer.courses;
    for (CourseInfo info : courseInfos) {
      final String courseType = info.getType();
      if (!info.isAdaptive() && StringUtil.isEmptyOrSpaces(courseType)) continue;
      final List<String> typeLanguage = StringUtil.split(courseType, " ");
      if (info.isAdaptive() || (typeLanguage.size() == 2 && PYCHARM_PREFIX.equals(typeLanguage.get(0)))) {
        for (Integer instructor : info.instructors) {
          final StepicUser author = getFromStepic(EduStepicNames.USERS + "/" + String.valueOf(instructor),
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
      sectionContainer = getFromStepic(EduStepicNames.SECTIONS + String.valueOf(sectionId), StepicWrappers.SectionContainer.class);
    List<Integer> unitIds = sectionContainer.sections.get(0).units;
    final List<Lesson> lessons = new ArrayList<>();
    for (Integer unitId : unitIds) {
      StepicWrappers.UnitContainer
        unit = getFromStepic(EduStepicNames.UNITS + "/" + String.valueOf(unitId), StepicWrappers.UnitContainer.class);
      int lessonID = unit.units.get(0).lesson;
      StepicWrappers.LessonContainer
        lessonContainer = getFromStepic(EduStepicNames.LESSONS + String.valueOf(lessonID), StepicWrappers.LessonContainer.class);
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
    return getFromStepic(EduStepicNames.STEPS + "/" + String.valueOf(step), StepicWrappers.StepContainer.class).steps.get(0);
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
    setHeaders(attemptRequest, CONTENT_TYPE_APPL_JSON);
    String attemptRequestBody = new Gson().toJson(new StepicWrappers.AttemptWrapper(task.getStepicId()));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse attemptResponse = ourClient.execute(attemptRequest);
      final HttpEntity responseEntity = attemptResponse.getEntity();
      final String attemptResponseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = attemptResponse.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (statusLine.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
        if (StringUtil.isEmptyOrSpaces(login) || StringUtil.isEmptyOrSpaces(password)) {
          return;
        }
        else {
          login(login, password);
          postAttempt(task, passed, login, password);
        }
      }
      if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.warn("Failed to make attempt " + attemptResponseString);
      }
      final StepicWrappers.AttemptWrapper.Attempt attempt = new Gson().fromJson(attemptResponseString, StepicWrappers.AttemptContainer.class).attempts.get(0);

      final Map<String, TaskFile> taskFiles = task.getTaskFiles();
      final ArrayList<StepicWrappers.SolutionFile> files = new ArrayList<>();
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
    setHeaders(request, CONTENT_TYPE_APPL_JSON);

    String requestBody = new Gson().toJson(new StepicWrappers.SubmissionWrapper(attempt.id, passed ? "1" : "0", files));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
    final CloseableHttpResponse response = ourClient.execute(request);
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    final StatusLine line = response.getStatusLine();
    EntityUtils.consume(responseEntity);
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
    indicator.setText("Uploading course to " + EduStepicNames.STEPIC_URL);
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/courses");
    if (ourClient == null || !relogin) {
      if (!login(project)) return;
    }
    final StepicWrappers.AuthorWrapper authors = getCurrentUser();
    if (authors != null) {
      final List<StepicUser> courseAuthors = course.getAuthors();
      for (int i = 0; i < courseAuthors.size(); i++) {
        final StepicUser user = authors.users.get(i);
        if (courseAuthors.size() > i) {
          final StepicUser courseAuthor = courseAuthors.get(i);
          user.setFirstName(courseAuthor.getFirstName());
          user.setLastName(courseAuthor.getLastName());
        }
      }

      course.setAuthors(authors.users);
    }

    setHeaders(request, CONTENT_TYPE_APPL_JSON);
    String requestBody = new Gson().toJson(new StepicWrappers.CourseWrapper(course));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() != HttpStatus.SC_CREATED) {
        if (!relogin) {
          login(project);
          postCourse(project, course, true, indicator);
        }
        LOG.error("Failed to push " + responseString);
        return;
      }
      final CourseInfo postedCourse = new Gson().fromJson(responseString, StepicWrappers.CoursesContainer.class).courses.get(0);
      course.setId(postedCourse.id);
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

  static boolean login(@NotNull final Project project) {
    final StepicUser user = StudyTaskManager.getInstance(project).getUser();
    final String login =  user.getEmail();
    if (StringUtil.isEmptyOrSpaces(login)) {
      return showLoginDialog();
    }
    else {
      if (login(login, user.getPassword()) == null) {
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

  public static void postUnit(int lessonId, int position, int sectionId) {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.UNITS);
    setHeaders(request, CONTENT_TYPE_APPL_JSON);
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
    setHeaders(request, CONTENT_TYPE_APPL_JSON);
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

    if (ourClient == null) {
      if (!login(project)) {
        LOG.error("Failed to update task");
        return -1;
      }
    }

    final HttpPut request = new HttpPut(EduStepicNames.STEPIC_API_URL + "/step-sources/" + String.valueOf(task.getStepicId()));
    setHeaders(request, CONTENT_TYPE_APPL_JSON);
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().
      registerTypeAdapter(AnswerPlaceholder.class, new StudySerializationUtils.Json.StepicAnswerPlaceholderAdapter()).create();
    ApplicationManager.getApplication().invokeLater(() -> {
      final String requestBody = gson.toJson(new StepicWrappers.StepSourceWrapper(project, task, lessonId));
      request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

      try {
        final CloseableHttpResponse response = ourClient.execute(request);
        final HttpEntity responseEntity = response.getEntity();
        final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
        EntityUtils.consume(responseEntity);
        final StatusLine line = response.getStatusLine();
        if (line.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
          if (login(project)) {
            updateTask(project, task);
            return;
          }
        }
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
    if (ourClient == null) {
      if (!login(project)) {
        LOG.error("Failed to push lesson");
        return -1;
      }
    }

    setHeaders(request, CONTENT_TYPE_APPL_JSON);
    String requestBody = new Gson().toJson(new StepicWrappers.LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
        if (login(project)) {
          return updateLesson(project, lesson, indicator);
        }
      }
      if (line.getStatusCode() != HttpStatus.SC_OK) {
        LOG.error("Failed to push " + responseString);
        return -1;
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

  public static int postLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/lessons");
    if (ourClient == null) {
      login(project);
    }

    setHeaders(request, CONTENT_TYPE_APPL_JSON);
    String requestBody = new Gson().toJson(new StepicWrappers.LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine line = response.getStatusLine();
      EntityUtils.consume(responseEntity);
      if (line.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
        if (login(project)) {
          return postLesson(project, lesson, indicator);
        }
      }
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

  public static void deleteTask(@NotNull final Integer task) {
    final HttpDelete request = new HttpDelete(EduStepicNames.STEPIC_API_URL + EduStepicNames.STEP_SOURCES + task);
    setHeaders(request, CONTENT_TYPE_APPL_JSON);
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        final CloseableHttpResponse response = ourClient.execute(request);
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
    if (ourClient == null) {
      if (!login(project)) {
        LOG.error("Failed to update task");
      }
    }

    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + "/step-sources");
    setHeaders(request, CONTENT_TYPE_APPL_JSON);
    //TODO: register type adapter for task files here?
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().
      registerTypeAdapter(AnswerPlaceholder.class, new StudySerializationUtils.Json.StepicAnswerPlaceholderAdapter()).create();
    ApplicationManager.getApplication().invokeLater(() -> {
      final String requestBody = gson.toJson(new StepicWrappers.StepSourceWrapper(project, task, lessonId));
      request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

      try {
        final CloseableHttpResponse response = ourClient.execute(request);
        final StatusLine line = response.getStatusLine();
        final HttpEntity responseEntity = response.getEntity();
        final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
        EntityUtils.consume(responseEntity);
        if (line.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
          if (login(project)) {
            postTask(project, task, lessonId);
            return;
          }
        }
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

  static void setHeaders(@NotNull final HttpRequestBase request, String contentType) {
    request.addHeader(new BasicHeader("referer", EduStepicNames.STEPIC_URL));
    request.addHeader(new BasicHeader("X-CSRFToken", ourCSRFToken));
    request.addHeader(new BasicHeader("content-type", contentType));
  }
}
