package com.jetbrains.edu.coursecreator.format;

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
  @Expose private List<AnswerPlaceholder> task_windows = new ArrayList<AnswerPlaceholder>();
  private int myIndex;

  public TaskFile() {
  }

  public void addTaskWindow(@NotNull final AnswerPlaceholder answerPlaceholder, int index) {
    answerPlaceholder.setIndex(index);
    task_windows.add(answerPlaceholder);
  }

  public List<AnswerPlaceholder> getTaskWindows() {
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
  public AnswerPlaceholder getTaskWindow(@NotNull final Document document, @NotNull final LogicalPosition pos) {
    int line = pos.line;
    if (line >= document.getLineCount()) {
      return null;
    }
    int column = pos.column;
    int offset = document.getLineStartOffset(line) + column;
    for (AnswerPlaceholder tw : task_windows) {
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
    for (AnswerPlaceholder answerPlaceholder : task_windows) {
      AnswerPlaceholder savedWindow = new AnswerPlaceholder(answerPlaceholder.getLine(), answerPlaceholder.getStart(),
                                              answerPlaceholder.getLength(), "");
      target.getTaskWindows().add(savedWindow);
      savedWindow.setIndex(answerPlaceholder.getIndex());
      savedWindow.setReplacementLength(answerPlaceholder.getReplacementLength());
    }
  }

  public void update(@NotNull final TaskFile source) {
    for (AnswerPlaceholder answerPlaceholder : source.getTaskWindows()) {
      AnswerPlaceholder answerPlaceholderUpdated = getTaskWindow(answerPlaceholder.getIndex());
      if (answerPlaceholderUpdated == null) {
        break;
      }
      answerPlaceholderUpdated.setLine(answerPlaceholder.getLine());
      answerPlaceholderUpdated.setStart(answerPlaceholder.getStart());
      answerPlaceholderUpdated.setReplacementLength(answerPlaceholder.getReplacementLength());
      answerPlaceholderUpdated.setLength(answerPlaceholder.getLength());
    }
  }

 @Nullable
 private AnswerPlaceholder getTaskWindow(int index) {
    for (AnswerPlaceholder answerPlaceholder : task_windows) {
      if (answerPlaceholder.getIndex() == index) {
        return answerPlaceholder;
      }
    }
   return null;
  }

  /**
   * Marks symbols adjacent to task windows as read-only fragments
   */
  public void createGuardedBlocks(@NotNull final Editor editor) {
    for (AnswerPlaceholder answerPlaceholder : task_windows) {
      answerPlaceholder.createGuardedBlocks(editor);
    }
  }

  public void sortTaskWindows() {
    Collections.sort(task_windows);
    for (int i = 0; i < task_windows.size(); i++) {
      task_windows.get(i).setIndex(i + 1);
    }
  }

  public List<AnswerPlaceholder> getTask_windows() {
    return task_windows;
  }

  public void setTask_windows(List<AnswerPlaceholder> task_windows) {
    this.task_windows = task_windows;
  }

  public int getIndex() {
    return myIndex;
  }
}
