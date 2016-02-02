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
    return getAnswerPlaceholder(document, pos, false);
  }

  @Nullable
  public AnswerPlaceholder getAnswerPlaceholder(@NotNull final Document document, @NotNull final LogicalPosition pos,
                                                boolean useAnswerLength) {
    int line = pos.line;
    if (line >= document.getLineCount()) {
      return null;
    }
    int column = pos.column;
    int offset = document.getLineStartOffset(line) + column;
    for (AnswerPlaceholder placeholder : myAnswerPlaceholders) {
      if (placeholder.getLine() <= line) {
        int realStartOffset = placeholder.getRealStartOffset(document);
        int placeholderLength = useAnswerLength ? placeholder.getPossibleAnswerLength() : placeholder.getLength();
        final int length = placeholderLength > 0 ? placeholderLength : 0;
        int endOffset = realStartOffset + length;
        if (realStartOffset <= offset && offset <= endOffset) {
          return placeholder;
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
    target.name = source.name;
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


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TaskFile that = (TaskFile)o;

    if (getIndex() != that.getIndex()) return false;
    if (!name.equals(that.name)) return false;

    final List<AnswerPlaceholder> answerPlaceholders = getAnswerPlaceholders();
    final List<AnswerPlaceholder> thatAnswerPlaceholders = that.getAnswerPlaceholders();
    if (answerPlaceholders.size() != thatAnswerPlaceholders.size()) return false;
    for (int i = 0; i < answerPlaceholders.size(); i++) {
      final AnswerPlaceholder placeholder = answerPlaceholders.get(i);
      final AnswerPlaceholder thatPlaceholder = thatAnswerPlaceholders.get(i);
      if (!placeholder.equals(thatPlaceholder)) return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = getIndex();
    result = 31 * result + name.hashCode();
    for (AnswerPlaceholder placeholder : myAnswerPlaceholders) {
      result = 31 * result + placeholder.hashCode();
    }
    return result;
  }
}
