package com.jetbrains.python.edu.course;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.jetbrains.python.edu.StudyDocumentListener;
import com.jetbrains.python.edu.StudyTestRunner;
import com.jetbrains.python.edu.StudyUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Implementation of windows which user should type in
 */


public class TaskWindow implements Comparable, Stateful {
  private static final String WINDOW_POSTFIX = "_window.py";
  private static final Logger LOG = Logger.getInstance(TaskWindow.class);
  public int line = 0;
  public int start = 0;
  public String hint = "";
  public String possibleAnswer = "";
  public int length = 0;
  private TaskFile myTaskFile;
  public int myIndex = -1;
  public int myInitialLine = -1;
  public int myInitialStart = -1;
  public int myInitialLength = -1;
  public StudyStatus myStatus = StudyStatus.Unchecked;

  public StudyStatus getStatus() {
    return myStatus;
  }

  public void setStatus(StudyStatus status, StudyStatus oldStatus) {
    myStatus = status;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }

  public int getStart() {
    return start;
  }

  public void setStart(int start) {
    this.start = start;
  }

  public void setLine(int line) {
    this.line = line;
  }

  public int getLine() {
    return line;
  }


  /**
   * Draw task window with color according to its status
   */
  public void draw(@NotNull final Editor editor, boolean drawSelection, boolean moveCaret) {
    Document document = editor.getDocument();
    if (!isValid(document)) {
      return;
    }
    TextAttributes defaultTestAttributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
    JBColor color = getColor();
    int startOffset = document.getLineStartOffset(line) + start;
    RangeHighlighter
      rh = editor.getMarkupModel().addRangeHighlighter(startOffset, startOffset + length, HighlighterLayer.LAST + 1,
                                                       new TextAttributes(defaultTestAttributes.getForegroundColor(),
                                                                          defaultTestAttributes.getBackgroundColor(), color,
                                                                          defaultTestAttributes.getEffectType(),
                                                                          defaultTestAttributes.getFontType()),
                                                       HighlighterTargetArea.EXACT_RANGE);
    if (drawSelection) {
      editor.getSelectionModel().setSelection(startOffset, startOffset + length);
    }
    if (moveCaret) {
      editor.getCaretModel().moveToOffset(startOffset);
    }
    rh.setGreedyToLeft(true);
    rh.setGreedyToRight(true);
  }

  public boolean isValid(@NotNull final Document document) {
    boolean isLineValid = line < document.getLineCount() && line >= 0;
    if (!isLineValid) return false;
    boolean isStartValid = start >= 0 && start < document.getLineEndOffset(line);
    boolean isLengthValid = (getRealStartOffset(document) + length) <= document.getTextLength();
    return isLengthValid && isStartValid;
  }

  private JBColor getColor() {
    if (myStatus == StudyStatus.Solved) {
      return JBColor.GREEN;
    }
    if (myStatus == StudyStatus.Failed) {
      return JBColor.RED;
    }
    return JBColor.BLUE;
  }

  public int getRealStartOffset(@NotNull final Document document) {
    return document.getLineStartOffset(line) + start;
  }

  /**
   * Initializes window
   *
   * @param file task file which window belongs to
   */
  public void init(final TaskFile file, boolean isRestarted) {
    if (!isRestarted) {
      myInitialLine = line;
      myInitialLength = length;
      myInitialStart = start;
    }
    myTaskFile = file;
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  @Override
  public int compareTo(@NotNull Object o) {
    TaskWindow taskWindow = (TaskWindow)o;
    if (taskWindow.getTaskFile() != myTaskFile) {
      throw new ClassCastException();
    }
    int lineDiff = line - taskWindow.line;
    if (lineDiff == 0) {
      return start - taskWindow.start;
    }
    return lineDiff;
  }

  /**
   * Returns window to its initial state
   */
  public void reset() {
    myStatus = StudyStatus.Unchecked;
    line = myInitialLine;
    start = myInitialStart;
    length = myInitialLength;
  }

  public String getHint() {
    return hint;
  }

  public String getPossibleAnswer() {
    return possibleAnswer;
  }

  public void setPossibleAnswer(String possibleAnswer) {
    this.possibleAnswer = possibleAnswer;
  }

  public int getIndex() {
    return myIndex;
  }

  public void smartCheck(@NotNull final Project project,
                         @NotNull final VirtualFile answerFile,
                         @NotNull final TaskFile answerTaskFile,
                         @NotNull final TaskFile usersTaskFile,
                         @NotNull final StudyTestRunner testRunner,
                         @NotNull final VirtualFile virtualFile,
                         @NotNull final Document usersDocument) {

    try {
      VirtualFile windowCopy =
        answerFile.copy(this, answerFile.getParent(), answerFile.getNameWithoutExtension() + WINDOW_POSTFIX);
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document windowDocument = documentManager.getDocument(windowCopy);
      if (windowDocument != null) {
        File resourceFile = StudyUtils.copyResourceFile(virtualFile.getName(), windowCopy.getName(), project, usersTaskFile.getTask());
        TaskFile windowTaskFile = new TaskFile();
        TaskFile.copy(answerTaskFile, windowTaskFile);
        StudyDocumentListener listener = new StudyDocumentListener(windowTaskFile);
        windowDocument.addDocumentListener(listener);
        int start = getRealStartOffset(windowDocument);
        int end = start + getLength();
        TaskWindow userTaskWindow = usersTaskFile.getTaskWindows().get(getIndex());
        int userStart = userTaskWindow.getRealStartOffset(usersDocument);
        int userEnd = userStart + userTaskWindow.getLength();
        String text = usersDocument.getText(new TextRange(userStart, userEnd));
        windowDocument.replaceString(start, end, text);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            documentManager.saveDocument(windowDocument);
          }
        });
        VirtualFile fileWindows = StudyUtils.flushWindows(windowTaskFile, windowCopy);
        Process smartTestProcess = testRunner.launchTests(project, windowCopy.getPath());
        boolean res = testRunner.getPassedTests(smartTestProcess).equals(StudyTestRunner.TEST_OK);
        userTaskWindow.setStatus(res ? StudyStatus.Solved : StudyStatus.Failed, StudyStatus.Unchecked);
        StudyUtils.deleteFile(windowCopy);
        StudyUtils.deleteFile(fileWindows);
        if (!resourceFile.delete()) {
          LOG.error("failed to delete", resourceFile.getPath());
        }
      }
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}