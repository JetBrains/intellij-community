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
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.jetbrains.edu.learning.stepic.EduStepicConnector.getStep;

public class EduAdaptiveStepicConnector {
  public static final String PYTHON2 = "python2";
  public static final String PYTHON3 = "python3";
  public static final String PYCHARM_COMMENT = "# Posted from PyCharm Edu\n";
  private static final Logger LOG = Logger.getInstance(EduAdaptiveStepicConnector.class);
  private static final int CONNECTION_TIMEOUT = 60 * 1000;
  private static final String CODE_TASK_TYPE = "code";
  private static final String CHOICE_TYPE_TEXT = "choice";

  @Nullable
  public static Task getNextRecommendation(@NotNull Project project, @NotNull Course course) {
    try {
      final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
      if (client == null) {
        LOG.warn("Http client is null");
        return null;
      }
      final URI uri = new URIBuilder(EduStepicNames.STEPIC_API_URL + EduStepicNames.RECOMMENDATIONS_URL)
        .addParameter(EduNames.COURSE, String.valueOf(course.getId()))
        .build();
      final HttpGet request = new HttpGet(uri);
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
          final StepicWrappers.LessonContainer lessonContainer = EduStepicAuthorizedClient.getFromStepic(EduStepicNames.LESSONS + lessonId,
                                                                                                         StepicWrappers.LessonContainer.class);
          if (lessonContainer != null && lessonContainer.lessons.size() == 1) {
            final Lesson realLesson = lessonContainer.lessons.get(0);
            course.getLessons().get(0).setId(Integer.parseInt(lessonId));

            for (int stepId : realLesson.steps) {
              final Task taskFromStep = getTask(project, realLesson.getName(), stepId);
              if (taskFromStep != null) return taskFromStep;
            }

            final StepicUser user = StepicUpdateSettings.getInstance().getUser();
            postRecommendationReaction(lessonId,
                                       String.valueOf(user.getId()), -1);
            return getNextRecommendation(project, course);
          }
          else {
            LOG.warn("Got unexpected number of lessons: " + (lessonContainer == null ? null : lessonContainer.lessons.size()));
          }
        }
        else {
          LOG.warn("Got empty recommendation for the task: " + responseString);
        }
      }
      else {
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

  @Nullable
  private static Task getTask(@NotNull Project project, @NotNull String name, int stepId) throws IOException {
    final StepicWrappers.StepSource step = getStep(stepId);
    final String stepType = step.block.name;
    if (stepType.equals(CODE_TASK_TYPE)) {
      return getCodeTaskFromStep(project, step.block, name, stepId);
    }
    else if (stepType.equals(CHOICE_TYPE_TEXT)) {
      return getChoiceTaskFromStep(name, step.block, stepId);
    }
    else if (stepType.startsWith(EduStepicConnector.PYCHARM_PREFIX)) {
      return EduStepicConnector.createTask(stepId);
    }

    return null;
  }

  private static Task getChoiceTaskFromStep(@NotNull String lessonName,
                                            @NotNull StepicWrappers.Step block,
                                            int stepId) {
    final Task task = Task.createChoiceTask(lessonName);
    task.setStepId(stepId);
    task.setText(block.text);

    final StepicWrappers.AdaptiveAttemptWrapper.Attempt attempt = getAttemptForStep(stepId);
    if (attempt != null) {
      final StepicWrappers.AdaptiveAttemptWrapper.Dataset dataset = attempt.dataset;
      if (dataset != null) {
        task.setChoiceVariants(dataset.options);
        task.setMultipleChoice(dataset.is_multiple_choice);
      }
      else {
        LOG.warn("Dataset for step " + stepId + " is null");
      }
    }

    createMockTaskFileForChoiceProblem(task);
    return task;
  }

  private static void createMockTaskFileForChoiceProblem(@NotNull Task task) {
    final TaskFile taskFile = new TaskFile();
    taskFile.text = "# you can experiment here, it won't be checked";
    taskFile.name = "code";
    task.taskFiles.put("code.py", taskFile);
  }

  @Nullable
  private static StepicWrappers.AdaptiveAttemptWrapper.Attempt getAttemptForStep(int id) {
    final StepicUser user = StepicUpdateSettings.getInstance().getUser();
    try {
      final List<StepicWrappers.AdaptiveAttemptWrapper.Attempt> attempts = getAttempts(user, id);
      if (attempts != null && attempts.size() > 0) {
        final StepicWrappers.AdaptiveAttemptWrapper.Attempt attempt = attempts.get(0);
        return attempt.isActive() ? attempt : createNewAttempt(id);
      }
      else {
        return createNewAttempt(id);
      }
    }
    catch (URISyntaxException | IOException e) {
      LOG.warn(e.getMessage());
    }
    return null;
  }

  private static StepicWrappers.AdaptiveAttemptWrapper.Attempt createNewAttempt(int id) throws IOException {
    final String response = EduStepicConnector.postAttempt(id);
    final StepicWrappers.AdaptiveAttemptContainer attempt = new Gson().fromJson(response, StepicWrappers.AdaptiveAttemptContainer.class);
    return attempt.attempts.get(0);
  }

  private static List<StepicWrappers.AdaptiveAttemptWrapper.Attempt> getAttempts(@NotNull StepicUser user, int id)
    throws URISyntaxException, IOException {
    final URI attemptUrl = new URIBuilder(EduStepicNames.ATTEMPTS)
      .addParameter("step", String.valueOf(id))
      .addParameter("user", String.valueOf(user.getId()))
      .build();
    final StepicWrappers.AdaptiveAttemptContainer attempt =
      EduStepicAuthorizedClient.getFromStepic(attemptUrl.toString(), StepicWrappers.AdaptiveAttemptContainer.class, user);
    return attempt.attempts;
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

  public static boolean postRecommendationReaction(@NotNull String lessonId,
                                                   @NotNull String user,
                                                   int reaction) {
    final HttpPost post = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.RECOMMENDATION_REACTIONS_URL);
    final String json = new Gson()
      .toJson(new StepicWrappers.RecommendationReactionWrapper(new StepicWrappers.RecommendationReaction(reaction, user, lessonId)));
    post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
    if (client == null) return false;
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
        LOG.warn("Stepic returned non-201 status code: " + statusCode + " " + entityString);
        return false;
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    return false;
  }

  public static void addNextRecommendedTask(@NotNull Project project,
                                            @NotNull ProgressIndicator indicator,
                                            int reaction) {
    final StudyEditor editor = StudyUtils.getSelectedStudyEditor(project);
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course != null && editor != null && editor.getTaskFile() != null) {
      indicator.checkCanceled();
      final StepicUser user = StepicUpdateSettings.getInstance().getUser();

      final boolean recommendationReaction =
        postRecommendationReaction(String.valueOf(editor.getTaskFile().getTask().getLesson().getId()),
                                   String.valueOf(user.getId()), reaction);
      if (recommendationReaction) {
        indicator.checkCanceled();
        final Task task = getNextRecommendation(project, course);

        if (task != null) {
          final Lesson adaptive = course.getLessons().get(0);
          final Task unsolvedTask = adaptive.getTaskList().get(adaptive.getTaskList().size() - 1);
          final String lessonName = EduNames.LESSON + String.valueOf(adaptive.getIndex());
          if (reaction == 0 || reaction == -1) {
            unsolvedTask.copyParametersOf(task);

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
        else {
          ApplicationManager.getApplication().invokeLater(() -> {
            final Balloon balloon =
              JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("Couldn't load a new recommendation", MessageType.ERROR, null)
                .createBalloon();
            StudyUtils.showCheckPopUp(project, balloon);});
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

  private static void createTestFiles(@NotNull Course course, @NotNull Task task,
                                      @NotNull Task unsolvedTask, @NotNull VirtualFile lessonDir) {
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final VirtualFile taskDir = VfsUtil
          .findFileByIoFile(new File(lessonDir.getCanonicalPath(), EduNames.TASK + unsolvedTask.getIndex()), true);
        final File resourceRoot = new File(course.getCourseDirectory(), lessonDir.getName());

        if (taskDir != null) {
          final File newResourceRoot = new File(resourceRoot, taskDir.getName());
          File[] filesInTask = newResourceRoot.listFiles();
          if (filesInTask != null) {
            for (File file : filesInTask) {
              final String taskRelativePath = FileUtil.getRelativePath(taskDir.getPath(), file.getPath(), '/');
              if (taskRelativePath != null && !task.isTaskFile(taskRelativePath)) {
                final File resourceFile = new File(newResourceRoot, taskRelativePath);
                final File fileInProject = new File(taskDir.getCanonicalPath(), taskRelativePath);
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
  private static Task getCodeTaskFromStep(@NotNull Project project,
                                          @NotNull StepicWrappers.Step step,
                                          @NotNull String name,
                                          int lessonID) {
    final Task task = new Task();
    task.setName(name);
    task.setStepId(lessonID);
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
      for (StepicWrappers.FileWrapper wrapper : step.options.test) {
        task.addTestsTexts(wrapper.name, wrapper.text);
      }
    }
    else {
      if (step.options.samples != null) {
        createTestFileFromSamples(task, step.options.samples);
      }
    }

    task.taskFiles = new HashMap<>();
    if (step.options.files != null) {
      for (TaskFile taskFile : step.options.files) {
        task.taskFiles.put(taskFile.name, taskFile);
      }
    }
    else {
      final TaskFile taskFile = new TaskFile();
      taskFile.name = CODE_TASK_TYPE;
      final String templateForTask = getCodeTemplateForTask(project, task, step.options.codeTemplates);
      taskFile.text = templateForTask == null ? "# write your answer here \n" : templateForTask;
      task.taskFiles.put("code.py", taskFile);
    }
    return task;
  }

  private static String getCodeTemplateForTask(@NotNull Project project,
                                               @NotNull Task task,
                                               @Nullable StepicWrappers.CodeTemplatesWrapper codeTemplates) {
    if (codeTemplates != null) {
      final String languageString = getLanguageString(task, project);
      if (languageString != null) {
        return codeTemplates.getTemplateForLanguage(languageString);
      }
    }

    return null;
  }

  public static Pair<Boolean, String> checkChoiceTask(@NotNull Project project, @NotNull Task task) {
    if (task.getSelectedVariants().isEmpty()) return Pair.create(false, "No variants selected");
    final StepicWrappers.AdaptiveAttemptWrapper.Attempt attempt = getAttemptForStep(task.getStepId());

    if (attempt != null) {
      final int attemptId = attempt.id;

      final boolean isActiveAttempt = task.getSelectedVariants().stream()
        .allMatch(index -> attempt.dataset.options.get(index).equals(task.getChoiceVariants().get(index)));
      if (!isActiveAttempt) return Pair.create(false, "Your solution is out of date. Please try again");
      final StepicWrappers.SubmissionToPostWrapper wrapper = new StepicWrappers.SubmissionToPostWrapper(String.valueOf(attemptId),
                                                                                                        createChoiceTaskAnswerArray(task));
      final Pair<Boolean, String> pair = doAdaptiveCheck(wrapper, attemptId);
      if (!pair.getFirst()) {
        try {
          createNewAttempt(task.getStepId());
          final Task updatedTask = getTask(project, task.getName(), task.getStepId());
          if (updatedTask != null) {
            final List<String> variants = updatedTask.getChoiceVariants();
            task.setChoiceVariants(variants);
            task.setSelectedVariants(new ArrayList<>());
          }
        }
        catch (IOException e) {
          LOG.warn(e.getMessage());
        }
      }
      return pair;
    }

    return new Pair<>(false, "");
  }

  private static boolean[] createChoiceTaskAnswerArray(@NotNull Task task) {
    final List<Integer> selectedVariants = task.getSelectedVariants();
    final boolean[] answer = new boolean[task.getChoiceVariants().size()];
    for (Integer index : selectedVariants) {
      answer[index] = true;
    }
    return answer;
  }

  @Nullable
  public static Pair<Boolean, String> checkCodeTask(@NotNull Project project, @NotNull Task task) {
    int attemptId = -1;
    try {
      attemptId = getAttemptId(task);
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    if (attemptId != -1) {
      final Editor editor = StudyUtils.getSelectedEditor(project);
      String language = "python3";
      if (editor != null) {
        final String answer = PYCHARM_COMMENT + editor.getDocument().getText();
        final StepicWrappers.SubmissionToPostWrapper submissionToPost =
          new StepicWrappers.SubmissionToPostWrapper(String.valueOf(attemptId), language, answer);
        return doAdaptiveCheck(submissionToPost, attemptId);
      }
    }
    else {
      LOG.warn("Got an incorrect attempt id: " + attemptId);
    }
    return Pair.create(false, "");
  }

  private static Pair<Boolean, String> doAdaptiveCheck(@NotNull StepicWrappers.SubmissionToPostWrapper submission,
                                                       int attemptId) {
    final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
    if (client != null) {
      final StepicUser user = StepicUpdateSettings.getInstance().getUser();
      StepicWrappers.ResultSubmissionWrapper wrapper = postResultsForCheck(client, submission);
      if (wrapper != null) {
        wrapper = getCheckResults(client, wrapper, attemptId, user.getId());
        if (wrapper.submissions.length > 0) {
          final String status = wrapper.submissions[0].status;
          final String hint = wrapper.submissions[0].hint;
          final boolean isSolved = !status.equals("wrong");
          return Pair.create(isSolved, hint.isEmpty() ? StringUtil.capitalize(status) + " solution" : hint);
        }
        else {
          LOG.warn("Got a submission wrapper with incorrect submissions number: " + wrapper.submissions.length);
        }
      }
    }

    return Pair.create(false, "");
  }

  @Nullable
  private static StepicWrappers.ResultSubmissionWrapper postResultsForCheck(@NotNull final CloseableHttpClient client,
                                                                            @NotNull StepicWrappers.SubmissionToPostWrapper submissionToPostWrapper) {
    final CloseableHttpResponse response;
    try {
      final HttpPost httpPost = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.SUBMISSIONS);
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
  private static StepicWrappers.ResultSubmissionWrapper getCheckResults(@NotNull CloseableHttpClient client,
                                                                        @NotNull StepicWrappers.ResultSubmissionWrapper wrapper,
                                                                        int attemptId,
                                                                        int userId) {
    try {
      while (wrapper.submissions.length == 1 && wrapper.submissions[0].status.equals("evaluation")) {
        TimeUnit.MILLISECONDS.sleep(500);
        final URI submissionURI = new URIBuilder(EduStepicNames.STEPIC_API_URL + EduStepicNames.SUBMISSIONS)
          .addParameter("attempt", String.valueOf(attemptId))
          .addParameter("order", "desc")
          .addParameter("user", String.valueOf(userId))
          .build();
        final HttpGet httpGet = new HttpGet(submissionURI);
        setTimeout(httpGet);
        final CloseableHttpResponse httpResponse = client.execute(httpGet);
        final HttpEntity entity = httpResponse.getEntity();
        final String entityString = EntityUtils.toString(entity);
        EntityUtils.consume(entity);
        wrapper = new Gson().fromJson(entityString, StepicWrappers.ResultSubmissionWrapper.class);
      }
    }
    catch (InterruptedException | URISyntaxException | IOException e) {
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

  private static int getAttemptId(@NotNull Task task) throws IOException {
    final StepicWrappers.AdaptiveAttemptWrapper attemptWrapper = new StepicWrappers.AdaptiveAttemptWrapper(task.getStepId());

    final HttpPost post = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ATTEMPTS);
    post.setEntity(new StringEntity(new Gson().toJson(attemptWrapper)));

    final CloseableHttpClient client = EduStepicAuthorizedClient.getHttpClient();
    if (client == null) return -1;
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
    return -1;
  }

  private static void createTestFileFromSamples(@NotNull Task task,
                                                @NotNull List<List<String>> samples) {

    String testText = "from test_helper import check_samples\n\n" +
                      "if __name__ == '__main__':\n" +
                      "    check_samples(samples=" + new GsonBuilder().create().toJson(samples) + ")";
    task.addTestsTexts("tests.py", testText);
  }

  @NotNull
  public static List<Integer> getEnrolledCoursesIds(@NotNull StepicUser stepicUser) {
    try {
      final URI enrolledCoursesUri = new URIBuilder(EduStepicNames.COURSES).addParameter("enrolled", "true").build();
      final List<CourseInfo> courses = EduStepicAuthorizedClient.getFromStepic(enrolledCoursesUri.toString(),
                                                                               StepicWrappers.CoursesContainer.class,
                                                                               stepicUser).courses;
      final ArrayList<Integer> ids = new ArrayList<>();
      for (CourseInfo course : courses) {
        ids.add(course.getId());
      }
      return ids;
    }
    catch (IOException | URISyntaxException e) {
      LOG.warn(e.getMessage());
    }
    return Collections.emptyList();
  }
}
