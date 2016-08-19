package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
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
  @Expose public String name;
  @Expose public String text;
  private int myIndex = -1;
  private boolean myUserCreated = false;
  private boolean myTrackChanges = true;
  private boolean myHighlightErrors = false;
  @Expose @SerializedName("placeholders") private List<AnswerPlaceholder> myAnswerPlaceholders = new ArrayList<>();
  @Transient private Task myTask;

  public TaskFile() {
  }

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
   * @param offset position in editor
   * @return answer placeholder located in specified position or null if there is no task window in this position
   */
  @Nullable
  public AnswerPlaceholder getAnswerPlaceholder(int offset) {
    for (AnswerPlaceholder placeholder : myAnswerPlaceholders) {
      int placeholderStart = placeholder.getOffset();
      int placeholderEnd = placeholderStart + placeholder.getRealLength();
      if (placeholderStart <= offset && offset <= placeholderEnd) {
        return placeholder;
      }
    }
    return null;
  }


  public static void copy(@NotNull final TaskFile source, @NotNull final TaskFile target) {
    List<AnswerPlaceholder> sourceAnswerPlaceholders = source.getAnswerPlaceholders();
    List<AnswerPlaceholder> answerPlaceholdersCopy = new ArrayList<>(sourceAnswerPlaceholders.size());
    for (AnswerPlaceholder answerPlaceholder : sourceAnswerPlaceholders) {
      AnswerPlaceholder answerPlaceholderCopy = new AnswerPlaceholder();
      answerPlaceholderCopy.setTaskText(answerPlaceholder.getTaskText());
      answerPlaceholderCopy.setOffset(answerPlaceholder.getOffset());
      answerPlaceholderCopy.setLength(answerPlaceholder.getLength());
      answerPlaceholderCopy.setPossibleAnswer(answerPlaceholder.getPossibleAnswer());
      answerPlaceholderCopy.setIndex(answerPlaceholder.getIndex());
      answerPlaceholderCopy.setHints(answerPlaceholder.getHints());
      final AnswerPlaceholder.MyInitialState state = answerPlaceholder.getInitialState();
      if (state != null) {
        answerPlaceholderCopy.setInitialState(new AnswerPlaceholder.MyInitialState(state.getOffset(), state.getLength()));
      }
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
      myAnswerPlaceholders.get(i).setIndex(i);
    }
  }

  public boolean hasFailedPlaceholders() {
    for (AnswerPlaceholder placeholder : myAnswerPlaceholders) {
      if (placeholder.getStatus() == StudyStatus.Failed) {
        return true;
      }
    }
    return false;
  }
}
