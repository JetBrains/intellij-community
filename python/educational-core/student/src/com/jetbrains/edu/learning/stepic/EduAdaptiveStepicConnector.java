package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.jetbrains.edu.learning.stepic.EduStepicConnector.getHttpClient;
import static com.jetbrains.edu.learning.stepic.EduStepicConnector.setHeaders;

public class EduAdaptiveStepicConnector {
  private static final Logger LOG = Logger.getInstance(EduAdaptiveStepicConnector.class);
  private static final String STEPIC_URL = "https://stepic.org/";
  private static final String STEPIC_API_URL = STEPIC_URL + "api/";
  private static final String RECOMMENDATIONS_URL = "recommendations";
  private static final String CONTENT_TYPE_APPL_JSON = "application/json";
  private static final String LESSON_URL = "lessons/";
  private static final String RECOMMENDATION_REACTIONS_URL = "recommendation-reactions";
  private static final String ATTEMPTS_URL = "attempts";
  private static final String SUBMISSION_URL = "submissions";

  @Nullable
  public static Task getNextRecommendation(@NotNull final Project project, @NotNull Course course) {
    try {
      final CloseableHttpClient client = getHttpClient(project);
      final URI uri = new URIBuilder(STEPIC_API_URL + RECOMMENDATIONS_URL).addParameter("course", String.valueOf(course.getId())).build();
      final HttpGet request = new HttpGet(uri);
      setHeaders(request, CONTENT_TYPE_APPL_JSON);

      final CloseableHttpResponse response = client.execute(request);
      final StatusLine statusLine = response.getStatusLine();
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";

      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        final StepicWrappers.RecommendationWrapper recomWrapper = gson.fromJson(responseString, StepicWrappers.RecommendationWrapper.class);

        if (recomWrapper.recommendations.length != 0) {
          final StepicWrappers.Recommendation recommendation = recomWrapper.recommendations[0];
          final String lessonId = recommendation.lesson;
          final StepicWrappers.LessonContainer
            lessonContainer = EduStepicConnector.getFromStepic(LESSON_URL + lessonId, StepicWrappers.LessonContainer.class);
          final Lesson realLesson = lessonContainer.lessons.get(0);
          course.getLessons().get(0).id = Integer.parseInt(lessonId);

          for (int stepId : realLesson.steps) {
            final StepicWrappers.Step step = EduStepicConnector.getStep(stepId);
            if (step.name.equals("code")) {
              return getTaskFromStep(realLesson.getName(), stepId, step);
            }
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

  public static boolean postRecommendationReaction(@NotNull final Project project, @NotNull final String lessonId, 
                                                   @NotNull final String user,int reaction) {

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
      // TODO: get user from settings
      final StepicUser user = StudyTaskManager.getInstance(project).getUser();
      if (user != null &&
          postRecommendationReaction(project, String.valueOf(editor.getTaskFile().getTask().getLesson().id), 
                                     String.valueOf(user.id), reaction)) {
        final Task task = getNextRecommendation(project, course);

        if (task != null) {
          final Lesson adaptive = course.getLessons().get(0);
          if (reaction == 0 || reaction == -1) {
            final Task unsolvedTask = adaptive.getTaskList().get(0);
            unsolvedTask.setName(task.getName());
            unsolvedTask.setStepicId(task.getStepicId());
            unsolvedTask.setText(task.getText());
            unsolvedTask.getTaskFiles().clear();
            unsolvedTask.getTaskFiles().putAll(task.getTaskFiles());

            final File lessonDirectory = new File(course.getCourseDirectory(), EduNames.LESSON + String.valueOf(adaptive.getIndex()));
            final File taskDirectory = new File(lessonDirectory, EduNames.TASK + String.valueOf(adaptive.getTaskList().size()));
            StudyProjectGenerator.flushTask(task, taskDirectory);
            final StudyToolWindow window = StudyUtils.getStudyToolWindow(project);
            if (window != null) {
              window.setTaskText(unsolvedTask.getText());
            }

            adaptive.initLesson(course, false);
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
          }
          else {
            adaptive.addTask(task);
            task.setIndex(adaptive.getTaskList().size());
            final VirtualFile lessonDir = project.getBaseDir().findChild(EduNames.LESSON + String.valueOf(adaptive.getIndex()));

            if (lessonDir != null) {
              ApplicationManager.getApplication().runWriteAction(() -> {
                try {
                  StudyGenerator.createTask(task, lessonDir, new File(course.getCourseDirectory(), lessonDir.getName()), project);
                }
                catch (IOException e) {
                  LOG.warn(e.getMessage());
                }
              });
            }

            final File lessonDirectory = new File(course.getCourseDirectory(), EduNames.LESSON + String.valueOf(adaptive.getIndex()));
            final File taskDirectory = new File(lessonDirectory, EduNames.TASK + String.valueOf(adaptive.getTaskList().size()));
            StudyProjectGenerator.flushTask(task, taskDirectory);
            adaptive.initLesson(course, false);
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
          }
        }
      }
      else {
        LOG.warn("Recommendation reactions weren't posted");
      }
    }
  }

  @NotNull
  private static Task getTaskFromStep(@NotNull String name, int lessonID, StepicWrappers.Step step) {
    final Task task = new Task();
    task.setName(name);
    task.setStepicId(lessonID);
    task.setText(step.text);
    if (step.options.test != null) {
      for (StepicWrappers.TestFileWrapper wrapper : step.options.test) {
        task.addTestsTexts(wrapper.name, wrapper.text);
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
      taskFile.text = "# write your answer here \n";
      task.taskFiles.put("code.py", taskFile);
    }
    return task;
  }

  @Nullable
  public static Pair<Boolean, String> checkTask(@NotNull final Project project, @NotNull final Task task) {
    try {
      final int attemptId = getAttemptId(project, task, ATTEMPTS_URL);
      final Editor editor = StudyUtils.getSelectedEditor(project);
      String language = getLanguageString(task, project);
      if (editor != null && language != null) {
        final StepicWrappers.SubmissionToPostWrapper submissionToPostWrapper =
          new StepicWrappers.SubmissionToPostWrapper(String.valueOf(attemptId), "python3", editor.getDocument().getText());
        final HttpPost httpPost = new HttpPost(STEPIC_API_URL + SUBMISSION_URL);
        httpPost.setEntity(new StringEntity(new Gson().toJson(submissionToPostWrapper)));
        final CloseableHttpClient client = getHttpClient(project);
        setHeaders(httpPost, CONTENT_TYPE_APPL_JSON);
        final CloseableHttpResponse execute = client.execute(httpPost);
        StepicWrappers.ResultSubmissionWrapper wrapper =
          new Gson().fromJson(EntityUtils.toString(execute.getEntity()), StepicWrappers.ResultSubmissionWrapper.class);

        final StepicUser user = StudyTaskManager.getInstance(project).getUser();
        if (user != null) {
          final int id = user.getId();
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
            wrapper = new Gson().fromJson(EntityUtils.toString(httpResponse.getEntity()), StepicWrappers.ResultSubmissionWrapper.class);
          }
          if (wrapper.submissions.length == 1) {
            final boolean isSolved = !wrapper.submissions[0].status.equals("wrong");
            return Pair.create(isSolved, wrapper.submissions[0].hint);
          }
        }
      }
    }
    catch (ClientProtocolException e) {
      LOG.warn(e.getMessage());
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    catch (URISyntaxException e) {
      LOG.warn(e.getMessage());
    }
    catch (InterruptedException e) {
      LOG.warn(e.getMessage());
    }
    return Pair.create(false, "");
  }

  @Nullable
  private static String getLanguageString(@NotNull Task task, @NotNull Project project) {
    final Sdk sdk = StudyUtils.findSdk(task, project);
    if (sdk != null) {
      if (sdk.getVersionString() != null && sdk.getVersionString().startsWith("3")) {
        return "python3";
      }
      else {
        return "python2";
      }
    }
    else {
      StudyUtils.showNoSdkNotification(task, project);
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
    final StepicWrappers.AttemptContainer container =
      new Gson().fromJson(EntityUtils.toString(httpResponse.getEntity()), StepicWrappers.AttemptContainer.class);
    return container.attempts.get(0).id;
  }
}
