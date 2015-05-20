package com.jetbrains.edu.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of task file which contains task windows for student to type in and
 * which is visible to student in project view
 */

public class TaskFile {
  @SerializedName("placeholders")
  @Expose
  private List<AnswerPlaceholder> myAnswerPlaceholders = new ArrayList<AnswerPlaceholder>();
  private int myIndex = -1;

  @Expose
  public String name;
  @Expose
  public String text;
  @Transient private Task myTask;
  @Transient
  private AnswerPlaceholder mySelectedAnswerPlaceholder = null;
  private boolean myUserCreated = false;
  private boolean myTrackChanges = true;
  private boolean myHighlightErrors = false;

  public void initTaskFile(final Task task, boolean isRestarted) {
    setTask(task);
    final List<AnswerPlaceholder> answerPlaceholders = getAnswerPlaceholders();
    for (AnswerPlaceholder answerPlaceholder : answerPlaceholders) {
      answerPlaceholder.initAnswerPlaceholder(this, isRestarted);
    }
    Collections.sort(answerPlaceholders, new AnswerPlaceholderComparator());
    for (int i = 0; i < answerPlaceholders.size(); i++) {
      answerPlaceholders.get(i).setIndex(i);
    }
  }

  @Nullable
  @Transient
  public AnswerPlaceholder getSelectedAnswerPlaceholder() {
    return mySelectedAnswerPlaceholder;
  }

  public void setSelectedAnswerPlaceholder(@NotNull final AnswerPlaceholder selectedAnswerPlaceholder) {
    if (selectedAnswerPlaceholder.getTaskFile() == this) {
      mySelectedAnswerPlaceholder = selectedAnswerPlaceholder;
    }
    else {
      throw new IllegalArgumentException("Window may be set as selected only in task file which it belongs to");
    }
  }

  public List<AnswerPlaceholder> getAnswerPlaceholders() {
    return myAnswerPlaceholders;
  }

  public void setAnswerPlaceholders(List<AnswerPlaceholder> answerPlaceholders) {
    this.myAnswerPlaceholders = answerPlaceholders;
  }

  public void addAnswerPlaceholder(AnswerPlaceholder answerPlaceholder) {
    myAnswerPlaceholders.add(answerPlaceholder);
  }

  public int getIndex() {
    return myIndex;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  @Transient
  public Task getTask() {
    return myTask;
  }

  @Transient
  public void setTask(Task task) {
    myTask = task;
  }

  /**
   * @param pos position in editor
   * @return task window located in specified position or null if there is no task window in this position
   */
  @Nullable
  public AnswerPlaceholder getAnswerPlaceholder(@NotNull final Document document, @NotNull final LogicalPosition pos) {
    int line = pos.line;
    if (line >= document.getLineCount()) {
      return null;
    }
    int column = pos.column;
    int offset = document.getLineStartOffset(line) + column;
    for (AnswerPlaceholder tw : myAnswerPlaceholders) {
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


  public static void copy(@NotNull final TaskFile source, @NotNull final TaskFile target) {
    List<AnswerPlaceholder> sourceAnswerPlaceholders = source.getAnswerPlaceholders();
    List<AnswerPlaceholder> answerPlaceholdersCopy = new ArrayList<AnswerPlaceholder>(sourceAnswerPlaceholders.size());
    for (AnswerPlaceholder answerPlaceholder : sourceAnswerPlaceholders) {
      AnswerPlaceholder answerPlaceholderCopy = new AnswerPlaceholder();
      answerPlaceholderCopy.setLine(answerPlaceholder.getLine());
      answerPlaceholderCopy.setStart(answerPlaceholder.getStart());
      answerPlaceholderCopy.setTaskText(answerPlaceholder.getTaskText());
      answerPlaceholderCopy.setLength(answerPlaceholder.getLength());
      answerPlaceholderCopy.setPossibleAnswer(answerPlaceholder.getPossibleAnswer());
      answerPlaceholderCopy.setIndex(answerPlaceholder.getIndex());
      answerPlaceholdersCopy.add(answerPlaceholderCopy);
    }
    target.setAnswerPlaceholders(answerPlaceholdersCopy);
  }

  public void setUserCreated(boolean userCreated) {
    myUserCreated = userCreated;
  }

  public boolean isUserCreated() {
    return myUserCreated;
  }

  public boolean isTrackChanges() {
    return myTrackChanges;
  }

  public void setTrackChanges(boolean trackChanges) {
    myTrackChanges = trackChanges;
  }

  public boolean isHighlightErrors() {
    return myHighlightErrors;
  }

  public void setHighlightErrors(boolean highlightErrors) {
    myHighlightErrors = highlightErrors;
  }

  public void sortAnswerPlaceholders() {
    Collections.sort(myAnswerPlaceholders, new AnswerPlaceholderComparator());
    for (int i = 0; i < myAnswerPlaceholders.size(); i++) {
      myAnswerPlaceholders.get(i).setIndex(i + 1);
    }
  }
}
