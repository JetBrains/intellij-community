package com.jetbrains.edu.learning.core;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public class EduUtils {
  private EduUtils() {
  }

  private static final Logger LOG = Logger.getInstance(EduUtils.class.getName());

  public static final Comparator<StudyItem> INDEX_COMPARATOR = Comparator.comparingInt(StudyItem::getIndex);

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
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  public static boolean indexIsValid(int index, Collection collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static VirtualFile flushWindows(@NotNull final TaskFile taskFile, @NotNull final VirtualFile file) {
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
        for (AnswerPlaceholder answerPlaceholder : taskFile.getActivePlaceholders()) {
          int length = answerPlaceholder.getRealLength();
          int start = answerPlaceholder.getOffset();
          final String windowDescription = document.getText(new TextRange(start, start + length));
          printWriter.println("#educational_plugin_window = " + windowDescription);
        }
        ApplicationManager.getApplication().runWriteAction(() -> FileDocumentManager.getInstance().saveDocument(document));
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

  @Nullable
  public static TaskFile createStudentFile(Project project, VirtualFile answerFile, @Nullable Task task, int targetSubtaskIndex) {
    try {
      if (task == null) {
        task = StudyUtils.getTaskForFile(project, answerFile);
        if (task == null) {
          return null;
        }
        task = task.copy();
      }
      TaskFile taskFile = task.getTaskFile(StudyUtils.pathRelativeToTask(answerFile));
      if (taskFile == null) {
        return null;
      }
      if (isImage(taskFile.name)) {
        taskFile.text = Base64.encodeBase64String(answerFile.contentsToByteArray());
        return taskFile;
      }
      Document document = FileDocumentManager.getInstance().getDocument(answerFile);
      if (document == null) {
        return null;
      }
      FileDocumentManager.getInstance().saveDocument(document);
      final LightVirtualFile studentFile = new LightVirtualFile("student_task", PlainTextFileType.INSTANCE, document.getText());
      Document studentDocument = FileDocumentManager.getInstance().getDocument(studentFile);
      if (studentDocument == null) {
        return null;
      }
      EduDocumentListener listener = new EduDocumentListener(taskFile, false);
      studentDocument.addDocumentListener(listener);
      taskFile.setTrackLengths(false);
      for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
        if (task instanceof TaskWithSubtasks) {
          int fromSubtask = ((TaskWithSubtasks)task).getActiveSubtaskIndex();
          placeholder.switchSubtask(studentDocument, fromSubtask, targetSubtaskIndex);
        }
      }
      for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
        replaceWithTaskText(studentDocument, placeholder, targetSubtaskIndex);
      }
      taskFile.setTrackChanges(true);
      studentDocument.removeDocumentListener(listener);
      taskFile.text = studentDocument.getImmutableCharSequence().toString();
      return taskFile;
    }
    catch (IOException e) {
      LOG.error("Failed to convert answer file to student one");
    }

    return null;
  }

  private static void replaceWithTaskText(Document studentDocument, AnswerPlaceholder placeholder, int toSubtaskIndex) {
    AnswerPlaceholderSubtaskInfo info = placeholder.getSubtaskInfos().get(toSubtaskIndex);
    if (info == null) {
      return;
    }
    String replacementText;
    if (Collections.min(placeholder.getSubtaskInfos().keySet()) == toSubtaskIndex) {
      replacementText = info.getPlaceholderText();
    }
    else {
      Integer max = Collections.max(ContainerUtil.filter(placeholder.getSubtaskInfos().keySet(), i -> i < toSubtaskIndex));
      replacementText = placeholder.getSubtaskInfos().get(max).getPossibleAnswer();
    }
    replaceAnswerPlaceholder(studentDocument, placeholder, placeholder.getVisibleLength(toSubtaskIndex), replacementText);
  }

  public static void replaceAnswerPlaceholder(@NotNull final Document document,
                                              @NotNull final AnswerPlaceholder answerPlaceholder,
                                              int length,
                                              String replacementText) {
    final int offset = answerPlaceholder.getOffset();
    CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(offset, offset + length, replacementText);
      FileDocumentManager.getInstance().saveDocument(document);
    }));
  }

  public static void deleteWindowDescriptions(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      VirtualFile virtualFile = taskDir.findFileByRelativePath(name);
      if (virtualFile == null) {
        continue;
      }
      String windowsFileName = virtualFile.getNameWithoutExtension() + EduNames.WINDOWS_POSTFIX;
      VirtualFile parentDir = virtualFile.getParent();
      deleteWindowsFile(parentDir, windowsFileName);
    }
  }

  private static void deleteWindowsFile(@NotNull final VirtualFile taskDir, @NotNull final String name) {
    final VirtualFile fileWindows = taskDir.findChild(name);
    if (fileWindows != null && fileWindows.exists()) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          fileWindows.delete(taskDir);
        }
        catch (IOException e) {
          LOG.warn("Tried to delete non existed _windows file");
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

  public static void runUndoableAction(Project project, String name, UndoableAction action) {
    runUndoableAction(project, name, action, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
  }

  public static void runUndoableAction(Project project, String name, UndoableAction action, UndoConfirmationPolicy confirmationPolicy) {
    new WriteCommandAction(project, name) {
      protected void run(@NotNull final Result result) throws Throwable {
        action.redo();
        UndoManager.getInstance(project).undoableActionPerformed(action);
      }

      @Override
      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return confirmationPolicy;
      }
    }.execute();
  }
}
