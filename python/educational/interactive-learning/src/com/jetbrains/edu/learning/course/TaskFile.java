package com.jetbrains.edu.learning.course;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.TaskWindowDeleteHandler;
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

public class TaskFile implements Stateful {
  public String name;
  public String text;
  @SerializedName("placeholders")
  private List<AnswerPlaceholder> myAnswerPlaceholders = new ArrayList<AnswerPlaceholder>();

  private Task myTask;
  @Transient
  private AnswerPlaceholder mySelectedAnswerPlaceholder = null;
  private int myIndex = -1;
  private boolean myUserCreated = false;
  private boolean myTrackChanges = true;
  private boolean myHighlightErrors = false;

  /**
   * @return if all the windows in task file are marked as resolved
   */
  @Transient
  public StudyStatus getStatus() {
    for (AnswerPlaceholder answerPlaceholder : myAnswerPlaceholders) {
      StudyStatus windowStatus = answerPlaceholder.getStatus();
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
  public AnswerPlaceholder getSelectedAnswerPlaceholder() {
    return mySelectedAnswerPlaceholder;
  }

  /**
   * @param selectedAnswerPlaceholder window from this task file to be set as selected
   */
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

  /**
   * Creates task files in its task folder in project user created
   *
   * @param taskDir      project directory of task which task file belongs to
   * @param resourceRoot directory where original task file stored
   * @throws IOException
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
    editor.getMarkupModel().removeAllHighlighters();
    for (AnswerPlaceholder answerPlaceholder : myAnswerPlaceholders) {
      answerPlaceholder.draw(editor);
    }
    final Document document = editor.getDocument();
    EditorActionManager.getInstance()
      .setReadonlyFragmentModificationHandler(document, new TaskWindowDeleteHandler(editor));
    createGuardedBlocks(editor);
    editor.getColorsScheme().setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null);
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

  /**
   * Initializes state of task file
   *
   * @param task task which task file belongs to
   */

  public void init(final Task task, boolean isRestarted) {
    myTask = task;
    for (AnswerPlaceholder answerPlaceholder : myAnswerPlaceholders) {
      answerPlaceholder.init(this, isRestarted);
    }
    Collections.sort(myAnswerPlaceholders);
    for (int i = 0; i < myAnswerPlaceholders.size(); i++) {
      myAnswerPlaceholders.get(i).setIndex(i);
    }
  }

  /**
   * @param index index of task file in list of task files of its task
   */
  public void setIndex(int index) {
    myIndex = index;
  }


  public static void copy(@NotNull final TaskFile source, @NotNull final TaskFile target) {
    List<AnswerPlaceholder> sourceAnswerPlaceholders = source.getAnswerPlaceholders();
    List<AnswerPlaceholder> windowsCopy = new ArrayList<AnswerPlaceholder>(sourceAnswerPlaceholders.size());
    for (AnswerPlaceholder answerPlaceholder : sourceAnswerPlaceholders) {
      AnswerPlaceholder answerPlaceholderCopy = new AnswerPlaceholder();
      answerPlaceholderCopy.setLine(answerPlaceholder.getLine());
      answerPlaceholderCopy.setStart(answerPlaceholder.getStart());
      answerPlaceholderCopy.setLength(answerPlaceholder.getLength());
      answerPlaceholderCopy.setPossibleAnswer(answerPlaceholder.getPossibleAnswer());
      answerPlaceholderCopy.setIndex(answerPlaceholder.getIndex());
      windowsCopy.add(answerPlaceholderCopy);
    }
    target.setAnswerPlaceholders(windowsCopy);
  }

  public void setAnswerPlaceholders(List<AnswerPlaceholder> answerPlaceholders) {
    this.myAnswerPlaceholders = answerPlaceholders;
  }

  public void setStatus(@NotNull final StudyStatus status, @NotNull final StudyStatus oldStatus) {
    for (AnswerPlaceholder answerPlaceholder : myAnswerPlaceholders) {
      answerPlaceholder.setStatus(status, oldStatus);
    }
  }

  public void setUserCreated(boolean userCreated) {
    myUserCreated = userCreated;
  }

  public boolean isUserCreated() {
    return myUserCreated;
  }

  public void navigateToFirstTaskWindow(@NotNull final Editor editor) {
    if (!myAnswerPlaceholders.isEmpty()) {
      AnswerPlaceholder firstAnswerPlaceholder = StudyUtils.getFirst(myAnswerPlaceholders);
      navigateToTaskWindow(editor, firstAnswerPlaceholder);
    }
  }

  public void navigateToTaskWindow(@NotNull final Editor editor, @NotNull final AnswerPlaceholder answerPlaceholder) {
    if (!answerPlaceholder.isValid(editor.getDocument())) {
      return;
    }
    mySelectedAnswerPlaceholder = answerPlaceholder;
    LogicalPosition taskWindowStart = new LogicalPosition(answerPlaceholder.getLine(), answerPlaceholder.getStart());
    editor.getCaretModel().moveToLogicalPosition(taskWindowStart);
  }

  public void navigateToFirstFailedTaskWindow(@NotNull final Editor editor) {
    for (AnswerPlaceholder answerPlaceholder : myAnswerPlaceholders) {
      if (answerPlaceholder.getStatus() != StudyStatus.Failed) {
        continue;
      }
      navigateToTaskWindow(editor, answerPlaceholder);
      break;
    }
  }

  public boolean hasFailedTaskWindows() {
    return myAnswerPlaceholders.size() > 0 && getStatus() == StudyStatus.Failed;
  }

  /**
   * Marks symbols adjacent to task windows as read-only fragments
   */
  public void createGuardedBlocks(@NotNull final Editor editor) {
    final Document document = editor.getDocument();
    if (document instanceof DocumentImpl) {
      DocumentImpl documentImpl = (DocumentImpl)document;
      List<RangeMarker> blocks = documentImpl.getGuardedBlocks();
      for (AnswerPlaceholder answerPlaceholder : myAnswerPlaceholders) {
        if (!answerPlaceholder.isValid(document)) {
          return;
        }
        int start = answerPlaceholder.getRealStartOffset(document);
        int end = start + answerPlaceholder.getLength();
        if (start != 0) {
          createGuardedBlock(editor, blocks, start - 1, start);
        }
        if (end != document.getTextLength()) {
          createGuardedBlock(editor, blocks, end, end + 1);
        }
      }
    }
  }

  private static void createGuardedBlock(Editor editor, List<RangeMarker> blocks, int start, int end) {
    RangeHighlighter rh = editor.getMarkupModel()
      .addRangeHighlighter(start, end, HighlighterLayer.LAST + 1, null, HighlighterTargetArea.EXACT_RANGE);
    blocks.add(rh);
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
}
