package com.jetbrains.edu.learning;

import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.SaveAndSyncHandlerImpl;
import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.course.*;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.ui.StudyToolWindowFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.Collection;

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

  public static void synchronize() {
    FileDocumentManager.getInstance().saveAllDocuments();
    SaveAndSyncHandlerImpl.refreshOpenFiles();
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
  }

  /**
   * Gets number index in directory names like "task1", "lesson2"
   *
   * @param fullName    full name of directory
   * @param logicalName part of name without index
   * @return index of object
   */
  public static int getIndex(@NotNull final String fullName, @NotNull final String logicalName) {
    if (!fullName.contains(logicalName)) {
      throw new IllegalArgumentException();
    }
    return Integer.parseInt(fullName.substring(logicalName.length())) - 1;
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
      final String name = file.getNameWithoutExtension() + "_windows";
      PrintWriter printWriter = null;
      try {
        fileWindows = taskDir.createChildData(taskFile, name);
        printWriter = new PrintWriter(new FileOutputStream(fileWindows.getPath()));
        for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
          if (!answerPlaceholder.isValid(document)) {
            printWriter.println("#educational_plugin_window = ");
            continue;
          }
          int start = answerPlaceholder.getRealStartOffset(document);
          final String windowDescription = document.getText(new TextRange(start, start + answerPlaceholder.getLength()));
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
        closeSilently(printWriter);
        synchronize();
      }
    }
    return fileWindows;
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
    int lessonNum = task.getLesson().getIndex() + 1;
    assert course != null;
    final String pathToResource = FileUtil.join(course.getCourseDirectory(), StudyNames.LESSON_DIR + lessonNum, Task.TASK_DIR + taskNum);
    final File resourceFile = new File(pathToResource, copyName);
    FileUtil.copy(new File(pathToResource, sourceName), resourceFile);
    return resourceFile;
  }

  @Nullable
  public static Sdk findSdk(@NotNull final Task task, @NotNull final Project project) {
    final Language language = task.getLesson().getCourse().getLanguageById();
    return StudyExecutor.INSTANCE.forLanguage(language).findSdk(project);
  }

  public static void markDirAsSourceRoot(@NotNull final VirtualFile dir, @NotNull final Project project) {
    final Module module = ModuleUtilCore.findModuleForFile(dir, project);
    if (module == null) {
      LOG.info("Module for " + dir.getPath() + " was not found");
      return;
    }
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final ContentEntry entry = MarkRootActionBase.findContentEntry(model, dir);
    if (entry == null) {
      LOG.info("Content entry for " + dir.getPath() + " was not found");
      return;
    }
    entry.addSourceFolder(dir, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
        module.getProject().save();
      }
    });
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

  public static void enableAction(@NotNull final AnActionEvent event, boolean isEnable) {
    final Presentation presentation = event.getPresentation();
    presentation.setVisible(isEnable);
    presentation.setEnabled(isEnable);
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
}
