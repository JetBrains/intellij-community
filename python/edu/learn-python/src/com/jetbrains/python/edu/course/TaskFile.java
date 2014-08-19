package com.jetbrains.python.edu.course;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.python.edu.StudyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of task file which contains task windows for student to type in and
 * which is visible to student in project view
 */

public class TaskFile implements Stateful{
  public List<TaskWindow> taskWindows = new ArrayList<TaskWindow>();
  private Task myTask;
  @Transient
  private TaskWindow mySelectedTaskWindow = null;
  public int myIndex = -1;
  private boolean myUserCreated = false;

  /**
   * @return if all the windows in task file are marked as resolved
   */
  @Transient
  public StudyStatus getStatus() {
    for (TaskWindow taskWindow : taskWindows) {
      StudyStatus windowStatus = taskWindow.getStatus();
      if (windowStatus == StudyStatus.Failed) {
        return StudyStatus.Failed;
      }
      if (windowStatus == StudyStatus.Unchecked) {
        return StudyStatus.Unchecked;
      }
    }
    return StudyStatus.Solved;
  }

  public Task getTask() {
    return myTask;
  }

  @Nullable
  @Transient
  public TaskWindow getSelectedTaskWindow() {
    return mySelectedTaskWindow;
  }

  /**
   * @param selectedTaskWindow window from this task file to be set as selected
   */
  public void setSelectedTaskWindow(@NotNull final TaskWindow selectedTaskWindow) {
    if (selectedTaskWindow.getTaskFile() == this) {
      mySelectedTaskWindow = selectedTaskWindow;
    }
    else {
      throw new IllegalArgumentException("Window may be set as selected only in task file which it belongs to");
    }
  }

  public List<TaskWindow> getTaskWindows() {
    return taskWindows;
  }

  /**
   * Creates task files in its task folder in project user created
   *
   * @param taskDir      project directory of task which task file belongs to
   * @param resourceRoot directory where original task file stored
   * @throws java.io.IOException
   */
  public void create(@NotNull final VirtualFile taskDir, @NotNull final File resourceRoot,
                     @NotNull final String name) throws IOException {
    String systemIndependentName = FileUtil.toSystemIndependentName(name);
    final int index = systemIndependentName.lastIndexOf("/");
    if (index > 0) {
      systemIndependentName = systemIndependentName.substring(index + 1);
    }
    File resourceFile = new File(resourceRoot, name);
    File fileInProject = new File(taskDir.getPath(), systemIndependentName);
    FileUtil.copy(resourceFile, fileInProject);
  }

  public void drawAllWindows(Editor editor) {
    for (TaskWindow taskWindow : taskWindows) {
      taskWindow.draw(editor, false, false);
    }
  }


  /**
   * @param pos position in editor
   * @return task window located in specified position or null if there is no task window in this position
   */
  @Nullable
  public TaskWindow getTaskWindow(@NotNull final Document document, @NotNull final LogicalPosition pos) {
    int line = pos.line;
    if (line >= document.getLineCount()) {
      return null;
    }
    int column = pos.column;
    int offset = document.getLineStartOffset(line) + column;
    for (TaskWindow tw : taskWindows) {
      if (tw.getLine() <= line) {
        int twStartOffset = tw.getRealStartOffset(document);
        final int length = tw.getLength() > 0 ? tw.getLength() : 0;
        int twEndOffset = twStartOffset + length;
        if (twStartOffset <= offset && offset <= twEndOffset) {
          return tw;
        }
      }
    }
    return null;
  }

  /**
   * Updates task window lines
   *
   * @param startLine lines greater than this line and including this line will be updated
   * @param change    change to be added to line numbers
   */
  public void incrementLines(int startLine, int change) {
    for (TaskWindow taskTaskWindow : taskWindows) {
      if (taskTaskWindow.getLine() >= startLine) {
        taskTaskWindow.setLine(taskTaskWindow.getLine() + change);
      }
    }
  }

  /**
   * Initializes state of task file
   *
   * @param task task which task file belongs to
   */

  public void init(final Task task, boolean isRestarted) {
    myTask = task;
    for (TaskWindow taskWindow : taskWindows) {
      taskWindow.init(this, isRestarted);
    }
    Collections.sort(taskWindows);
    for (int i = 0; i < taskWindows.size(); i++) {
      taskWindows.get(i).setIndex(i);
    }
  }

  /**
   * @param index index of task file in list of task files of its task
   */
  public void setIndex(int index) {
    myIndex = index;
  }

  /**
   * Updates windows in specific line
   *
   * @param lineChange         change in line number
   * @param line               line to be updated
   * @param newEndOffsetInLine distance from line start to end of inserted fragment
   * @param oldEndOffsetInLine distance from line start to end of changed fragment
   */
  public void updateLine(int lineChange, int line, int newEndOffsetInLine, int oldEndOffsetInLine) {
    for (TaskWindow w : taskWindows) {
      if ((w.getLine() == line) && (w.getStart() >= oldEndOffsetInLine)) {
        int distance = w.getStart() - oldEndOffsetInLine;
        if (lineChange != 0 || newEndOffsetInLine <= w.getStart()) {
          w.setStart(distance + newEndOffsetInLine);
          w.setLine(line + lineChange);
        }
      }
    }
  }

  public static void copy(@NotNull final TaskFile source, @NotNull final TaskFile target) {
    List<TaskWindow> sourceTaskWindows = source.getTaskWindows();
    List<TaskWindow> windowsCopy = new ArrayList<TaskWindow>(sourceTaskWindows.size());
    for (TaskWindow taskWindow : sourceTaskWindows) {
      TaskWindow taskWindowCopy = new TaskWindow();
      taskWindowCopy.setLine(taskWindow.getLine());
      taskWindowCopy.setStart(taskWindow.getStart());
      taskWindowCopy.setLength(taskWindow.getLength());
      taskWindowCopy.setPossibleAnswer(taskWindow.getPossibleAnswer());
      taskWindowCopy.setIndex(taskWindow.getIndex());
      windowsCopy.add(taskWindowCopy);
    }
    target.setTaskWindows(windowsCopy);
  }

  public void setTaskWindows(List<TaskWindow> taskWindows) {
    this.taskWindows = taskWindows;
  }

  public void setStatus(@NotNull final StudyStatus status, @NotNull final StudyStatus oldStatus) {
    for (TaskWindow taskWindow : taskWindows) {
      taskWindow.setStatus(status, oldStatus);
    }
  }

  public void setUserCreated(boolean userCreated) {
    myUserCreated = userCreated;
  }

  public boolean isUserCreated() {
    return myUserCreated;
  }

  public void navigateToFirstTaskWindow(@NotNull final Editor editor) {
    if (!taskWindows.isEmpty()) {
      TaskWindow firstTaskWindow = StudyUtils.getFirst(taskWindows);
      mySelectedTaskWindow = firstTaskWindow;
      LogicalPosition taskWindowStart = new LogicalPosition(firstTaskWindow.getLine(), firstTaskWindow.getStart());
      editor.getCaretModel().moveToLogicalPosition(taskWindowStart);
      int startOffset = firstTaskWindow.getRealStartOffset(editor.getDocument());
      int endOffset = startOffset + firstTaskWindow.getLength();
      editor.getSelectionModel().setSelection(startOffset, endOffset);
    }
  }
}
