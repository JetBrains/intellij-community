package com.jetbrains.edu.learning.stepic;

import com.google.gson.annotations.Expose;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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
import java.io.InputStream;
import java.util.*;

public class StepicWrappers {
  private static final Logger LOG = Logger.getInstance(StepOptions.class);

  static class StepContainer {
    List<StepSource> steps;
  }

  public static class Step {
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

  public static class StepOptions {
    @Expose List<TestFileWrapper> test;
    @Expose String title;
    @Expose List<TaskFile> files;
    @Expose String text;
    @Expose List<List<String>> samples;
    @Expose Integer executionMemoryLimit;
    @Expose Integer executionTimeLimit;
    @Expose CodeTemplatesWrapper codeTemplates;

    public static StepOptions fromTask(final Project project, @NotNull final Task task) {
      final StepOptions source = new StepOptions();
      setTests(task, source, project);
      source.files = new ArrayList<>();
      source.title = task.getName();
      for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          final VirtualFile taskDir = task.getTaskDir(project);
          assert taskDir != null;
          VirtualFile ideaDir = project.getBaseDir().findChild(".idea");
          assert ideaDir != null;
          String stepic = "stepic";
          VirtualFile stepicDir = ideaDir.findChild(stepic);
          if (stepicDir == null) {
            try {
              stepicDir = ideaDir.createChildDirectory(StepicWrappers.class, stepic);
            }
            catch (IOException e) {
              LOG.info("Failed to create idea/stepic directory", e);
            }
          }
          if (stepicDir == null) {
            return;
          }
          String name = entry.getKey();
          VirtualFile answerFile = taskDir.findChild(name);
          Pair<VirtualFile, TaskFile> pair = EduUtils.createStudentFile(StepicWrappers.class, project, answerFile, stepicDir, null);
          if (pair == null) {
            return;
          }
          VirtualFile virtualFile = pair.getFirst();
          TaskFile taskFile = pair.getSecond();
          try {
            InputStream stream = virtualFile.getInputStream();
            taskFile.text =
              EduUtils.isImage(name) ? Base64.encodeBase64URLSafeString(FileUtil.loadBytes(stream)) : FileUtil.loadTextAndClose(stream);
          }
          catch (IOException e) {
            LOG.error("Can't find file " + virtualFile.getPath());
          }
          source.files.add(taskFile);
        });
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
        source.test = new ArrayList<>();
        for (Map.Entry<String, String> entry : testsText.entrySet()) {
          source.test.add(new TestFileWrapper(entry.getKey(), entry.getValue()));
        }
      }
    }
  }

  static class CodeTemplatesWrapper {
    String python3;
    String python27;

    @Nullable
    public String getTemplateForLanguage(@NotNull final String langauge) {
      if (langauge.equals(EduAdaptiveStepicConnector.PYTHON2)) {
        return python27;
      }

      if (langauge.equals(EduAdaptiveStepicConnector.PYTHON3)) {
        return python3;
      }

      return null;
    }
  }

  static class CoursesContainer {
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
      this.lesson.steps = new ArrayList<>();
    }
  }

  static class LessonContainer {
    List<Lesson> lessons;
  }

  static class StepSource {
    @Expose Step block;
    @Expose int position = 0;
    @Expose int lesson = 0;
    Date update_date;

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
  }

  static class Section {
    List<Integer> units;
    int course;
    String title;
    int position;
    int id;
  }

  static class SectionWrapper {
    Section section;
  }

  static class SectionContainer {
    List<Section> sections;
    List<Lesson> lessons;

    List<Unit> units;
  }

  static class Unit {
    int id;
    int section;
    int lesson;
    int position;
    List<Integer> assignments;
  }

  static class UnitContainer {

    List<Unit> units;
  }

  static class UnitWrapper {
    Unit unit;
  }

  static class AttemptWrapper {
    static class Attempt {
      public Attempt(int step) {
        this.step = step;
      }

      int step;
      int id;
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

  static class AttemptContainer {
    List<AttemptWrapper.Attempt> attempts;
  }

  static class SolutionFile {
    String name;
    String text;

    public SolutionFile(String name, String text) {
      this.name = name;
      this.text = text;
    }
  }

  static class AuthorWrapper {
    List<StepicUser> users;
  }

  static class SubmissionWrapper {
    Submission submission;


    public SubmissionWrapper(int attempt, String score, ArrayList<SolutionFile> files) {
      submission = new Submission(score, attempt, files);
    }

    static class Submission {
      int attempt;
      private final Reply reply;

      public Submission(String score, int attempt, ArrayList<SolutionFile> files) {
        reply = new Reply(files, score);
        this.attempt = attempt;
      }

      static class Reply {
        String score;
        List<SolutionFile> solution;

        public Reply(ArrayList<SolutionFile> files, String score) {
          this.score = score;
          solution = files;
        }
      }
    }
  }

  static class UserWrapper {
    StepicUser user;

    public UserWrapper(String user, String password) {
      this.user = new StepicUser(user, password);
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


  static class SubmissionToPostWrapper {
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

  static class ResultSubmissionWrapper {
    ResultSubmission[] submissions;

    static class ResultSubmission {
      int id;
      String status;
      String hint;
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
}
