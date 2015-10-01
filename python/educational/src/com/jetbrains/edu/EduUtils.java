package com.jetbrains.edu;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.Function;
import com.intellij.util.containers.HashMap;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.oldCourseFormat.OldCourse;
import com.jetbrains.edu.oldCourseFormat.TaskWindow;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

public class EduUtils {
  private EduUtils() {
  }
  private static final Logger LOG = Logger.getInstance(EduUtils.class.getName());

  public static Comparator<StudyItem> INDEX_COMPARATOR = new Comparator<StudyItem>() {
    @Override
    public int compare(StudyItem o1, StudyItem o2) {
      return o1.getIndex() - o2.getIndex();
    }
  };

  public static void enableAction(@NotNull final AnActionEvent event, boolean isEnable) {
    final Presentation presentation = event.getPresentation();
    presentation.setVisible(isEnable);
    presentation.setEnabled(isEnable);
  }

  /**
   * Gets number index in directory names like "task1", "lesson2"
   *
   * @param fullName    full name of directory
   * @param logicalName part of name without index
   * @return index of object
   */
  public static int getIndex(@NotNull final String fullName, @NotNull final String logicalName) {
    if (!fullName.startsWith(logicalName)) {
      return -1;
    }
    try {
      return Integer.parseInt(fullName.substring(logicalName.length())) - 1;
    } catch(NumberFormatException e) {
      return -1;
    }
  }

  public static boolean indexIsValid(int index, Collection collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static VirtualFile flushWindows(@NotNull final TaskFile taskFile, @NotNull final VirtualFile file,
                                         boolean useLength) {
    final VirtualFile taskDir = file.getParent();
    VirtualFile fileWindows = null;
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      LOG.debug("Couldn't flush windows");
      return null;
    }
    if (taskDir != null) {
      final String name = file.getNameWithoutExtension() + EduNames.WINDOWS_POSTFIX;
      deleteWindowsFile(taskDir, name);
      PrintWriter printWriter = null;
      try {
        fileWindows = taskDir.createChildData(taskFile, name);
        printWriter = new PrintWriter(new FileOutputStream(fileWindows.getPath()));
        for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
          int length = useLength ? answerPlaceholder.getLength() : answerPlaceholder.getPossibleAnswerLength();
          if (!answerPlaceholder.isValid(document, length)) {
            printWriter.println("#educational_plugin_window = ");
            continue;
          }
          int start = answerPlaceholder.getRealStartOffset(document);
          final String windowDescription = document.getText(new TextRange(start, start + length));
          printWriter.println("#educational_plugin_window = " + windowDescription);
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        if (printWriter != null) {
          printWriter.close();
        }
        synchronize();
      }
    }
    return fileWindows;
  }

  public static void synchronize() {
    FileDocumentManager.getInstance().saveAllDocuments();
    SaveAndSyncHandler.getInstance().refreshOpenFiles();
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
  }

  public static void createStudentFileFromAnswer(@NotNull final Project project,
                                                 @NotNull final VirtualFile userFileDir,
                                                 @NotNull final VirtualFile answerFileDir,
                                                 @NotNull final String taskFileName, @NotNull final TaskFile taskFile) {
    VirtualFile file = userFileDir.findChild(taskFileName);
    if (file != null) {
      try {
        file.delete(project);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    if (taskFile.getAnswerPlaceholders().isEmpty()) {
      String extension = FileUtilRt.getExtension(taskFileName);
      String nameWithoutExtension = FileUtilRt.getNameWithoutExtension(taskFileName);
      VirtualFile answerFile = answerFileDir.findChild(nameWithoutExtension + ".answer." + extension);
      if (answerFile != null) {
        try {
          answerFile.copy(answerFileDir, userFileDir, taskFileName);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      return;
    }
    try {
      userFileDir.createChildData(project, taskFileName);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    file = userFileDir.findChild(taskFileName);
    assert file != null;
    String answerFileName = file.getNameWithoutExtension() + ".answer." + file.getExtension();
    VirtualFile answerFile = answerFileDir.findChild(answerFileName);
    if (answerFile == null) {
      return;
    }
    final Document answerDocument = FileDocumentManager.getInstance().getDocument(answerFile);
    if (answerDocument == null) {
      return;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return;

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(0, document.getTextLength(), answerDocument.getCharsSequence());
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "Create Student File", "Create Student File");
    EduDocumentListener listener = new EduDocumentListener(taskFile, false);
    document.addDocumentListener(listener);
    taskFile.sortAnswerPlaceholders();
    for (int i = taskFile.getAnswerPlaceholders().size() - 1; i >= 0; i--) {
      final AnswerPlaceholder answerPlaceholder = taskFile.getAnswerPlaceholders().get(i);
      if (answerPlaceholder.getRealStartOffset(document) > document.getTextLength() || answerPlaceholder.getRealStartOffset(document) + answerPlaceholder.getPossibleAnswerLength() > document.getTextLength()) {
        LOG.error("Wrong startOffset: " + answerPlaceholder.getRealStartOffset(document) + "; document: " + file.getPath());
        return;
      }
      replaceAnswerPlaceholder(project, document, answerPlaceholder);
    }
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "Create Student File", "Create Student File");
    document.removeDocumentListener(listener);
  }

  private static void replaceAnswerPlaceholder(@NotNull final Project project,
                                               @NotNull final Document document,
                                               @NotNull final AnswerPlaceholder answerPlaceholder) {
    final String taskText = answerPlaceholder.getTaskText();
    final int offset = answerPlaceholder.getRealStartOffset(document);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(offset, offset + answerPlaceholder.getPossibleAnswerLength(), taskText);
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    }, "Replace Answer Placeholders", "Replace Answer Placeholders");
  }

  public static void deleteWindowDescriptions(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      VirtualFile virtualFile = taskDir.findChild(name);
      if (virtualFile == null) {
        continue;
      }
      String windowsFileName = virtualFile.getNameWithoutExtension() + EduNames.WINDOWS_POSTFIX;
      deleteWindowsFile(taskDir, windowsFileName);
    }
  }

  private static void deleteWindowsFile(@NotNull final VirtualFile taskDir, @NotNull final String name) {
    final VirtualFile fileWindows = taskDir.findChild(name);
    if (fileWindows != null && fileWindows.exists()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            fileWindows.delete(taskDir);
          }
          catch (IOException e) {
            LOG.warn("Tried to delete non existed _windows file");
          }
        }
      });
    }
  }

  @Nullable
  public static Task getTask(@NotNull final PsiDirectory directory, @NotNull final Course course) {
    PsiDirectory lessonDir = directory.getParent();
    if (lessonDir == null) {
      return null;
    }
    Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) {
      return null;
    }
    return lesson.getTask(directory.getName());
  }

  @NotNull
  public static Course transformOldCourse(@NotNull final OldCourse oldCourse) {
    return transformOldCourse(oldCourse, null);
  }

  @NotNull
  public static Course transformOldCourse(@NotNull final OldCourse oldCourse,
                                          @Nullable Function<Pair<AnswerPlaceholder, StudyStatus>, Void> setStatus) {
    Course course = new Course();
    course.setDescription(oldCourse.description);
    course.setName(oldCourse.name);
    course.setAuthors(new String[]{oldCourse.author});

    String updatedCoursePath = FileUtil.join(PathManager.getConfigPath(), "courses", oldCourse.name);
    if (new File(updatedCoursePath).exists()) {
      course.setCourseDirectory(FileUtil.toSystemIndependentName(updatedCoursePath));
    }
    final ArrayList<Lesson> lessons = new ArrayList<Lesson>();
    for (com.jetbrains.edu.oldCourseFormat.Lesson oldLesson : oldCourse.lessons) {
      final Lesson lesson = new Lesson();
      lesson.setName(oldLesson.name);
      lesson.setIndex(oldLesson.myIndex + 1);

      final ArrayList<Task> tasks = new ArrayList<Task>();
      for (com.jetbrains.edu.oldCourseFormat.Task oldTask : oldLesson.taskList) {
        final Task task = new Task();
        task.setIndex(oldTask.myIndex + 1);
        task.setName(oldTask.name);
        task.setLesson(lesson);
        final HashMap<String, TaskFile> taskFiles = new HashMap<String, TaskFile>();
        for (Map.Entry<String, com.jetbrains.edu.oldCourseFormat.TaskFile> entry : oldTask.taskFiles.entrySet()) {
          final TaskFile taskFile = new TaskFile();
          final com.jetbrains.edu.oldCourseFormat.TaskFile oldTaskFile = entry.getValue();
          taskFile.setIndex(oldTaskFile.myIndex);
          taskFile.name = entry.getKey();

          final ArrayList<AnswerPlaceholder> placeholders = new ArrayList<AnswerPlaceholder>();
          for (TaskWindow window : oldTaskFile.taskWindows) {
            final AnswerPlaceholder placeholder = new AnswerPlaceholder();
            placeholder.setIndex(window.myIndex);
            placeholder.setHint(window.hint);
            placeholder.setLength(window.length);
            placeholder.setLine(window.line);
            placeholder.setPossibleAnswer(window.possibleAnswer);
            placeholder.setStart(window.start);
            placeholders.add(placeholder);
            placeholder.setInitialState(new AnswerPlaceholder.MyInitialState(window.myInitialLine,
                                                                             window.myInitialLength,
                                                                             window.myInitialStart));
            if (setStatus != null) {
              setStatus.fun(Pair.create(placeholder, window.myStatus));
            }
          }

          taskFile.setAnswerPlaceholders(placeholders);
          taskFiles.put(entry.getKey(), taskFile);
        }
        task.taskFiles = taskFiles;
        tasks.add(task);
      }

      lesson.taskList = tasks;

      lessons.add(lesson);
    }
    course.setLessons(lessons);
    course.initCourse(true);
    return course;
  }

  public static boolean isImage(String fileName) {
    final String[] readerFormatNames = ImageIO.getReaderFormatNames();
    for (@NonNls String format : readerFormatNames) {
      final String ext = format.toLowerCase();
      if (fileName.endsWith(ext)) {
        return true;
      }
    }
    return false;
  }
}
