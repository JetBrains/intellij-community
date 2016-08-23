package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
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
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
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
  public static final String PYTHON2 = "python2";
  public static final String PYTHON3 = "python3";
  public static final String PYCHARM_COMMENT = "# Posted from PyCharm Edu\n";
  private static final Logger LOG = Logger.getInstance(EduAdaptiveStepicConnector.class);
  private static final int CONNECTION_TIMEOUT = 60 * 1000;

  @Nullable
  public static Task getNextRecommendation(@NotNull final Project project, @NotNull Course course) {
    try {
      final CloseableHttpClient client = getHttpClient(project);
      final URI uri = new URIBuilder(EduStepicNames.STEPIC_API_URL + EduStepicNames.RECOMMENDATIONS_URL)
        .addParameter(EduNames.COURSE, String.valueOf(course.getId()))
        .build();
      final HttpGet request = new HttpGet(uri);
      setHeaders(request, EduStepicNames.CONTENT_TYPE_APPL_JSON);
      setTimeout(request);

      final CloseableHttpResponse response = client.execute(request);
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";

      final int statusCode = response.getStatusLine().getStatusCode();
      EntityUtils.consume(responseEntity);
      if (statusCode == HttpStatus.SC_OK) {
        final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        final StepicWrappers.RecommendationWrapper recomWrapper = gson.fromJson(responseString, StepicWrappers.RecommendationWrapper.class);

        if (recomWrapper.recommendations.length != 0) {
          final StepicWrappers.Recommendation recommendation = recomWrapper.recommendations[0];
          final String lessonId = recommendation.lesson;
          final StepicWrappers.LessonContainer
            lessonContainer = getFromStepic(EduStepicNames.LESSONS + lessonId, StepicWrappers.LessonContainer.class);
          if (lessonContainer.lessons.size() == 1) {
            final Lesson realLesson = lessonContainer.lessons.get(0);
            course.getLessons().get(0).setId(Integer.parseInt(lessonId));

            for (int stepId : realLesson.steps) {
              final StepicWrappers.StepSource step = getStep(stepId);
              if (step.block.name.equals("code")) {
                return getTaskFromStep(project, stepId, step.block, realLesson.getName());
              }
            }

            final StepicUser user = StudyTaskManager.getInstance(project).getUser();
            postRecommendationReaction(project, lessonId,
                                       String.valueOf(user.getId()), -1);
            return getNextRecommendation(project, course);
          }
          else {
            LOG.warn("Got unexpected number of lessons: " + lessonContainer.lessons.size());
          }
        }
      }
      else {
        if ((statusCode == HttpStatus.SC_BAD_REQUEST || statusCode == HttpStatus.SC_UNAUTHORIZED) && login(project)) {
          return getNextRecommendation(project, course);
        }
        throw new IOException("Stepic returned non 200 status code: " + responseString);
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());

      final String connectionMessages = "Connection problems, Please, try again";
      final Balloon balloon =
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(connectionMessages, MessageType.ERROR, null)
          .createBalloon();
      ApplicationManager.getApplication().invokeLater(() -> {
        if (StudyUtils.getSelectedEditor(project) != null) {
          StudyUtils.showCheckPopUp(project, balloon);
        }
      });
    }
    catch (URISyntaxException e) {
      LOG.warn(e.getMessage());
    }
    return null;
  }

  private static void setTimeout(HttpGet request) {
    final RequestConfig requestConfig = RequestConfig.custom()
      .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
      .setConnectTimeout(CONNECTION_TIMEOUT)
      .setSocketTimeout(CONNECTION_TIMEOUT)
      .build();
    request.setConfig(requestConfig);
  }

  private static void setTimeout(HttpPost request) {
    final RequestConfig requestConfig = RequestConfig.custom()
      .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
      .setConnectTimeout(CONNECTION_TIMEOUT)
      .setSocketTimeout(CONNECTION_TIMEOUT)
      .build();
    request.setConfig(requestConfig);
  }

  public static boolean postRecommendationReaction(@NotNull final Project project, @NotNull final String lessonId,
                                                   @NotNull final String user, int reaction) {
    final HttpPost post = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.RECOMMENDATION_REACTIONS_URL);
    final String json = new Gson()
      .toJson(new StepicWrappers.RecommendationReactionWrapper(new StepicWrappers.RecommendationReaction(reaction, user, lessonId)));
    post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    final CloseableHttpClient client = getHttpClient(project);
    setHeaders(post, EduStepicNames.CONTENT_TYPE_APPL_JSON);
    setTimeout(post);
    try {
      final CloseableHttpResponse execute = client.execute(post);
      final int statusCode = execute.getStatusLine().getStatusCode();
      final HttpEntity entity = execute.getEntity();
      final String entityString = EntityUtils.toString(entity);
      EntityUtils.consume(entity);
      if (statusCode == HttpStatus.SC_CREATED) {
        return true;
      }
      else {
        if ((statusCode == HttpStatus.SC_BAD_REQUEST || statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN)
            && login(project)) {
          return postRecommendationReaction(project, lessonId, user, reaction);
        }
        LOG.warn("Stepic returned non-201 status code: " + statusCode + " " + entityString);
        return false;
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    return false;
  }

  public static void addNextRecommendedTask(@NotNull final Project project, int reaction, ProgressIndicator indicator) {
    final StudyEditor editor = StudyUtils.getSelectedStudyEditor(project);
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null && editor != null && editor.getTaskFile() != null) {
      indicator.checkCanceled();
      final StepicUser user = StudyTaskManager.getInstance(project).getUser();

      final boolean recommendationReaction =
        postRecommendationReaction(project, String.valueOf(editor.getTaskFile().getTask().getLesson().getId()),
                                   String.valueOf(user.getId()), reaction);
      if (recommendationReaction) {
        indicator.checkCanceled();
        final Task task = getNextRecommendation(project, course);

        if (task != null) {
          final Lesson adaptive = course.getLessons().get(0);
          final Task unsolvedTask = adaptive.getTaskList().get(adaptive.getTaskList().size() - 1);
          final String lessonName = EduNames.LESSON + String.valueOf(adaptive.getIndex());
          if (reaction == 0 || reaction == -1) {
            unsolvedTask.setName(task.getName());
            unsolvedTask.setStepicId(task.getStepicId());
            unsolvedTask.setText(task.getText());
            unsolvedTask.getTestsText().clear();
            unsolvedTask.setStatus(StudyStatus.Unchecked);
            final Map<String, String> testsText = task.getTestsText();
            for (String testName : testsText.keySet()) {
              unsolvedTask.addTestsTexts(testName, testsText.get(testName));
            }
            final Map<String, TaskFile> taskFiles = task.getTaskFiles();
            if (taskFiles.size() == 1) {
              final TaskFile taskFile = editor.getTaskFile();
              taskFile.text = ((TaskFile)taskFiles.values().toArray()[0]).text;

              ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
                final Document document = editor.getEditor().getDocument();
                final String taskFileText = taskFiles.get(EduStepicNames.DEFAULT_TASKFILE_NAME).text;
                document.setText(taskFileText);
              }));
            }
            else {
              LOG.warn("Got task without unexpected number of task files: " + taskFiles.size());
            }

            final File lessonDirectory = new File(course.getCourseDirectory(), lessonName);
            final String taskName = EduNames.TASK + String.valueOf(adaptive.getTaskList().size());
            final File taskDirectory = new File(lessonDirectory, taskName);
            StudyProjectGenerator.flushTask(task, taskDirectory);
            StudyProjectGenerator.flushCourseJson(course, new File(course.getCourseDirectory()));
            final VirtualFile lessonDir = project.getBaseDir().findChild(lessonName);

            if (lessonDir != null) {
              createTestFiles(course, task, unsolvedTask, lessonDir);
            }
            final StudyToolWindow window = StudyUtils.getStudyToolWindow(project);
            if (window != null) {
              window.setTaskText(StudyUtils.wrapTextToDisplayLatex(unsolvedTask.getText()), unsolvedTask.getTaskDir(project), project);
            }
            StudyNavigator.navigateToTask(project, lessonName, taskName);
          }
          else {
            adaptive.addTask(task);
            task.setIndex(adaptive.getTaskList().size());
            final VirtualFile lessonDir = project.getBaseDir().findChild(lessonName);

            if (lessonDir != null) {
              ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
                try {
                  final File lessonDirectory = new File(course.getCourseDirectory(), lessonName);
                  final String taskName = EduNames.TASK + String.valueOf(task.getIndex());
                  final File taskDir = new File(lessonDirectory, taskName);
                  StudyProjectGenerator.flushTask(task, taskDir);
                  StudyProjectGenerator.flushCourseJson(course, new File(course.getCourseDirectory()));
                  StudyGenerator.createTask(task, lessonDir, new File(course.getCourseDirectory(), lessonDir.getName()), project);
                  adaptive.initLesson(course, true);
                  StudyNavigator.navigateToTask(project, lessonName, taskName);
                }
                catch (IOException e) {
                  LOG.warn(e.getMessage());
                }
              }));
            }
          }
        }
        ApplicationManager.getApplication().invokeLater(() -> {
          VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
          ProjectView.getInstance(project).refresh();
        });
      }
      else {
        LOG.warn("Recommendation reactions weren't posted");
        ApplicationManager.getApplication().invokeLater(() -> StudyUtils.showErrorPopupOnToolbar(project));
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
  private static Task getTaskFromStep(Project project, int lessonID, @NotNull final StepicWrappers.Step step, @NotNull String name) {
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

    task.taskFiles = new HashMap<>();      // TODO: it looks like we don't need taskFiles as map anymore
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
      attemptId = getAttemptId(project, task);
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
        new StepicWrappers.SubmissionToPostWrapper(String.valueOf(attemptId), language, PYCHARM_COMMENT + text);
      final HttpPost httpPost = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.SUBMISSIONS);
      setHeaders(httpPost, EduStepicNames.CONTENT_TYPE_APPL_JSON);
      setTimeout(httpPost);
      try {
        httpPost.setEntity(new StringEntity(new Gson().toJson(submissionToPostWrapper)));
      }
      catch (UnsupportedEncodingException e) {
        LOG.warn(e.getMessage());
      }
      response = client.execute(httpPost);
      final HttpEntity entity = response.getEntity();
      final String entityString = EntityUtils.toString(entity);
      EntityUtils.consume(entity);
      return new Gson().fromJson(entityString, StepicWrappers.ResultSubmissionWrapper.class);
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
        final URI submissionURI = new URIBuilder(EduStepicNames.STEPIC_API_URL + EduStepicNames.SUBMISSIONS)
          .addParameter("attempt", String.valueOf(attemptId))
          .addParameter("order", "desc")
          .addParameter("user", String.valueOf(id))
          .build();
        final HttpGet httpGet = new HttpGet(submissionURI);
        setHeaders(httpGet, EduStepicNames.CONTENT_TYPE_APPL_JSON);
        setTimeout(httpGet);
        final CloseableHttpResponse httpResponse = client.execute(httpGet);
        final HttpEntity entity = httpResponse.getEntity();
        final String entityString = EntityUtils.toString(entity);
        EntityUtils.consume(entity);
        wrapper = new Gson().fromJson(entityString, StepicWrappers.ResultSubmissionWrapper.class);
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
            return versionStringParts.get(1).startsWith("2") ? PYTHON2 : PYTHON3;
          }
        }
      }
      else {
        StudyUtils.showNoSdkNotification(task, project);
      }
    }
    return null;
  }

  private static int getAttemptId(@NotNull final Project project, @NotNull Task task) throws IOException {
    final StepicWrappers.AttemptToPostWrapper attemptWrapper = new StepicWrappers.AttemptToPostWrapper(task.getStepicId());

    final HttpPost post = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ATTEMPTS);
    post.setEntity(new StringEntity(new Gson().toJson(attemptWrapper)));

    final CloseableHttpClient client = getHttpClient(project);
    setHeaders(post, EduStepicNames.CONTENT_TYPE_APPL_JSON);
    setTimeout(post);
    final CloseableHttpResponse httpResponse = client.execute(post);
    final int statusCode = httpResponse.getStatusLine().getStatusCode();
    final HttpEntity entity = httpResponse.getEntity();
    final String entityString = EntityUtils.toString(entity);
    EntityUtils.consume(entity);
    if (statusCode == HttpStatus.SC_CREATED) {
      final StepicWrappers.AttemptContainer container =
        new Gson().fromJson(entityString, StepicWrappers.AttemptContainer.class);
      return (container.attempts != null && !container.attempts.isEmpty()) ? container.attempts.get(0).id : -1;
    }
    else {
      if ((statusCode == HttpStatus.SC_BAD_REQUEST || statusCode == HttpStatus.SC_UNAUTHORIZED) && login(project)) {
        return getAttemptId(project, task);
      }
    }
    return -1;
  }

  private static void createTestFileFromSamples(@NotNull final Task task,
                                                @NotNull final List<List<String>> samples) {

    String testText = "from test_helper import check_samples\n\n" +
                      "if __name__ == '__main__':\n" +
                      "    check_samples(samples=" + new GsonBuilder().create().toJson(samples) + ")";
    task.addTestsTexts("tests.py", testText);
  }
}
