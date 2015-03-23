package com.jetbrains.edu.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.ssl.CertificateManager;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

public class EduStepicConnector {
  private static final String stepicApiUrl = "https://stepic.org/api/";
  private static final Logger LOG = Logger.getInstance(EduStepicConnector.class.getName());
  private static final String ourDomain = "stepic.org";
  private static String ourSessionId = "524iethiwju2tjywaqmf7tbwx0p0jk1b";
  private static String ourCSRFToken = "LJ9n6OyLVA7hxU94dlYWUu65MF51Nx37";
  //this prefix indicates that course can be opened by educational plugin
  public static final String PYCHARM_PREFIX = "pycharm ";

  private EduStepicConnector() {
  }

  private static <T> T getFromStepic(String link, final Class<T> container) throws IOException {
    return HttpRequests.request(stepicApiUrl + link).connect(new HttpRequests.RequestProcessor<T>() {
      @Override
      public T process(@NotNull HttpRequests.Request request) throws IOException {
        final BufferedReader reader = request.getReader();
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        return gson.fromJson(reader, container);
      }
    });
  }

  @NotNull
  public static List<CourseInfo> getCourses() {
    try {
      return getFromStepic("courses/99", CoursesContainer.class).courses;
    }
    catch (IOException e) {
      LOG.error("IOException " + e.getMessage());
    }
    return Collections.emptyList();
    /*try {                             // TODO: uncomment
      return HttpRequests.request(stepicApiUrl + "courses").connect(new HttpRequests.RequestProcessor<List<CourseInfo>>() {

        @Override
        public List<CourseInfo> process(@NotNull HttpRequests.Request request) throws IOException {
          final BufferedReader reader = request.getReader();
          Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
          return gson.fromJson(reader, CoursesContainer.class).courses;
        }
      });
    }
    catch (IOException e) {
      LOG.error("IOException " + e.getMessage());
    }
    return null;*/
  }

  public static Course getCourse(@NotNull final CourseInfo info) {
    final Course course = new Course();
    course.setAuthor(info.getAuthor());
    course.setDescription(info.getDescription());
    course.setName(info.getName());
    String courseType = info.getType();
    course.setLanguage(courseType.substring(PYCHARM_PREFIX.length()));
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
    final SectionWrapper sectionWrapper = getFromStepic("sections/" + String.valueOf(sectionId), SectionWrapper.class);
    List<Integer> unitIds = sectionWrapper.sections.get(0).units;
    final List<Lesson> lessons = new ArrayList<Lesson>();
    for (Integer unitId : unitIds) {
      UnitWrapper unit = getFromStepic("units/" + String.valueOf(unitId), UnitWrapper.class);
      int lessonID = unit.units.get(0).lesson;
      LessonContainer lesson = getFromStepic("lessons/" + String.valueOf(lessonID), LessonContainer.class);
      Lesson realLesson = lesson.lessons.get(0);
      lessons.add(realLesson);
    }

    for (Lesson lesson : lessons) {
      lesson.taskList = new ArrayList<Task>();
      for (Integer s : lesson.steps) {
        createTask(lesson, s);
      }
    }
    return lessons;
  }

  private static void createTask(Lesson lesson, Integer s) throws IOException {
    final Step step = getStep(s);
    final Task task = new Task();
    task.setName(step.name);
    task.setText(step.text);
    for (TestFileWrapper wrapper : step.options.test) {
      task.setTestsTexts(wrapper.name, wrapper.text);
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


  public static boolean postLesson(Project project, @NotNull final Lesson lesson) {
    final HttpPost request = new HttpPost(stepicApiUrl + "lessons");
    final ArrayList<Header> headers = getHeaders(request);
    HttpClientBuilder builder = HttpClients.custom().setSslcontext(CertificateManager.getInstance().getSslContext());
    final BasicCookieStore cookieStore = getCookies();
    final CloseableHttpClient client = builder.setDefaultHeaders(headers).setDefaultCookieStore(cookieStore).build();

    String requestBody = new Gson().toJson(new LessonWrapper(lesson));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = client.execute(request);
      final String responseString = EntityUtils.toString(response.getEntity());
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != 201) {
        LOG.error("Failed to push " + EntityUtils.toString(response.getEntity()));
        return false;
      }
      final Lesson postedLesson = new Gson().fromJson(responseString, Course.class).getLessons().get(0);
      for (Task task : lesson.getTaskList()) {
        postTask(project, task, postedLesson.id);
      }
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
    return false;
  }

  public static boolean postTask(Project project, @NotNull final Task task, int id) {
    final HttpPost request = new HttpPost(stepicApiUrl + "step-sources");
    final ArrayList<Header> headers = getHeaders(request);
    HttpClientBuilder builder = HttpClients.custom().setSslcontext(CertificateManager.getInstance().getSslContext());
    final BasicCookieStore cookieStore = getCookies();
    final CloseableHttpClient client = builder.setDefaultHeaders(headers).setDefaultCookieStore(cookieStore).build();
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    String requestBody = gson.toJson(new StepSourceWrapper(project, task, id));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse response = client.execute(request);
      final StatusLine line = response.getStatusLine();
      if (line.getStatusCode() != 201) {
        LOG.error("Failed to push " + EntityUtils.toString(response.getEntity()));
      }
      return line.getStatusCode() == 201;
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }

    return false;
  }

  private static BasicCookieStore getCookies() {
    final BasicCookieStore cookieStore = new BasicCookieStore();
    final BasicClientCookie sessionid = new BasicClientCookie("sessionid", ourSessionId);
    sessionid.setDomain(ourDomain);
    sessionid.setPath("/");

    cookieStore.addCookie(sessionid);
    final BasicClientCookie csrfToken = new BasicClientCookie("csrftoken", ourCSRFToken);
    csrfToken.setDomain(ourDomain);
    csrfToken.setPath("/");

    cookieStore.addCookie(csrfToken);
    return cookieStore;
  }

  private static ArrayList<Header> getHeaders(HttpPost request) {
    final ArrayList<Header> headers = new ArrayList<Header>();
    headers.add(new BasicHeader("referer", "https://stepic.org"));
    headers.add(new BasicHeader("X-CSRFToken", ourCSRFToken));
    headers.add(new BasicHeader("content-type", "application/json"));
    request.setHeaders(headers.toArray(new Header[headers.size()]));
    return headers;
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
    @Expose String title;  //HERE
    @Expose List<TaskFile> files;
    @Expose String text;

    public static StepOptions fromTask(final Project project, @NotNull final Task task) {
      final StepOptions source = new StepOptions();

      final String text = task.getTestsText(project);
      source.test = Collections.singletonList(new TestFileWrapper("tests.py", text));
      source.files = new ArrayList<TaskFile>();
      source.title = task.getName();
      for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            final VirtualFile taskDir = task.getTaskDir(project);
            EduUtils.createStudentFileFromAnswer(project, taskDir, taskDir, entry);
          }
        });
        final TaskFile taskFile = entry.getValue();
        taskFile.name = entry.getKey();
        final Document document = task.getDocument(project, taskFile.name);
        if (document != null) {
          source.text = document.getImmutableCharSequence().toString();
          taskFile.text = document.getImmutableCharSequence().toString();
        }
        source.files.add(taskFile);
      }
      return source;
    }
  }

  private static class CoursesContainer {
    public List<CourseInfo> courses;
  }

  static class StepSourceWrapper {
    @Expose
    StepSource stepSource;

    public StepSourceWrapper(Project project, Task task, int id) {
      stepSource = new StepSource(project, task, id);
    }
  }

  static class LessonWrapper {
    Lesson lesson;

    public LessonWrapper(Lesson lesson) {
      this.lesson = new Lesson();
      this.lesson.setName(lesson.getName());
    }
  }

  static class LessonContainer {
    List<Lesson> lessons;
  }

  static class StepSource {
    @Expose Step block;
    @Expose int position = 0;
    @Expose int lesson = 0;

    public StepSource(Project project, Task task, int id) {
      lesson = id;
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

  static class SectionWrapper {
    static class Section {
      List<Integer> units;
    }

    List<Section> sections;
    List<Lesson> lessons;

    static class Unit {
      int id;
      int lesson;
    }

    List<Unit> units;
  }

  static class UnitWrapper {
    static class Unit {
      int lesson;
    }

    List<Unit> units;
  }
}
