package com.jetbrains.edu.learning.stepic;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyUtils;
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
import java.util.stream.Collectors;

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
    @Expose List<FileWrapper> test;
    @Expose String title;
    @Expose List<TaskFile> files;
    @Expose List<FileWrapper> text;
    @Expose List<List<String>> samples;
    @Expose Integer executionMemoryLimit;
    @Expose Integer executionTimeLimit;
    @Expose CodeTemplatesWrapper codeTemplates;
    @SerializedName("format_version")
    @Expose public int formatVersion = 2;
    @SerializedName("last_subtask_index")
    @Expose int lastSubtaskIndex = 0;

    public static StepOptions fromTask(final Project project, @NotNull final Task task) {
      final StepOptions source = new StepOptions();
      source.lastSubtaskIndex = task.getLastSubtaskIndex();
      setTests(task, source, project);
      setTaskTexts(task, source, project);
      source.files = new ArrayList<>();
      source.title = task.getName();
      for (final Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          final VirtualFile taskDir = task.getTaskDir(project);
          assert taskDir != null;
          VirtualFile ideaDir = project.getBaseDir().findChild(Project.DIRECTORY_STORE_FOLDER);
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
          VirtualFile answerFile = taskDir.findFileByRelativePath(name);
          Pair<VirtualFile, TaskFile> pair = EduUtils.createStudentFile(StepicWrappers.class, project, answerFile, stepicDir, null, 0);
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

    private static void setTaskTexts(@NotNull Task task, @NotNull StepOptions stepOptions, @NotNull Project project) {
      stepOptions.text = new ArrayList<>();
      VirtualFile taskDir = task.getTaskDir(project);
      if (taskDir == null) {
        return;
      }
      List<VirtualFile> taskDescriptionFiles = Arrays.stream(taskDir.getChildren())
        .filter(virtualFile -> StudyUtils.isTaskDescriptionFile(virtualFile.getName()))
        .collect(Collectors.toList());
      for (VirtualFile taskDescriptionFile : taskDescriptionFiles) {
        addFileWrapper(taskDescriptionFile, stepOptions.text);
      }
    }

    private static void setTests(@NotNull Task task, @NotNull StepOptions source, @NotNull Project project) {
      final Map<String, String> testsText = task.getTestsText();
      source.test = new ArrayList<>();
      if (testsText.isEmpty()) {
        List<VirtualFile> testFiles = getTestFiles(task, project);
        for (VirtualFile testFile : testFiles) {
          addFileWrapper(testFile, source.test);
        }
      }
      else {
        for (Map.Entry<String, String> entry : testsText.entrySet()) {
          source.test.add(new FileWrapper(entry.getKey(), entry.getValue()));
        }
      }
    }
  }

  private static void addFileWrapper(@NotNull VirtualFile file, List<FileWrapper> wrappers) {
    try {
      wrappers.add(new FileWrapper(file.getName(), VfsUtilCore.loadText(file)));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static List<VirtualFile> getTestFiles(@NotNull Task task, @NotNull Project project) {
    List<VirtualFile> testFiles = new ArrayList<>();
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return testFiles;
    }
    if (!task.hasSubtasks()) {
      VirtualFile testFile = taskDir.findChild(EduNames.TESTS_FILE);
      testFiles.add(testFile);
      return testFiles;
    }
    testFiles.addAll(Arrays.stream(taskDir.getChildren())
                       .filter(file -> StudyUtils.isTestsFile(project, file.getName()))
                       .collect(Collectors.toList()));
    return testFiles;
  }

  static class CodeTemplatesWrapper {
    String python3;
    String python27;

    @Nullable
    public String getTemplateForLanguage(@NotNull final String language) {
      if (language.equals(EduAdaptiveStepicConnector.PYTHON2)) {
        return python27;
      }

      if (language.equals(EduAdaptiveStepicConnector.PYTHON3)) {
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

  static class FileWrapper {
    @Expose public final String name;
    @Expose public final String text;

    public FileWrapper(String name, String text) {
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

  static class AdaptiveAttemptWrapper {
    static class Attempt {
      int step;
      Dataset dataset;
      String dataset_url;
      String status;
      String time;
      String time_left;
      String user;
      String user_id;
      int id;

      public Attempt(int step) {
        this.step = step;
      }

      public boolean isActive() {
        return status.equals("active");
      }
    }

    static class Dataset {
      boolean is_multiple_choice;
      List<String> options;
    }

    public AdaptiveAttemptWrapper(int step) {
      attempt = new Attempt(step);
    }

    Attempt attempt;
  }

  static class AdaptiveAttemptContainer {
    List<AdaptiveAttemptWrapper.Attempt> attempts;
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
      submission = new Submission(attemptId, new Submission.CodeReply(language, code));
    }

    public SubmissionToPostWrapper(@NotNull String attemptId, boolean[] choices) {
      submission = new Submission(attemptId, new Submission.ChoiceReply(choices));
    }

    static class Submission {
      String attempt;
      Reply reply;

      public Submission(String attempt, Reply reply) {
        this.attempt = attempt;
        this.reply = reply;
      }


      interface Reply {

      }

      static class CodeReply implements Reply {
        String language;
        String code;

        public CodeReply(String language, String code) {
          this.language = language;
          this.code = code;
        }
      }

      static class ChoiceReply implements Reply {
        boolean[] choices;

        public ChoiceReply(boolean[] choices) {
          this.choices = choices;
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
}
