package com.jetbrains.edu.learning.stepik;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.net.HttpConfigurable;
import com.jetbrains.edu.learning.StudySerializationUtils;
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
import org.apache.http.entity.AbstractHttpEntity;
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

@Deprecated
public class EduStepikConnector {
  private static final Logger LOG = Logger.getInstance(EduStepikConnector.class.getName());
  private static final String stepikUrl = "https://stepik.org/";
  private static String ourCSRFToken = "";
  private static String accessToken = "";
  private static CloseableHttpClient ourClient;
  private static final String CLIENT_ID = "hUCWcq3hZHCmz0DKrDtwOWITLcYutzot7p4n59vU";

  //this prefix indicates that course can be opened by educational plugin
  public static final String PYCHARM_PREFIX = "pycharm";
  public static final String CODE_PREFIX = "code";
  public static final String PYTHON27 = "python27";
  public static final String PYTHON3 = "python3";
  private static BasicCookieStore ourCookieStore;

  static final private Gson GSON =
    new GsonBuilder().registerTypeAdapter(TaskFile.class, new StudySerializationUtils.Json.StepikTaskFileAdapter())
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

  private EduStepikConnector() {
  }

  // TODO : merge. look at comments
  public static StepikUser login(@NotNull final String username, @NotNull final String password) {
    initializeClient();
    StepikWrappers.TokenInfo tokenInfo = postCredentials(username, password);
    if (tokenInfo == null) {
      return null;
    }
    accessToken = tokenInfo.accessToken;
    initializeClient();
    final StepikWrappers.AuthorWrapper stepikUserWrapper = getCurrentUser();
    if (stepikUserWrapper != null && stepikUserWrapper.users.size() == 1) {
      StepikUser user = stepikUserWrapper.users.get(0);
      user.setupTokenInfo(tokenInfo);
      user.setEmail(username);
      user.setPassword(password);
      return user;
    }
    return null;
  }

  public static StepikUser testLogin(@NotNull final String username, @NotNull final String password) {
    CloseableHttpClient tmp = ourClient;
    String tmpToken = accessToken;
    resetClient();
    StepikUser user = login(username, password);
    ourClient = tmp;
    accessToken = tmpToken;
    return user;
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

  @Nullable
  public static StepikWrappers.AuthorWrapper getCurrentUser() {
    try {
      return getFromStepik(EduStepikNames.CURRENT_USER, StepikWrappers.AuthorWrapper.class);
    }
    catch (IOException e) {
      LOG.warn("Couldn't get author info");
    }
    return null;
  }

  public static boolean createUser(@NotNull final String user, @NotNull final String password) {
    final HttpPost userRequest = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.USERS);
    initializeClient();
    //setHeaders(userRequest, "application/json");
    setHeaders(userRequest);
    String requestBody = new Gson().toJson(new StepikWrappers.UserWrapper(user, password));
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
      HttpClientBuilder builder = HttpClients
        .custom()
        .setMaxConnPerRoute(100000)
        .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);

      HttpConfigurable instance = HttpConfigurable.getInstance();
      if (instance.USE_HTTP_PROXY) {
        HttpHost host = new HttpHost(instance.PROXY_HOST, instance.PROXY_PORT);
        builder.setProxy(host);
      }

      try {
        // Create a trust manager that does not validate certificate for this connection
        TrustManager[] trustAllCerts = getTrustAllCerts();
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        ourClient = builder.setSslcontext(sslContext).build();
      }
      catch (NoSuchAlgorithmException | KeyManagementException e) {
        LOG.error(e.getMessage());
      }
    }
  }

  public static void resetClient() {
    accessToken = "";
    ourClient = null;
  }

  private static StepikWrappers.TokenInfo postCredentials(String user, String password) {
    //String url = EduStepikNames.STEPIK_URL + EduStepikNames.LOGIN;
    String url = EduStepikNames.TOKEN_URL;
    final HttpPost request = new HttpPost(url);
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("grant_type", "password"));
    nvps.add(new BasicNameValuePair("client_id", CLIENT_ID));
    nvps.add(new BasicNameValuePair("username", user));
    nvps.add(new BasicNameValuePair("password", password));

    request.setEntity(new UrlEncodedFormEntity(nvps, Consts.UTF_8));

    try {
      final CloseableHttpResponse response = ourClient.execute(request);
      final StatusLine statusLine = response.getStatusLine();
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        return GSON.fromJson(responseString, StepikWrappers.TokenInfo.class);
      }
      else {
        LOG.warn("Failed to login: " + statusLine.getStatusCode() + statusLine.getReasonPhrase());
        LOG.info("Failed to login " + responseString);
        throw new IOException("Stepik returned non 200 status code " + responseString);
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
      return null;
    }
  }

  static <T> T getFromStepik(String link, final Class<T> container) throws IOException {
    final HttpGet request = new HttpGet(EduStepikNames.STEPIK_API_URL + link);
    if (ourClient == null) {
      initializeClient();
    }
    //    setHeaders(request, EduStepikNames.CONTENT_TYPE_APPL_JSON);
    setHeaders(request);

    final CloseableHttpResponse response = ourClient.execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepik returned non 200 status code " + responseString);
    }
    return GSON.fromJson(responseString, container);
  }

  static boolean postToStepik(String link, AbstractHttpEntity entity) throws IOException {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + link);
    request.setEntity(entity);
    if (ourClient == null) {
      initializeClient();
    }
    setHeaders(request);

    final CloseableHttpResponse response = ourClient.execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepik returned non 200 status code " + responseString);
    }
    return true;
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

    final StepikWrappers.EnrollmentWrapper enrollment = new StepikWrappers.EnrollmentWrapper(String.valueOf(courseId));
    try {
      return postToStepik(EduStepikNames.ENROLLMENTS, new StringEntity(new GsonBuilder().create().toJson(enrollment)));
    }
    catch (IOException e) {
      LOG.warn("EnrollToCourse error\n" + e.getMessage());
    }
    return false;
  }

  @NotNull
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
    final StepikWrappers.CoursesContainer coursesContainer = getFromStepik(url, StepikWrappers.CoursesContainer.class);
    final List<CourseInfo> courseInfos = coursesContainer.courses;
    for (CourseInfo info : courseInfos) {
      final String courseType = info.getType();
      if (!info.isAdaptive() && StringUtil.isEmptyOrSpaces(courseType)) continue;
      final List<String> typeLanguage = StringUtil.split(courseType, " ");
      // TODO: should adaptive course be of PyCharmType ?
      if (info.isAdaptive() || (typeLanguage.size() == 2 && PYCHARM_PREFIX.equals(typeLanguage.get(0)))) {
        for (Integer instructor : info.instructors) {
          final StepikUser author =
            getFromStepik(EduStepikNames.USERS + "/" + String.valueOf(instructor), StepikWrappers.AuthorWrapper.class).users.get(0);
          info.addAuthor(author);
        }

        String name = info.getName().replaceAll("[^a-zA-Z0-9\\s]", "");
        info.setName(name.trim());

        result.add(info);
      }
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
        switch (course.getCourseType()){
          case ("stepik") : course.addLessons(getLessons2(section));
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

  public static List<Lesson> getLessons2(int sectionId) throws IOException {
    final StepikWrappers.SectionContainer
            sectionContainer = getFromStepik(EduStepikNames.SECTIONS + String.valueOf(sectionId), StepikWrappers.SectionContainer.class);
    List<Integer> unitIds = sectionContainer.sections.get(0).units;

    StepikWrappers.UnitContainer
            unitContainer = getFromStepik(EduStepikNames.UNITS + "/" + getIdQuery(unitIds), StepikWrappers.UnitContainer.class);
    List<Integer> lessonsIds = new ArrayList<>();
    unitContainer.units.forEach(x -> lessonsIds.add(x.lesson));
    StepikWrappers.LessonContainer
            lessonContainer = getFromStepik(EduStepikNames.LESSONS + getIdQuery(lessonsIds), StepikWrappers.LessonContainer.class);

    String sectionName = sectionContainer.sections.get(0).title;
    final List<Lesson> lessons = new ArrayList<Lesson>();
    for (Lesson lesson : lessonContainer.lessons) {
      lesson.setName(sectionName + EduNames.SEPARATOR + lesson.getName());
//      LOG.info("set lesson name " + lesson.getName());
      createTasks(lesson, lesson.steps);
      if (!lesson.taskList.isEmpty()) {
        lessons.add(lesson);
      }
    }
    return lessons;
  }

  private static void createTasks(Lesson lesson, List<Integer> stepikIds) throws IOException {
    final StepikWrappers.StepContainer stepContainer = getSteps(stepikIds);
    List<StepikWrappers.Step> steps = new ArrayList<>();
    stepContainer.steps.forEach(x -> steps.add(x.block));
    int i = 0;
    for (StepikWrappers.Step step : steps) {
      if (supported(step.name)) {
        final Task task = new Task();
        task.setStepId(stepikIds.get(i++));

        switch (step.name) {
          case (CODE_PREFIX):
            createCodeTask(task, step);
            break;
          case (PYCHARM_PREFIX):
            createPyCharmTask(task, step);
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
    task.setName("step" + task.getStepId());
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

    final TaskFile taskFile = new TaskFile();
    taskFile.name = "Main.java";
    //final String templateForTask = getCodeTemplateForTask();
    final String templateForTask = step.options.codeTemplates.getTemplateForLanguage("java");
    taskFile.text = templateForTask == null ? "# write your answer here \n" : templateForTask;
    task.taskFiles.put(taskFile.name, taskFile);
  }

  public static StepikWrappers.Step getStep(Integer step) throws IOException {
    return getFromStepik(EduStepikNames.STEPS + "/" + String.valueOf(step), StepikWrappers.StepContainer.class).steps.get(0).block;
  }

  public static StepikWrappers.StepContainer getSteps(List<Integer> steps) throws IOException {
    return getFromStepik(EduStepikNames.STEPS + "/" + getIdQuery(steps), StepikWrappers.StepContainer.class);
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

  //TODO rewrite with postToStepik
  // use StepikConnectorPost.postAttempt(Task)
  public static void postAttempt(@NotNull final Task task, boolean passed, @Nullable String login, @Nullable String password) {
    if (task.getStepId() <= 0) {
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

    final HttpPost attemptRequest = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.ATTEMPTS);
    setHeaders(attemptRequest, "application/json");
    String attemptRequestBody = new Gson().toJson(new StepikWrappers.AttemptWrapper(task.getStepId()));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse attemptResponse = ourClient.execute(attemptRequest);
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

  //TODO rewrite with postToStepik
  private static void postSubmission(boolean passed,
                                     StepikWrappers.AttemptWrapper.Attempt attempt,
                                     ArrayList<StepikWrappers.SolutionFile> files) throws IOException {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.SUBMISSIONS);
    setHeaders(request, EduStepikNames.CONTENT_TYPE_APPL_JSON);

    String requestBody = new Gson().toJson(new StepikWrappers.SubmissionContainer(attempt.id, passed ? "1" : "0", files));
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

  //TODO rewrite with postToStepik
  private static void postCourse(final Project project,
                                 @NotNull Course course,
                                 boolean relogin,
                                 @NotNull final ProgressIndicator indicator) {
    indicator.setText("Uploading course to " + stepikUrl);
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + "courses");
    if (ourClient == null || !relogin) {
      if (!login(project)) return;
    }
    final StepikWrappers.AuthorWrapper user = getCurrentUser();
    if (user != null) {
      course.setAuthors(user.users);
    }

    setHeaders(request, EduStepikNames.CONTENT_TYPE_APPL_JSON);
    String requestBody = new Gson().toJson(new StepikWrappers.CourseWrapper(course));
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

  public static boolean login(@NotNull final Project project) {
    StepikUser user = StudyTaskManager.getInstance(project).getUser();
    final String login = user.getEmail();
    if (StringUtil.isEmptyOrSpaces(login)) {
        if ( (user = StudyTaskManager.getInstance(ProjectManager.getInstance().getDefaultProject()).getUser()) == null) {
          return showLoginDialog();
        }
        else {
          StudyTaskManager.getInstance(project).setUser(user);
        }
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

  private static void postUnit(int lessonId, int position, int sectionId) {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.UNITS);
    setHeaders(request, EduStepikNames.CONTENT_TYPE_APPL_JSON);
    final StepikWrappers.UnitWrapper unitWrapper = new StepikWrappers.UnitWrapper();
    unitWrapper.unit = new StepikWrappers.Unit();
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
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + "sections");
    setHeaders(request, "application/json");
    final StepikWrappers.Section section = new StepikWrappers.Section();
    section.course = courseId;
    section.title = title;
    section.position = position;
    final StepikWrappers.SectionWrapper sectionContainer = new StepikWrappers.SectionWrapper();
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
      final StepikWrappers.Section
        postedSection = new Gson().fromJson(responseString, StepikWrappers.SectionContainer.class).sections.get(0);
      return postedSection.id;
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return -1;
  }

  public static int updateLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPut request = new HttpPut(EduStepikNames.STEPIK_API_URL + EduStepikNames.LESSONS + String.valueOf(lesson.getId()));
    if (ourClient == null) {
      if (!login(project)) {
        LOG.error("Failed to push lesson");
        return 0;
      }
    }

    setHeaders(request, "application/json");
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

  public static int postLesson(@NotNull final Project project, @NotNull final Lesson lesson, ProgressIndicator indicator) {
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.LESSONS);
    if (ourClient == null) {
      login(project);
    }

    setHeaders(request, "application/json");
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

  public static void deleteTask(@NotNull final Integer task) {
    final HttpDelete request = new HttpDelete(EduStepikNames.STEPIK_API_URL + EduStepikNames.STEP_SOURCES + task);
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
    final HttpPost request = new HttpPost(EduStepikNames.STEPIK_API_URL + EduStepikNames.STEP_SOURCES);
    setHeaders(request, "application/json");
    //TODO: register type adapter for task files here?
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    ApplicationManager.getApplication().invokeLater(() -> {
      final String requestBody = gson.toJson(new StepikWrappers.StepSourceWrapper(project, task, lessonId));
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


  @Deprecated
  //use setHeaders(HttpRequestBase), it sets accessToken
  static void setHeaders(@NotNull final HttpRequestBase request, String contentType) {
    request.addHeader(new BasicHeader("referer", stepikUrl));
    request.addHeader(new BasicHeader("X-CSRFToken", ourCSRFToken));
    request.addHeader(new BasicHeader("content-type", contentType));
  }

  public static void setHeaders(@NotNull final HttpRequestBase request) {
    if (!accessToken.isEmpty()) {
//      LOG.info("setup default headers");
      request.addHeader(new BasicHeader("Authorization", "Bearer " + accessToken));
      request.addHeader(new BasicHeader("content-type", EduStepikNames.CONTENT_TYPE_APPL_JSON));
    }
    else {
      LOG.warn("access_token is empty");
    }
  }

  public static String getIdQuery(List<Integer> list) {
    StringBuilder sb = new StringBuilder();
    sb.append("?");
    for (Integer id : list) {
      sb.append("ids[]=" + id + "&");
    }
    return sb.toString();
  }

  public static TrustManager[] getTrustAllCerts() {
    return new TrustManager[]{new X509TrustManager() {
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }

      public void checkClientTrusted(X509Certificate[] certs, String authType) {
      }

      public void checkServerTrusted(X509Certificate[] certs, String authType) {
      }
    }};
  }

  public static void setAccessToken(String accessToken2) {
    accessToken = accessToken2;
  }
}
