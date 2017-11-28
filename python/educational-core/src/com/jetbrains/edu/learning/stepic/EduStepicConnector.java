package com.jetbrains.edu.learning.stepic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.PyCharmTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.BuiltInServerOptions;
import org.jetbrains.ide.BuiltInServerManager;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static com.jetbrains.edu.learning.stepic.EduStepicNames.PYCHARM_PREFIX;

public class EduStepicConnector {
  private static final Logger LOG = Logger.getInstance(EduStepicConnector.class.getName());

  public static final int CURRENT_VERSION = 2;
  //this prefix indicates that course can be opened by educational plugin
  private static final String ADAPTIVE_NOTE =
    "\n\nInitially, the adaptive system may behave somewhat randomly, but the more problems you solve, the smarter it becomes!";

  private EduStepicConnector() {
  }

  public static boolean enrollToCourse(final int courseId, @Nullable final StepicUser stepicUser) {
    if (stepicUser == null) return false;
    HttpPost post = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ENROLLMENTS);
    try {
      final StepicWrappers.EnrollmentWrapper enrollment = new StepicWrappers.EnrollmentWrapper(String.valueOf(courseId));
      post.setEntity(new StringEntity(new GsonBuilder().create().toJson(enrollment)));
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient(stepicUser);
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
  public static List<Course> getCourses(@Nullable StepicUser user) {
    try {
      List<Course> result = new ArrayList<>();
      int pageNumber = 1;
      while (addCoursesFromStepic(user, result, pageNumber)) {
        pageNumber += 1;
      }
      return result;
    }
    catch (IOException e) {
      LOG.warn("Cannot load course list " + e.getMessage());
    }
    return Collections.emptyList();
  }

  public static Date getCourseUpdateDate(final int courseId) {
    final String url = EduStepicNames.COURSES + "/" + courseId;
    try {
      final List<RemoteCourse> courses = EduStepicClient.getFromStepic(url, StepicWrappers.CoursesContainer.class).courses;
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
      LOG.warn("Could not retrieve lesson with id=" + lessonId);
    }

    return null;
  }

  public static Date getTaskUpdateDate(final int taskId) {
    final String url = EduStepicNames.STEPS + String.valueOf(taskId);
    try {
      List<StepicWrappers.StepSource> steps = EduStepicClient.getFromStepic(url, StepicWrappers.StepContainer.class).steps;
      if (!steps.isEmpty()) {
        return steps.get(0).update_date;
      }
    }
    catch (IOException e) {
      LOG.warn("Could not retrieve task with id=" + taskId);
    }

    return null;
  }

  public static StepicWrappers.CoursesContainer getCoursesFromStepik(@Nullable StepicUser user, URI url) throws IOException {
    final StepicWrappers.CoursesContainer coursesContainer;
    if (user != null) {
      coursesContainer = EduStepicAuthorizedClient.getFromStepic(url.toString(), StepicWrappers.CoursesContainer.class, user);
    }
    else {
      coursesContainer = EduStepicClient.getFromStepic(url.toString(), StepicWrappers.CoursesContainer.class);
    }
    return coursesContainer;
  }

  private static boolean addCoursesFromStepic(@Nullable StepicUser user, List<Course> result, int pageNumber) throws IOException {
    final URI url;
    try {
      url = new URIBuilder(EduStepicNames.COURSES).addParameter("is_idea_compatible", "true").
        addParameter("page", String.valueOf(pageNumber)).build();
    }
    catch (URISyntaxException e) {
      LOG.error(e.getMessage());
      return false;
    }
    final StepicWrappers.CoursesContainer coursesContainer = getCoursesFromStepik(user, url);
    addAvailableCourses(result, coursesContainer);
    return coursesContainer.meta.containsKey("has_next") && coursesContainer.meta.get("has_next") == Boolean.TRUE;
  }

  @Nullable
  public static Course getCourseFromStepik(@Nullable StepicUser user, int courseId) throws IOException {
    final URI url;
    try {
      url = new URIBuilder(EduStepicNames.COURSES + "/" + courseId).addParameter("is_idea_compatible", "true")
        .build();
    }
    catch (URISyntaxException e) {
      LOG.error(e.getMessage());
      return null;
    }
    final StepicWrappers.CoursesContainer coursesContainer = getCoursesFromStepik(user, url);

    if (coursesContainer != null && !coursesContainer.courses.isEmpty()) {
      return coursesContainer.courses.get(0);
    } else {
      return null;
    }
  }

  static void addAvailableCourses(List<Course> result, StepicWrappers.CoursesContainer coursesContainer) throws IOException {
    final List<RemoteCourse> courses = coursesContainer.courses;
    for (RemoteCourse info : courses) {
      if (!info.isAdaptive() && StringUtil.isEmptyOrSpaces(info.getType())) continue;
      setCourseLanguage(info);

      if (canBeOpened(info)) {
        final ArrayList<StepicUser> authors = new ArrayList<>();
        for (Integer instructor : info.getInstructors()) {
          final StepicUser author = EduStepicClient.getFromStepic(EduStepicNames.USERS + String.valueOf(instructor),
                                                                  StepicWrappers.AuthorWrapper.class).users.get(0);
          authors.add(author);
        }
        info.setAuthors(authors);

        if (info.isAdaptive()) {
          info.setDescription("This is a Stepik Adaptive course.\n\n" + info.getDescription() + ADAPTIVE_NOTE);
        }
        result.add(info);
      }
    }
  }

  private static void setCourseLanguage(RemoteCourse info) {
    String courseType = info.getType();
    final int separator = courseType.indexOf(" ");
    assert separator != -1;
    final String language = courseType.substring(separator + 1);
    info.setLanguage(language);
  }

  static boolean canBeOpened(RemoteCourse courseInfo) {
    final ArrayList<String> supportedLanguages = new ArrayList<>();
    final LanguageExtensionPoint[] extensions = Extensions.getExtensions(EduPluginConfigurator.EP_NAME, null);
    for (LanguageExtensionPoint extension : extensions) {
      String languageId = extension.getKey();
      supportedLanguages.add(languageId);
    }

    if (courseInfo.isAdaptive()) {
      return supportedLanguages.contains(courseInfo.getLanguageID());
    }

    String courseType = courseInfo.getType();
    final List<String> typeLanguage = StringUtil.split(courseType, " ");
    String prefix = typeLanguage.get(0);
    if (!supportedLanguages.contains(courseInfo.getLanguageID())) return false;
    if (typeLanguage.size() < 2 || !prefix.startsWith(PYCHARM_PREFIX)) {
      return false;
    }
    String versionString = prefix.substring(PYCHARM_PREFIX.length());
    if (versionString.isEmpty()) {
      return true;
    }
    try {
      Integer version = Integer.valueOf(versionString);
      return version <= CURRENT_VERSION;
    }
    catch (NumberFormatException e) {
      LOG.info("Wrong version format", e);
      return false;
    }
  }

  public static RemoteCourse getCourse(@NotNull final Project project, @NotNull final RemoteCourse remoteCourse) {
    final List<Lesson> lessons = remoteCourse.getLessons(true);
    if (!lessons.isEmpty()) return remoteCourse;
    if (!remoteCourse.isAdaptive()) {
      try {
        for (Integer section : remoteCourse.getSections()) {
          remoteCourse.addLessons(getLessons(section));
        }
        return remoteCourse;
      }
      catch (IOException e) {
        LOG.error("IOException " + e.getMessage());
      }
    }
    else {
      final Lesson lesson = new Lesson();
      lesson.setName(EduNames.ADAPTIVE);
      remoteCourse.addLesson(lesson);
      //TODO: more specific name?
      final Task recommendation = EduAdaptiveStepicConnector.getNextRecommendation(project, remoteCourse);
      if (recommendation != null) {
        lesson.addTask(recommendation);
      }
      return remoteCourse;
    }
    return null;
  }

  public static List<Lesson> getLessons(int sectionId) throws IOException {
    final StepicWrappers.SectionContainer
      sectionContainer = getFromStepik(EduStepicNames.SECTIONS + String.valueOf(sectionId),
                                       StepicWrappers.SectionContainer.class);
    List<Integer> unitIds = sectionContainer.sections.get(0).units;
    final List<Lesson> lessons = new ArrayList<>();
    for (Integer unitId : unitIds) {
      StepicWrappers.UnitContainer
        unit = getFromStepik(EduStepicNames.UNITS + "/" + String.valueOf(unitId), StepicWrappers.UnitContainer.class);
      int lessonID = unit.units.get(0).lesson;
      StepicWrappers.LessonContainer
        lessonContainer = getFromStepik(EduStepicNames.LESSONS + String.valueOf(lessonID),
                                        StepicWrappers.LessonContainer.class);
      Lesson lesson = lessonContainer.lessons.get(0);
      lesson.taskList = new ArrayList<>();
      for (int stepId : lesson.steps) {
        final Task task = createTask(stepId);
        if (task != null) {
          lesson.addTask(task);
        }
      }
      if (!lesson.taskList.isEmpty()) {
        lessons.add(lesson);
      }
    }

    return lessons;
  }

  private static <T> T getFromStepik(String link, final Class<T> container) throws IOException {
    final StepicUser user = StudySettings.getInstance().getUser();
    final boolean isAuthorized = user != null;
    if (isAuthorized) {
      return EduStepicAuthorizedClient.getFromStepic(link, container, user);
    }
    return EduStepicClient.getFromStepic(link, container);
  }

  @Nullable
  public static Task createTask(int stepicId) throws IOException {
    final StepicWrappers.StepSource step = getStep(stepicId);
    final StepicWrappers.Step block = step.block;
    if (!block.name.startsWith(PYCHARM_PREFIX)) {
      LOG.error("Got a block with non-pycharm prefix: " + block.name + " for step: " + stepicId);
      return null;
    }
    final int lastSubtaskIndex = block.options.lastSubtaskIndex;
    Task task = new PyCharmTask();
    if (lastSubtaskIndex != 0) {
      task = createTaskWithSubtasks(lastSubtaskIndex);
    }
    task.setStepId(stepicId);
    task.setUpdateDate(step.update_date);
    task.setName(block.options != null ? block.options.title : (PYCHARM_PREFIX + CURRENT_VERSION));

    for (StepicWrappers.FileWrapper wrapper : block.options.test) {
      task.addTestsTexts(wrapper.name, wrapper.text);
    }
    if (block.options.text != null) {
      for (StepicWrappers.FileWrapper wrapper : block.options.text) {
        task.addTaskText(wrapper.name, wrapper.text);
      }
    } else {
      task.addTaskText(EduNames.TASK, block.text);
    }

    task.taskFiles = new HashMap<>();      // TODO: it looks like we don't need taskFiles as map anymore
    if (block.options.files != null) {
      for (TaskFile taskFile : block.options.files) {
        addPlaceholdersTexts(taskFile);
        task.taskFiles.put(taskFile.name, taskFile);
      }
    }
    return task;
  }

  private static void addPlaceholdersTexts(TaskFile file) {
    final String fileText = file.text;
    final List<AnswerPlaceholder> placeholders = file.getAnswerPlaceholders();
    for (AnswerPlaceholder placeholder : placeholders) {
      final AnswerPlaceholderSubtaskInfo info = placeholder.getActiveSubtaskInfo();
      if (info == null) {
        continue;
      }
      final int offset = placeholder.getOffset();
      final int length = placeholder.getLength();
      if (fileText.length() > offset + length) {
        info.setPlaceholderText(fileText.substring(offset, offset + length));
      }
    }
  }

  @NotNull
  private static Task createTaskWithSubtasks(int lastSubtaskIndex) {
    TaskWithSubtasks task = new TaskWithSubtasks();
    task.setLastSubtaskIndex(lastSubtaskIndex);
    return task;
  }

  public static StepicWrappers.StepSource getStep(int step) throws IOException {
    return getFromStepik(EduStepicNames.STEPS + String.valueOf(step),
                         StepicWrappers.StepContainer.class).steps.get(0);
  }

  public static void postSolution(@NotNull final Task task, boolean passed, @NotNull final Project project) {
    if (task.getStepId() <= 0) {
      return;
    }

    try {
      final String response = postAttempt(task.getStepId());
      if (response.isEmpty()) return;
      final StepicWrappers.AttemptWrapper.Attempt attempt =
        new Gson().fromJson(response, StepicWrappers.AttemptContainer.class).attempts.get(0);
      final Map<String, TaskFile> taskFiles = task.getTaskFiles();
      final ArrayList<StepicWrappers.SolutionFile> files = new ArrayList<>();
      final VirtualFile taskDir = task.getTaskDir(project);
      if (taskDir == null) {
        LOG.error("Failed to find task directory " + task.getName());
        return;
      }
      for (TaskFile fileEntry : taskFiles.values()) {
        final String fileName = fileEntry.name;
        final VirtualFile virtualFile = taskDir.findFileByRelativePath(fileName);
        if (virtualFile != null) {
          ApplicationManager.getApplication().runReadAction(() -> {
            final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
            if (document != null) {
              files.add(new StepicWrappers.SolutionFile(fileName, document.getCharsSequence().toString()));
            }
          });
        }
      }

      postSubmission(passed, attempt, files);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  public static String postAttempt(int id) throws IOException {
    final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
    if (client == null || StudySettings.getInstance().getUser() == null) return "";
    final HttpPost attemptRequest = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ATTEMPTS);
    String attemptRequestBody = new Gson().toJson(new StepicWrappers.AttemptWrapper(id));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    final CloseableHttpResponse attemptResponse = client.execute(attemptRequest);
    final HttpEntity responseEntity = attemptResponse.getEntity();
    final String attemptResponseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    final StatusLine statusLine = attemptResponse.getStatusLine();
    EntityUtils.consume(responseEntity);
    if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
      LOG.warn("Failed to make attempt " + attemptResponseString);
      return "";
    }
    return attemptResponseString;
  }

  private static void postSubmission(boolean passed, StepicWrappers.AttemptWrapper.Attempt attempt,
                                     ArrayList<StepicWrappers.SolutionFile> files) throws IOException {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.SUBMISSIONS);

    String requestBody = new Gson().toJson(new StepicWrappers.SubmissionWrapper(attempt.id, passed ? "1" : "0", files));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
    final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
    if (client == null) return;
    final CloseableHttpResponse response = client.execute(request);
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    final StatusLine line = response.getStatusLine();
    EntityUtils.consume(responseEntity);
    if (line.getStatusCode() != HttpStatus.SC_CREATED) {
      LOG.error("Failed to make submission " + responseString);
    }
  }

  @NotNull
  public static String createOAuthLink(String authRedirectUrl) {
    return "https://stepik.org/oauth2/authorize/" +
           "?client_id=" + EduStepicNames.CLIENT_ID +
           "&redirect_uri=" + authRedirectUrl +
           "&response_type=code";
  }

  @NotNull
  public static String getOAuthRedirectUrl() {
    int port = BuiltInServerManager.getInstance().getPort();

    // according to https://confluence.jetbrains.com/display/IDEADEV/Remote+communication
    int defaultPort = BuiltInServerOptions.getInstance().builtInServerPort;
    if (port >= defaultPort && port < (defaultPort + 20)) {
      return "http://localhost:" + port + "/api/" + EduStepicNames.OAUTH_SERVICE_NAME;
    }

    return EduStepicNames.EXTERNAL_REDIRECT_URL;
  }

  public static void doAuthorize(@NotNull Runnable externalRedirectUrlHandler) {
    EduUsagesCollector.stepikAuthorization();
    String redirectUrl = getOAuthRedirectUrl();
    String link = createOAuthLink(redirectUrl);
    BrowserUtil.browse(link);
    if (!redirectUrl.startsWith("http://localhost")) {
      externalRedirectUrlHandler.run();
    }
  }

  public static StepicWrappers.Unit getUnit(int unitId) {
    try {
      List<StepicWrappers.Unit> units =
        getFromStepik(EduStepicNames.UNITS + "/" + String.valueOf(unitId), StepicWrappers.UnitContainer.class).units;
      if (!units.isEmpty()) {
        return units.get(0);
      }
    }
    catch (IOException e) {
      LOG.warn("Failed getting unit: " + unitId);
    }
    return new StepicWrappers.Unit();
  }

  public static StepicWrappers.Section getSection(int sectionId) {
    try {
      List<StepicWrappers.Section> sections =
        getFromStepik(EduStepicNames.SECTIONS + "/" + String.valueOf(sectionId), StepicWrappers.SectionContainer.class).getSections();
      if (!sections.isEmpty()) {
        return sections.get(0);
      }
    }
    catch (IOException e) {
      LOG.warn("Failed getting section: " + sectionId);
    }
    return new StepicWrappers.Section();
  }

  public static Lesson getLesson(int lessonId) {
    try {
      List<Lesson> lessons =
        getFromStepik(EduStepicNames.LESSONS + "/" + String.valueOf(lessonId), StepicWrappers.LessonContainer.class).lessons;
      if (!lessons.isEmpty()) {
        return lessons.get(0);
      }
    }
    catch (IOException e) {
      LOG.warn("Failed getting section: " + lessonId);
    }
    return new Lesson();
  }
}
