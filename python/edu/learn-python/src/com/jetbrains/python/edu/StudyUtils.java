package com.jetbrains.python.edu;

import com.intellij.ide.SaveAndSyncHandlerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.TaskWindow;
import com.jetbrains.python.edu.editor.StudyEditor;
import com.jetbrains.python.edu.ui.StudyToolWindowFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collection;

public class StudyUtils {
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
  public static String getFileText(String parentDir, String fileName, boolean wrapHTML) {

    File inputFile = parentDir !=null ? new File(parentDir, fileName) : new File(fileName);
    if (!inputFile.exists()) return null;
    StringBuilder taskText = new StringBuilder();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile)));
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
      LOG.error("Failed to get file text from file " + fileName, e);
    }
    finally {
      closeSilently(reader);
    }
    return null;
  }

  public static void updateAction(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    Project project = e.getProject();
    if (project != null) {
      FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
      for (FileEditor editor : editors) {
        if (editor instanceof StudyEditor) {
          presentation.setEnabled(true);
        }
      }
    }
  }

  public static void updateStudyToolWindow(Project project) {
    ToolWindowManager.getInstance(project).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW).getContentManager().removeAllContents(false);
    StudyToolWindowFactory factory =  new StudyToolWindowFactory();
    factory.createToolWindowContent(project, ToolWindowManager.getInstance(project).getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW));
  }

  public  static void synchronize() {
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
  public static VirtualFile flushWindows(Document document, TaskFile taskFile, VirtualFile file) {
    VirtualFile taskDir = file.getParent();
    VirtualFile fileWindows = null;
    if (taskDir != null) {
      String name = file.getNameWithoutExtension() + "_windows";
      PrintWriter printWriter = null;
      try {

        fileWindows = taskDir.createChildData(taskFile, name);
        printWriter = new PrintWriter(new FileOutputStream(fileWindows.getPath()));
        for (TaskWindow taskWindow : taskFile.getTaskWindows()) {
          if (!taskWindow.isValid(document)) {
            continue;
          }
          int start = taskWindow.getRealStartOffset(document);
          String windowDescription = document.getText(new TextRange(start, start + taskWindow.getLength()));
          printWriter.println("#study_plugin_window = " + windowDescription);
        }
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
}
