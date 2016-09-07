/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.edu.learning.stepik;

import com.google.gson.annotations.Expose;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StepikWrappers {
  private static final Logger LOG = Logger.getInstance(StepOptions.class);

  static class StepContainer {
    List<StepSource> steps;
  }

  public static class Step {
    @Expose StepOptions options;
    @Expose String text;
    //@Expose String name = "pycharm";
    @Expose String name;
    @Expose StepOptions source;

    public static Step fromTask(Project project, @NotNull final Task task) {
      final Step step = new Step();
      step.text = task.getTaskText(project);
      step.source = StepOptions.fromTask(project, task);
      return step;
    }
  }

  public static class StepOptions {
    @Expose List<TestFileWrapper> test;
    @Expose String title;
    @Expose List<TaskFile> files;
    @Expose String text;
    @Expose List<List<String>> samples;
    @Expose Integer executionMemoryLimit;
    @Expose Integer executionTimeLimit;
    //    @Expose Map<String, String> codeTemplates;
    @Expose CodeTemplatesWrapper codeTemplates;

    public static StepOptions fromTask(final Project project, @NotNull final Task task) {
      final StepOptions source = new StepOptions();
      setTests(task, source, project);
      source.files = new ArrayList<TaskFile>();
      source.title = task.getName();
      for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
        final TaskFile taskFile = new TaskFile();
        TaskFile.copy(entry.getValue(), taskFile);
        ApplicationManager.getApplication().runWriteAction(() -> {
          final VirtualFile taskDir = task.getTaskDir(project);
          assert taskDir != null;
          VirtualFile ideaDir = project.getBaseDir().findChild(".idea");
          assert ideaDir != null;
          EduUtils.createStudentFileFromAnswer(project, ideaDir, taskDir, entry.getKey(), taskFile);
        });
        taskFile.name = entry.getKey();

        VirtualFile ideaDir = project.getBaseDir().findChild(".idea");
        if (ideaDir == null) return null;
        final VirtualFile file = ideaDir.findChild(taskFile.name);
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
        ApplicationManager.getApplication().runReadAction(() -> {
          source.test = Collections.singletonList(new TestFileWrapper(EduNames.TESTS_FILE, task.getTestsText(project)));
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

  static class CodeTemplatesWrapper {
    String python3;
    String python27;
    String java;
    String java8;

    @Nullable
    public String getTemplateForLanguage(@NotNull final String langauge) {
      if (langauge.equals(EduAdaptiveStepikConnector.PYTHON27)) {
        return python27;
      }

      if (langauge.equals(EduAdaptiveStepikConnector.PYTHON3)) {
        return python3;
      }

      if (langauge.equals("java")) {
        return java;
      }

      if (langauge.equals("java8")) {
        return java8;
      }

      return null;
    }
  }

  public static class CoursesContainer {
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
      this.lesson.setId(lesson.getId());
      this.lesson.steps = new ArrayList<Integer>();
    }
  }

  static class LessonContainer {
    List<Lesson> lessons;
  }

  static class StepSource {
    @Expose Step block;
    @Expose int id;
    @Expose int position = 0;
    @Expose int lesson = 0;

    public StepSource(Project project, Task task, int lesson) {
      this.lesson = lesson;
      position = task.getIndex();
      block = Step.fromTask(project, task);
    }
  }

  static class TestFileWrapper {
    @Expose public final String name;
    @Expose public final String text;

    public TestFileWrapper(String name, String text) {
      this.name = name;
      this.text = text;
    }

    @Override
    public String toString() {
      return "TestFileWrapper{" +
             "name='" + name + '\'' +
             ", text='" + text + '\'' +
             '}';
    }
  }

  public static class Section {
    List<Integer> units;
    public int course;
    String title;
    int position;
    int id;
  }

  static class SectionWrapper {
    Section section;
  }

  public static class SectionContainer {
    public List<Section> sections;
    List<Lesson> lessons;

    List<Unit> units;
  }

  public static class Unit {
    int id;
    public int section;
    int lesson;
    int position;
    List<Integer> assignments;
  }

  public static class UnitContainer {

    public List<Unit> units;
  }

  static class UnitWrapper {
    Unit unit;
  }

  public static class AttemptWrapper {
    public static class Attempt {
      public Attempt(int step) {
        this.step = step;
      }

      public int step;
      public int id;
    }

    public AttemptWrapper(int step) {
      attempt = new Attempt(step);
    }

    Attempt attempt;
  }

  static class AttemptToPostWrapper {
    static class Attempt {
      int step;
      String dataset_url;
      String status;
      String time;
      String time_left;
      String user;
      String user_id;

      public Attempt(int step) {
        this.step = step;
      }
    }

    public AttemptToPostWrapper(int step) {
      attempt = new Attempt(step);
    }

    Attempt attempt;
  }

  public static class AttemptContainer {
    public List<AttemptWrapper.Attempt> attempts;
  }

  static class SolutionFile {
    String name;
    String text;

    public SolutionFile(String name, String text) {
      this.name = name;
      this.text = text;
    }
  }

  public static class AuthorWrapper {
    public List<StepikUser> users;
  }

  public static class SubmissionContainer {
    public List<Submission> submissions;


    public SubmissionContainer(int attempt, String score, ArrayList<SolutionFile> files) {
      submissions = new ArrayList<>();
      submissions.add(new Submission(score, attempt, files));
    }

    public static class Submission {
      public int id;
      public int attempt;
      public final Reply reply;

      public Submission(String score, int attempt, ArrayList<SolutionFile> files) {
        reply = new Reply(files, score);
        this.attempt = attempt;
      }

      public static class Reply {
        String score;
        List<SolutionFile> solution;
        public String code;
        public String language;

        public Reply(ArrayList<SolutionFile> files, String score) {
          this.score = score;
          solution = files;
        }
      }
    }
  }

  static class UserWrapper {
    StepikUser user;

    public UserWrapper(String user, String password) {
      this.user = new StepikUser(user, password);
    }
  }

  static class RecommendationReaction {
    int reaction;
    String user;
    String lesson;

    public RecommendationReaction(int reaction, String user, String lesson) {
      this.reaction = reaction;
      this.user = user;
      this.lesson = lesson;
    }
  }

  static class RecommendationReactionWrapper {
    RecommendationReaction recommendationReaction;

    public RecommendationReactionWrapper(RecommendationReaction recommendationReaction) {
      this.recommendationReaction = recommendationReaction;
    }
  }

  static class RecommendationWrapper {
    Recommendation[] recommendations;
  }

  static class Recommendation {
    String id;
    String lesson;
  }


  public static class SubmissionToPostWrapper {
    Submission submission;

    public SubmissionToPostWrapper(@NotNull String attemptId, @NotNull String language, @NotNull String code) {
      submission = new Submission(attemptId, new Submission.Reply(language, code));
    }

    static class Submission {
      String attempt;
      Reply reply;

      public Submission(String attempt, Reply reply) {
        this.attempt = attempt;
        this.reply = reply;
      }

      static class Reply {
        String language;
        String code;

        public Reply(String language, String code) {
          this.language = language;
          this.code = code;
        }
      }
    }
  }

  public static class ResultSubmissionWrapper {
    public ResultSubmission[] submissions;

    public static class ResultSubmission {
      public int id;
      public String status;
      public String hint;
    }
  }

  static class AssignmentsWrapper {
    List<Assignment> assignments;
  }

  static class Assignment {
    int id;
    int step;
  }

  static class ViewsWrapper {
    View view;

    public ViewsWrapper(final int assignment, final int step) {
      this.view = new View(assignment, step);
    }
  }

  static class View {
    int assignment;
    int step;

    public View(int assignment, int step) {
      this.assignment = assignment;
      this.step = step;
    }
  }

  static class Enrollment {
    String course;

    public Enrollment(String courseId) {
      course = courseId;
    }
  }

  static class EnrollmentWrapper {
    Enrollment enrollment;

    public EnrollmentWrapper(@NotNull final String courseId) {
      enrollment = new Enrollment(courseId);
    }
  }

  static class TokenInfo {
    @Expose String accessToken;
    @Expose String refreshToken;
    @Expose String tokenType;
    @Expose String scope;
    @Expose int expiresIn;

    public TokenInfo() {
      accessToken = "";
      refreshToken = "";
    }

    public String getAccessToken() {
      return accessToken;
    }

    public String getRefreshToken() {
      return refreshToken;
    }
  }

  public static class MetricsWrapper {
    Metric metric;

    public MetricsWrapper(String tags_name, String tags_action, int courseId, int stepId) {
      metric = new Metric(tags_name, tags_action, courseId, stepId);
    }

    public interface MetricActions {
      String POST = "post";
      String DOWNLOAD = "download";
      String GET_COURSE = "get_course";
    }

    public interface PluginNames {
      String S_Union = "S_Union";
      String S_CLion = "S_CLion";
      String S_PyCharm = "S_PyCharm";
    }

    public class Metric {
      String name = "ide_plugin";
      Tags tags;
      Data data;

      public Metric(String tags_name, String tags_action, int courseId, int stepId) {
        this.tags = new Tags(tags_name, tags_action);
        this.data = new Data(courseId, stepId);
      }

      public class Tags {
        String name;
        String action;

        public Tags(String action) {
          this.action = action;
        }

        public Tags(String name, String action) {
          this.name = name;
          this.action = action;
        }
      }

      public class Data {
        int courseId;
        int stepId;

        public Data(int courseId, int stepId) {
          this.courseId = courseId;
          this.stepId = stepId;
        }
      }
    }
  }
}
