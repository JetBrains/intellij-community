package com.jetbrains.edu.learning;

import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.EduAnswerPlaceholderDeleteHandler;
import com.jetbrains.edu.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.run.StudyExecutor;
import com.jetbrains.edu.learning.run.StudyTestRunner;
import com.jetbrains.edu.learning.ui.StudyToolWindowFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.Collection;
import java.util.List;

public class StudyUtils {
  private StudyUtils() {
  }

  private static final Logger LOG = Logger.getInstance(StudyUtils.class.getName());

  public static void closeSilently(@Nullable final Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      }
      catch (IOException e) {
        // close silently
      }
    }
  }

  public static boolean isZip(String fileName) {
    return fileName.contains(".zip");
  }

  public static <T> T getFirst(@NotNull final Iterable<T> container) {
    return container.iterator().next();
  }

  public static boolean indexIsValid(int index, @NotNull final Collection collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static String getFileText(@Nullable final String parentDir, @NotNull final String fileName, boolean wrapHTML,
                                   @NotNull final String encoding) {
    final File inputFile = parentDir != null ? new File(parentDir, fileName) : new File(fileName);
    if (!inputFile.exists()) return null;
    final StringBuilder taskText = new StringBuilder();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), encoding));
      String line;
      while ((line = reader.readLine()) != null) {
        taskText.append(line).append("\n");
        if (wrapHTML) {
          taskText.append("<br>");
        }
      }
      return wrapHTML ? UIUtil.toHtml(taskText.toString()) : taskText.toString();
    }
    catch (IOException e) {
      LOG.info("Failed to get file text from file " + fileName, e);
    }
    finally {
      closeSilently(reader);
    }
    return null;
  }

  public static void updateAction(@NotNull final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(false);
    final Project project = e.getProject();
    if (project != null) {
      final FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
      for (FileEditor editor : editors) {
        if (editor instanceof StudyEditor) {
          presentation.setEnabled(true);
          presentation.setVisible(true);
        }
      }
    }
  }

  public static void updateStudyToolWindow(@NotNull final Project project) {
    ToolWindowManager.getInstance(project).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW).
      getContentManager().removeAllContents(false);
    StudyToolWindowFactory factory = new StudyToolWindowFactory();
    factory.createToolWindowContent(project, ToolWindowManager.getInstance(project).
      getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW));
  }

  public static void deleteFile(@NotNull final VirtualFile file) {
    try {
      file.delete(StudyUtils.class);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static File copyResourceFile(@NotNull final String sourceName, @NotNull final String copyName, @NotNull final Project project,
                                      @NotNull final Task task)
    throws IOException {
    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    final Course course = taskManager.getCourse();
    int taskNum = task.getIndex() + 1;
    int lessonNum = task.getLesson().getIndex();
    assert course != null;
    final String pathToResource = FileUtil.join(course.getCourseDirectory(), EduNames.LESSON + lessonNum, EduNames.TASK + taskNum);
    final File resourceFile = new File(pathToResource, copyName);
    FileUtil.copy(new File(pathToResource, sourceName), resourceFile);
    return resourceFile;
  }

  @Nullable
  public static Sdk findSdk(@NotNull final Task task, @NotNull final Project project) {
    final Language language = task.getLesson().getCourse().getLanguageById();
    return StudyExecutor.INSTANCE.forLanguage(language).findSdk(project);
  }

  @NotNull
  public static StudyTestRunner getTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    final Language language = task.getLesson().getCourse().getLanguageById();
    return StudyExecutor.INSTANCE.forLanguage(language).getTestRunner(task, taskDir);
  }

  public static RunContentExecutor getExecutor(@NotNull final Project project, @NotNull final Task currentTask,
                                               @NotNull final ProcessHandler handler) {
    final Language language = currentTask.getLesson().getCourse().getLanguageById();
    return StudyExecutor.INSTANCE.forLanguage(language).getExecutor(project, handler);
  }

  public static void setCommandLineParameters(@NotNull final GeneralCommandLine cmd,
                                              @NotNull final Project project,
                                              @NotNull final String filePath,
                                              @NotNull final String sdkPath,
                                              @NotNull final Task currentTask) {
    final Language language = currentTask.getLesson().getCourse().getLanguageById();
    StudyExecutor.INSTANCE.forLanguage(language).setCommandLineParameters(cmd, project, filePath, sdkPath, currentTask);
  }

  public static void showNoSdkNotification(@NotNull final Task currentTask, @NotNull final Project project) {
    final Language language = currentTask.getLesson().getCourse().getLanguageById();
    StudyExecutor.INSTANCE.forLanguage(language).showNoSdkNotification(project);
  }


  /**
   * shows pop up in the center of "check task" button in study editor
   */
  public static void showCheckPopUp(@NotNull final Project project, @NotNull final Balloon balloon) {
    final StudyEditor studyEditor = StudyEditor.getSelectedStudyEditor(project);
    assert studyEditor != null;
    final JButton checkButton = studyEditor.getCheckButton();
    balloon.showInCenterOf(checkButton);
    Disposer.register(project, balloon);
  }

  /**
   * returns language manager which contains all the information about language specific file names
   */
  @Nullable
  public static StudyLanguageManager getLanguageManager(@NotNull final Course course) {
    Language language = course.getLanguageById();
    return language == null ? null : StudyLanguageManager.INSTANCE.forLanguage(language);
  }

  @Nullable
  public static TaskFile getTaskFile(@NotNull final Project project, @NotNull final VirtualFile file) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return null;
    }
    final VirtualFile taskDir = file.getParent();
    if (taskDir == null) {
      return null;
    }
    final String taskDirName = taskDir.getName();
    if (taskDirName.contains(EduNames.TASK)) {
      final VirtualFile lessonDir = taskDir.getParent();
      if (lessonDir != null) {
        int lessonIndex = EduUtils.getIndex(lessonDir.getName(), EduNames.LESSON);
        List<Lesson> lessons = course.getLessons();
        if (!indexIsValid(lessonIndex, lessons)) {
          return null;
        }
        final Lesson lesson = lessons.get(lessonIndex);
        int taskIndex = EduUtils.getIndex(taskDirName, EduNames.TASK);
        final List<Task> tasks = lesson.getTaskList();
        if (!indexIsValid(taskIndex, tasks)) {
          return null;
        }
        final Task task = tasks.get(taskIndex);
        return task.getFile(file.getName());
      }
    }
    return null;
  }


  public static void drawAllWindows(Editor editor, TaskFile taskFile) {
    editor.getMarkupModel().removeAllHighlighters();
    final Project project = editor.getProject();
    if (project == null) return;
    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      final JBColor color = taskManager.getColor(answerPlaceholder);
      EduAnswerPlaceholderPainter.drawAnswerPlaceholder(editor, answerPlaceholder, true, color);
    }
    final Document document = editor.getDocument();
    EditorActionManager.getInstance()
      .setReadonlyFragmentModificationHandler(document, new EduAnswerPlaceholderDeleteHandler(editor));
    EduAnswerPlaceholderPainter.createGuardedBlocks(editor, taskFile, true);
    editor.getColorsScheme().setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null);
  }

}
