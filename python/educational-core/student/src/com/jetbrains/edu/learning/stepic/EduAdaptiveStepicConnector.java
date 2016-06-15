package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.checker.StudyExecutor;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.jetbrains.edu.learning.stepic.EduStepicConnector.*;

public class EduAdaptiveStepicConnector {
  public static final String PYTHON27 = "python27";
  public static final String PYTHON3 = "python3";

  private static final Logger LOG = Logger.getInstance(EduAdaptiveStepicConnector.class);
  private static final String STEPIC_URL = "https://stepic.org/";
  private static final String STEPIC_API_URL = STEPIC_URL + "api/";
  private static final String RECOMMENDATIONS_URL = "recommendations";
  private static final String CONTENT_TYPE_APPL_JSON = "application/json";
  private static final String LESSON_URL = "lessons/";
  private static final String RECOMMENDATION_REACTIONS_URL = "recommendation-reactions";
  private static final String ATTEMPTS_URL = "attempts";
  private static final String SUBMISSION_URL = "submissions";
  private static final String ASSIGNMENT_URL = "/assignments";
  private static final String VIEWS_URL = "/views";
  private static final String UNITS_URL = "/units";
  private static final String DEFAULT_TASKFILE_NAME = "code.py";

  @Nullable
  public static Task getNextRecommendation(@NotNull final Project project, @NotNull Course course) {
    try {
      final CloseableHttpClient client = getHttpClient(project);
      final URI uri = new URIBuilder(STEPIC_API_URL + RECOMMENDATIONS_URL)
        .addParameter("course", String.valueOf(course.getId()))
        .build();
      final HttpGet request = new HttpGet(uri);
      setHeaders(request, CONTENT_TYPE_APPL_JSON);

      final CloseableHttpResponse response = client.execute(request);
      final StatusLine statusLine = response.getStatusLine();
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";

      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        final StepicWrappers.RecommendationWrapper recomWrapper = gson.fromJson(responseString, StepicWrappers.RecommendationWrapper.class);

        if (recomWrapper.recommendations.length != 0) {
          final StepicWrappers.Recommendation recommendation = recomWrapper.recommendations[0];
          final String lessonId = recommendation.lesson;
          final StepicWrappers.LessonContainer
            lessonContainer = getFromStepic(LESSON_URL + lessonId, StepicWrappers.LessonContainer.class);
          if (lessonContainer.lessons.size() == 1) {
            final Lesson realLesson = lessonContainer.lessons.get(0);
            course.getLessons().get(0).id = Integer.parseInt(lessonId);

            viewAllSteps(client, realLesson.id);

            for (int stepId : realLesson.steps) {
              final StepicWrappers.Step step = getStep(stepId);
              if (step.name.equals("code")) {
                return getTaskFromStep(project, stepId, step, realLesson.getName());
              }
            }

            LOG.warn("Got a lesson without code part as a recommendation");
          }
          else {
            LOG.warn("Got unexpected number of lessons: " + lessonContainer.lessons.size());
          }
        }
      }
      else {
        throw new IOException("Stepic returned non 200 status code: " + responseString);
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    catch (URISyntaxException e) {
      LOG.warn(e.getMessage());
    }
    return null;
  }

  private static void viewAllSteps(CloseableHttpClient client, int lessonId) throws URISyntaxException, IOException {
    final URI unitsUrl = new URIBuilder(UNITS_URL).addParameter("lesson", String.valueOf(lessonId)).build();
    final StepicWrappers.UnitContainer unitContainer = getFromStepic(unitsUrl.toString(), StepicWrappers.UnitContainer.class);
    if (unitContainer.units.size() != 1) {
      LOG.warn("Got unexpected numbers of units: " + unitContainer.units.size());
      return;
    }

    final URIBuilder builder = new URIBuilder(ASSIGNMENT_URL);
    for (Integer step : unitContainer.units.get(0).assignments) {
      builder.addParameter("ids[]", String.valueOf(step));
    }
    final URI assignmentUrl = builder.build();
    final StepicWrappers.AssignmentsWrapper assignments = getFromStepic(assignmentUrl.toString(), StepicWrappers.AssignmentsWrapper.class);
    if (assignments.assignments.size() > 0) {
      for (StepicWrappers.Assignment assignment : assignments.assignments) {
        final HttpPost post = new HttpPost(STEPIC_API_URL + VIEWS_URL);
        final StepicWrappers.ViewsWrapper viewsWrapper = new StepicWrappers.ViewsWrapper(assignment.id, assignment.step);
        post.setEntity(new StringEntity(new Gson().toJson(viewsWrapper)));
        setHeaders(post, CONTENT_TYPE_APPL_JSON);
        final CloseableHttpResponse viewPostResult = client.execute(post);
        if (viewPostResult.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
          LOG.warn("Error while Views post, code: " + viewPostResult.getStatusLine().getStatusCode());
        }
      }
    }
    else {
      LOG.warn("Got assignments of incorrect length: " + assignments.assignments.size());
    }
  }

  public static boolean postRecommendationReaction(@NotNull final Project project, @NotNull final String lessonId,
                                                   @NotNull final String user, int reaction) {
    final HttpPost post = new HttpPost(STEPIC_API_URL + RECOMMENDATION_REACTIONS_URL);
    final String json = new Gson()
      .toJson(new StepicWrappers.RecommendationReactionWrapper(new StepicWrappers.RecommendationReaction(reaction, user, lessonId)));
    post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    final CloseableHttpClient client = getHttpClient(project);
    setHeaders(post, CONTENT_TYPE_APPL_JSON);
    try {
      final CloseableHttpResponse execute = client.execute(post);
      return execute.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED;
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    return false;
  }

  public static void addNextRecommendedTask(@NotNull final Project project, int reaction) {
    final StudyEditor editor = StudyUtils.getSelectedStudyEditor(project);
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null && editor != null && editor.getTaskFile() != null) {
      final StepicUser user = StudyTaskManager.getInstance(project).getUser();

      final boolean recommendationReaction =
        user != null && postRecommendationReaction(project, String.valueOf(editor.getTaskFile().getTask().getLesson().id),
                                                   String.valueOf(user.id), reaction);
      if (recommendationReaction) {
        final Task task = getNextRecommendation(project, course);

        if (task != null) {
          final Lesson adaptive = course.getLessons().get(0);
          final Task unsolvedTask = adaptive.getTaskList().get(adaptive.getTaskList().size() - 1);
          if (reaction == 0 || reaction == -1) {
            unsolvedTask.setName(task.getName());
            unsolvedTask.setStepicId(task.getStepicId());
            unsolvedTask.setText(task.getText());
            unsolvedTask.getTestsText().clear();
            final Map<String, String> testsText = task.getTestsText();
            for (String testName : testsText.keySet()) {
              unsolvedTask.addTestsTexts(testName, testsText.get(testName));
            }
            final Map<String, TaskFile> taskFiles = task.getTaskFiles();
            if (taskFiles.size() == 1) {
              final TaskFile taskFile = editor.getTaskFile();
              taskFile.text = ((TaskFile)taskFiles.values().toArray()[0]).text;
              ApplicationManager.getApplication().invokeLater(() ->
                                                                ApplicationManager.getApplication().runWriteAction(() ->
                                                                                                                     editor.getEditor()
                                                                                                                       .getDocument()
                                                                                                                       .setText(
                                                                                                                         taskFiles.get(
                                                                                                                           DEFAULT_TASKFILE_NAME).text)));
            }
            else {
              LOG.warn("Got task without unexpected number of task files: " + taskFiles.size());
            }

            final File lessonDirectory = new File(course.getCourseDirectory(), EduNames.LESSON + String.valueOf(adaptive.getIndex()));
            final File taskDirectory = new File(lessonDirectory, EduNames.TASK + String.valueOf(adaptive.getTaskList().size()));
            StudyProjectGenerator.flushTask(task, taskDirectory);
            StudyProjectGenerator.flushCourseJson(course, new File(course.getCourseDirectory()));
            final VirtualFile lessonDir = project.getBaseDir().findChild(EduNames.LESSON + String.valueOf(adaptive.getIndex()));

            if (lessonDir != null) {
              createTestFiles(course, task, unsolvedTask, lessonDir);
            }
            final StudyToolWindow window = StudyUtils.getStudyToolWindow(project);
            if (window != null) {
              window.setTaskText(unsolvedTask.getText(), unsolvedTask.getTaskDir(project), project);
            }
          }
          else {
            adaptive.addTask(task);
            task.setIndex(adaptive.getTaskList().size());
            final VirtualFile lessonDir = project.getBaseDir().findChild(EduNames.LESSON + String.valueOf(adaptive.getIndex()));

            if (lessonDir != null) {
              ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
                try {
                  StudyGenerator.createTask(task, lessonDir, new File(course.getCourseDirectory(), lessonDir.getName()), project);
                }
                catch (IOException e) {
                  LOG.warn(e.getMessage());
                }
              }));
            }

            final File lessonDirectory = new File(course.getCourseDirectory(), EduNames.LESSON + String.valueOf(adaptive.getIndex()));
            StudyProjectGenerator.flushLesson(lessonDirectory, adaptive);
            StudyProjectGenerator.flushCourseJson(course, new File(course.getCourseDirectory()));
            adaptive.initLesson(course, true);
          }
        }
        ApplicationManager.getApplication().invokeLater(() -> {
          VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
          ProjectView.getInstance(project).refresh();
        });
      }
      else {
        LOG.warn("Recommendation reactions weren't posted");
      }
    }
  }

  private static void createTestFiles(Course course, Task task, Task unsolvedTask, VirtualFile lessonDir) {
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final VirtualFile taskDir = VfsUtil
          .findFileByIoFile(new File(lessonDir.getCanonicalPath(), EduNames.TASK + unsolvedTask.getIndex()), true);
        final File resourceRoot = new File(course.getCourseDirectory(), lessonDir.getName());
        File newResourceRoot = null;
        if (taskDir != null) {
          newResourceRoot = new File(resourceRoot, taskDir.getName());
          File[] filesInTask = newResourceRoot.listFiles();
          if (filesInTask != null) {
            for (File file : filesInTask) {
              String fileName = file.getName();
              if (!task.isTaskFile(fileName)) {
                File resourceFile = new File(newResourceRoot, fileName);
                File fileInProject = new File(taskDir.getCanonicalPath(), fileName);
                FileUtil.copy(resourceFile, fileInProject);
              }
            }
          }
        }
        else {
          LOG.warn("Task directory is null");
        }
      }
      catch (IOException e) {
        LOG.warn(e.getMessage());
      }
    }));
  }

  @NotNull
  private static Task getTaskFromStep(Project project,
                                      int lessonID,
                                      @NotNull final StepicWrappers.Step step, @NotNull String name) {
    final Task task = new Task();
    task.setName(name);
    task.setStepicId(lessonID);
    task.setText(step.text);
    task.setStatus(StudyStatus.Unchecked);
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
      task.setText(task.getText() + "<br>" + builder.toString());
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

    if (step.options.test != null) {
      for (StepicWrappers.TestFileWrapper wrapper : step.options.test) {
        task.addTestsTexts(wrapper.name, wrapper.text);
      }
    }
    else {
      if (step.options.samples != null) {
        createTestFileFromSamples(task, step.options.samples);
      }
    }

    task.taskFiles = new HashMap<String, TaskFile>();      // TODO: it looks like we don't need taskFiles as map anymore
    if (step.options.files != null) {
      for (TaskFile taskFile : step.options.files) {
        task.taskFiles.put(taskFile.name, taskFile);
      }
    }
    else {
      final TaskFile taskFile = new TaskFile();
      taskFile.name = "code";
      final String templateForTask = getCodeTemplateForTask(step.options.codeTemplates, task, project);
      taskFile.text = templateForTask == null ? "# write your answer here \n" : templateForTask;
      task.taskFiles.put("code.py", taskFile);
    }
    return task;
  }

  private static String getCodeTemplateForTask(@Nullable StepicWrappers.CodeTemplatesWrapper codeTemplates,
                                               @NotNull final Task task, @NotNull final Project project) {

    if (codeTemplates != null) {
      final String languageString = getLanguageString(task, project);
      if (languageString != null) {
        return codeTemplates.getTemplateForLanguage(languageString);
      }
    }

    return null;
  }

  @Nullable
  public static Pair<Boolean, String> checkTask(@NotNull final Project project, @NotNull final Task task) {
    int attemptId = -1;
    try {
      attemptId = getAttemptId(project, task, ATTEMPTS_URL);
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    if (attemptId != -1) {
      final Editor editor = StudyUtils.getSelectedEditor(project);
      String language = getLanguageString(task, project);
      if (editor != null && language != null) {
        final CloseableHttpClient client = getHttpClient(project);
        StepicWrappers.ResultSubmissionWrapper wrapper = postResultsForCheck(client, attemptId, language, editor.getDocument().getText());

        final StepicUser user = StudyTaskManager.getInstance(project).getUser();
        if (user != null) {
          final int id = user.getId();
          wrapper = getCheckResults(attemptId, id, client, wrapper);
          if (wrapper.submissions.length == 1) {
            final boolean isSolved = !wrapper.submissions[0].status.equals("wrong");
            return Pair.create(isSolved, wrapper.submissions[0].hint);
          }
          else {
            LOG.warn("Got a submission wrapper with incorrect submissions number: " + wrapper.submissions.length);
          }
        }
        else {
          LOG.warn("User is null");
        }
      }
    }
    else {
      LOG.warn("Got an incorrect attempt id: " + attemptId);
    }
    return Pair.create(false, "");
  }

  @Nullable
  private static StepicWrappers.ResultSubmissionWrapper postResultsForCheck(@NotNull final CloseableHttpClient client,
                                                                            final int attemptId,
                                                                            @NotNull final String language,
                                                                            @NotNull final String text) {
    final CloseableHttpResponse response;
    try {
      final StepicWrappers.SubmissionToPostWrapper submissionToPostWrapper =
        new StepicWrappers.SubmissionToPostWrapper(String.valueOf(attemptId), language, text);
      final HttpPost httpPost = new HttpPost(STEPIC_API_URL + SUBMISSION_URL);
      setHeaders(httpPost, CONTENT_TYPE_APPL_JSON);
      try {
        httpPost.setEntity(new StringEntity(new Gson().toJson(submissionToPostWrapper)));
      }
      catch (UnsupportedEncodingException e) {
        LOG.warn(e.getMessage());
      }
      response = client.execute(httpPost);
      return new Gson().fromJson(EntityUtils.toString(response.getEntity()), StepicWrappers.ResultSubmissionWrapper.class);
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    return null;
  }

  @NotNull
  private static StepicWrappers.ResultSubmissionWrapper getCheckResults(int attemptId,
                                                                        int id,
                                                                        CloseableHttpClient client,
                                                                        StepicWrappers.ResultSubmissionWrapper wrapper) {
    try {
      while (wrapper.submissions.length == 1 && wrapper.submissions[0].status.equals("evaluation")) {
        TimeUnit.MILLISECONDS.sleep(500);
        final URI submissionURI = new URIBuilder(STEPIC_API_URL + SUBMISSION_URL)
          .addParameter("attempt", String.valueOf(attemptId))
          .addParameter("order", "desc")
          .addParameter("user", String.valueOf(id))
          .build();
        final HttpGet httpGet = new HttpGet(submissionURI);
        setHeaders(httpGet, CONTENT_TYPE_APPL_JSON);
        final CloseableHttpResponse httpResponse = client.execute(httpGet);
        final String entity = EntityUtils.toString(httpResponse.getEntity());
        wrapper = new Gson().fromJson(entity, StepicWrappers.ResultSubmissionWrapper.class);
      }
    }
    catch (InterruptedException e) {
      LOG.warn(e.getMessage());
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    catch (URISyntaxException e) {
      LOG.warn(e.getMessage());
    }
    return wrapper;
  }

  @Nullable
  private static String getLanguageString(@NotNull Task task, @NotNull Project project) {
    final Language pythonLanguage = Language.findLanguageByID("Python");
    if (pythonLanguage != null) {
      final Sdk language = StudyExecutor.INSTANCE.forLanguage(pythonLanguage).findSdk(project);
      if (language != null) {
        final String versionString = language.getVersionString();
        if (versionString != null) {
          final List<String> versionStringParts = StringUtil.split(versionString, " ");
          if (versionStringParts.size() == 2) {
            return versionStringParts.get(1).startsWith("2") ? PYTHON27 : PYTHON3;
          }
        }
      }
      else {
        StudyUtils.showNoSdkNotification(task, project);
      }
    }
    return null;
  }

  private static int getAttemptId(@NotNull final Project project, @NotNull Task task, @NotNull final String attempts) throws IOException {
    final StepicWrappers.AttemptToPostWrapper attemptWrapper = new StepicWrappers.AttemptToPostWrapper(task.getStepicId());

    final HttpPost post = new HttpPost(STEPIC_API_URL + attempts);
    post.setEntity(new StringEntity(new Gson().toJson(attemptWrapper)));

    final CloseableHttpClient client = getHttpClient(project);
    setHeaders(post, CONTENT_TYPE_APPL_JSON);
    final CloseableHttpResponse httpResponse = client.execute(post);
    final String entity = EntityUtils.toString(httpResponse.getEntity());
    final StepicWrappers.AttemptContainer container =
      new Gson().fromJson(entity, StepicWrappers.AttemptContainer.class);
    return (container.attempts != null && !container.attempts.isEmpty()) ? container.attempts.get(0).id : -1;
  }

  private static void createTestFileFromSamples(@NotNull final Task task,
                                                @NotNull final List<List<String>> samples) {

    String testText = "from test_helper import check_samples\n\n" +
                      "if __name__ == '__main__':\n" +
                      "    check_samples(samples=" + new GsonBuilder().create().toJson(samples) + ")";
    task.addTestsTexts("tests.py", testText);
  }
}
