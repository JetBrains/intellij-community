package com.jetbrains.edu.learning;

import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.SaveAndSyncHandlerImpl;
import com.intellij.ide.projectView.actions.MarkRootActionBase;
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

import java.io.*;
import java.util.Collection;

public class StudyUtils {
  private StudyUtils() {
  }

  private static final Logger LOG = Logger.getInstance(StudyUtils.class.getName());

  public static void closeSilently(Closeable stream) {
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

  public static <T> T getFirst(Iterable<T> container) {
    return container.iterator().next();
  }

  public static boolean indexIsValid(int index, Collection collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static String getFileText(String parentDir, String fileName, boolean wrapHTML, String encoding) {

    File inputFile = parentDir != null ? new File(parentDir, fileName) : new File(fileName);
    if (!inputFile.exists()) return null;
    StringBuilder taskText = new StringBuilder();
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

  public static void updateAction(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(false);
    Project project = e.getProject();
    if (project != null) {
      FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
      for (FileEditor editor : editors) {
        if (editor instanceof StudyEditor) {
          presentation.setEnabled(true);
          presentation.setVisible(true);
        }
      }
    }
  }

  public static void updateStudyToolWindow(Project project) {
    ToolWindowManager.getInstance(project).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW).getContentManager()
      .removeAllContents(false);
    StudyToolWindowFactory factory = new StudyToolWindowFactory();
    factory
      .createToolWindowContent(project, ToolWindowManager.getInstance(project).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW));
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
  public static VirtualFile flushWindows(TaskFile taskFile, VirtualFile file) {
    VirtualFile taskDir = file.getParent();
    VirtualFile fileWindows = null;
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      LOG.debug("Couldn't flush windows");
      return null;
    }
    if (taskDir != null) {
      String name = file.getNameWithoutExtension() + "_windows";
      PrintWriter printWriter = null;
      try {
        fileWindows = taskDir.createChildData(taskFile, name);
        printWriter = new PrintWriter(new FileOutputStream(fileWindows.getPath()));
        for (TaskWindow taskWindow : taskFile.getTaskWindows()) {
          if (!taskWindow.isValid(document)) {
            printWriter.println("#educational_plugin_window = ");
            continue;
          }
          int start = taskWindow.getRealStartOffset(document);
          String windowDescription = document.getText(new TextRange(start, start + taskWindow.getLength()));
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

  public static void deleteFile(VirtualFile file) {
    try {
      file.delete(StudyUtils.class);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static File copyResourceFile(String sourceName, String copyName, Project project, Task task)
    throws IOException {
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    Course course = taskManager.getCourse();
    int taskNum = task.getIndex() + 1;
    int lessonNum = task.getLesson().getIndex() + 1;
    assert course != null;
    String pathToResource =
      FileUtil.join(new File(course.getResourcePath()).getParent(), Lesson.LESSON_DIR + lessonNum, Task.TASK_DIR + taskNum);
    File resourceFile = new File(pathToResource, copyName);
    FileUtil.copy(new File(pathToResource, sourceName), resourceFile);
    return resourceFile;
  }

  @Nullable
  public static Sdk findSdk(@NotNull final Project project) {
    final StudyUtilsExtensionPoint[] extensions =
      ApplicationManager.getApplication().getExtensions(StudyUtilsExtensionPoint.EP_NAME);
    if (extensions.length > 0) {
      return extensions[0].findSdk(project);
    }
    return null;
  }

  public static void markDirAsSourceRoot(@NotNull final VirtualFile dir, @NotNull final Project project) {
    final Module module = ModuleUtilCore.findModuleForFile(dir, project);
    if (module == null) {
      LOG.info("Module for " + dir.getPath() + " was not found");
      return;
    }
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    ContentEntry entry = MarkRootActionBase.findContentEntry(model, dir);
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

  public static StudyTestRunner getTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    final StudyUtilsExtensionPoint[] extensions =
      ApplicationManager.getApplication().getExtensions(StudyUtilsExtensionPoint.EP_NAME);
    if (extensions.length > 0) {
      return extensions[0].getTestRunner(task, taskDir);
    }
    return null;
  }

  public static String getLinkToTutorial() {
    final StudyUtilsExtensionPoint[] extensions =
      ApplicationManager.getApplication().getExtensions(StudyUtilsExtensionPoint.EP_NAME);
    if (extensions.length > 0) {
      return extensions[0].getLinkToTutorial();
    }
    return null;
  }

  public static RunContentExecutor getExecutor(@NotNull final Project project, @NotNull final ProcessHandler handler) {
    final StudyUtilsExtensionPoint[] extensions =
      ApplicationManager.getApplication().getExtensions(StudyUtilsExtensionPoint.EP_NAME);
    if (extensions.length > 0) {
      return extensions[0].getExecutor(project, handler);
    }
    return null;
  }

  public static void setCommandLineParameters(@NotNull final GeneralCommandLine cmd,
                                               @NotNull final Project project,
                                               @NotNull final String filePath,
                                               @NotNull final String pythonPath,
                                               @NotNull final Task currentTask) {
  final StudyUtilsExtensionPoint[] extensions =
      ApplicationManager.getApplication().getExtensions(StudyUtilsExtensionPoint.EP_NAME);
    if (extensions.length > 0) {
      extensions[0].setCommandLineParameters(cmd, project, filePath, pythonPath, currentTask);
    }
  }

  public static void enableAction(@NotNull final AnActionEvent event, boolean isEnable) {
    final Presentation presentation = event.getPresentation();
    presentation.setVisible(isEnable);
    presentation.setEnabled(isEnable);
  }
}
