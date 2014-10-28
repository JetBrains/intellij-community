package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskFile {
  @Expose public List<TaskWindow> task_windows = new ArrayList<TaskWindow>();
  public int myIndex;

  public TaskFile() {
  }

  public void addTaskWindow(@NotNull final TaskWindow taskWindow, int index) {
    taskWindow.setIndex(index);
    task_windows.add(taskWindow);
  }

  public List<TaskWindow> getTaskWindows() {
    return task_windows;
  }

  public void setIndex(int index) {
    myIndex = index;
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
    for (TaskWindow tw : task_windows) {
      if (tw.getLine() <= line) {
        int twStartOffset = tw.getRealStartOffset(document);
        final int length = tw.getReplacementLength() > 0 ? tw.getReplacementLength() : 0;
        int twEndOffset = twStartOffset + length;
        if (twStartOffset <= offset && offset <= twEndOffset) {
          return tw;
        }
      }
    }
    return null;
  }

  public void copy(@NotNull final TaskFile target) {
    target.setIndex(myIndex);
    for (TaskWindow taskWindow : task_windows) {
      TaskWindow savedWindow = new TaskWindow(taskWindow.getLine(), taskWindow.getStart(),
                                              taskWindow.getLength(), "");
      target.getTaskWindows().add(savedWindow);
      savedWindow.setIndex(taskWindow.getIndex());
      savedWindow.setReplacementLength(taskWindow.getReplacementLength());
    }
  }

  public void update(@NotNull final TaskFile source) {
    for (TaskWindow taskWindow : source.getTaskWindows()) {
      TaskWindow taskWindowUpdated = getTaskWindow(taskWindow.getIndex());
      if (taskWindowUpdated == null) {
        break;
      }
      taskWindowUpdated.setLine(taskWindow.getLine());
      taskWindowUpdated.setStart(taskWindow.getStart());
      taskWindowUpdated.setReplacementLength(taskWindow.getReplacementLength());
      taskWindowUpdated.setLength(taskWindow.getLength());
    }
  }

 @Nullable
 public TaskWindow getTaskWindow(int index) {
    for (TaskWindow taskWindow : task_windows) {
      if (taskWindow.getIndex() == index) {
        return taskWindow;
      }
    }
   return null;
  }

  /**
   * Marks symbols adjacent to task windows as read-only fragments
   */
  public void createGuardedBlocks(@NotNull final Editor editor) {
    for (TaskWindow taskWindow : task_windows) {
      taskWindow.createGuardedBlocks(editor);
    }
  }

  public void sortTaskWindows() {
    Collections.sort(task_windows);
    for (int i = 0; i < task_windows.size(); i++) {
      task_windows.get(i).setIndex(i + 1);
    }
  }
}
