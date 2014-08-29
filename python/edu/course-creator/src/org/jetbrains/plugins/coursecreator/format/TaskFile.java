package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.CCProjectService;

import java.util.ArrayList;
import java.util.List;

public class TaskFile {
  @Expose public List<TaskWindow> task_windows = new ArrayList<TaskWindow>();
  public int myIndex;
  public boolean myTrackChanges = true;

  public boolean isTrackChanges() {
    return myTrackChanges;
  }

  public void setTrackChanges(boolean trackChanges) {
    myTrackChanges = trackChanges;
  }

  public TaskFile() {}

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

  /**
   * Updates task window lines
   *
   * @param startLine lines greater than this line and including this line will be updated
   * @param change    change to be added to line numbers
   */
  public void incrementLines(int startLine, int change) {
    for (TaskWindow taskTaskWindow : task_windows) {
      if (taskTaskWindow.getLine() >= startLine) {
        taskTaskWindow.setLine(taskTaskWindow.getLine() + change);
      }
    }
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
    for (TaskWindow w : task_windows) {
      if ((w.getLine() == line) && (w.getStart() >= oldEndOffsetInLine)) {
        int distance = w.getStart() - oldEndOffsetInLine;
        boolean coveredByPrevTW = false;
        int prevIndex = w.getIndex() - 1;
        if (CCProjectService.indexIsValid(prevIndex, task_windows)) {
          TaskWindow prevTW = task_windows.get(prevIndex);
          if (prevTW.getLine() == line) {
            int endOffset = prevTW.getStart() + prevTW.getLength();
            if (endOffset >= newEndOffsetInLine) {
              coveredByPrevTW = true;
            }
          }
        }
        if (lineChange != 0 || newEndOffsetInLine <= w.getStart() || coveredByPrevTW) {
          w.setStart(distance + newEndOffsetInLine);
          w.setLine(line + lineChange);
        }
      }
    }
  }
}
