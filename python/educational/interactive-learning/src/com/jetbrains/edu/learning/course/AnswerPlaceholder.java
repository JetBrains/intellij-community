package com.jetbrains.edu.learning.course;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.jetbrains.edu.learning.StudyDocumentListener;
import com.jetbrains.edu.learning.StudyTestRunner;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Implementation of windows which user should type in
 */

public class AnswerPlaceholder implements Comparable, Stateful {
  private static final String WINDOW_POSTFIX = "_window";
  private static final Logger LOG = Logger.getInstance(AnswerPlaceholder.class);
  private int line = 0;
  private int start = 0;
  private String hint = "";
  private String possibleAnswer = "";
  private int length = 0;
  private TaskFile myTaskFile;
  private int myIndex = -1;
  private int myInitialLine = -1;
  private int myInitialStart = -1;
  private int myInitialLength = -1;
  private StudyStatus myStatus = StudyStatus.Unchecked;

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
  public void draw(@NotNull final Editor editor) {
    Document document = editor.getDocument();
    if (!isValid(document)) {
      return;
    }
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes defaultTestAttributes = new TextAttributes(scheme.getDefaultForeground(), scheme.getDefaultBackground(), null,
                                                                    EffectType.BOXED, Font.PLAIN);
    final JBColor color = getColor();
    int startOffset = document.getLineStartOffset(line) + start;
    RangeHighlighter
      highlighter = editor.getMarkupModel().addRangeHighlighter(startOffset, startOffset + length, HighlighterLayer.LAST + 1,
                                                       defaultTestAttributes,
                                                       HighlighterTargetArea.EXACT_RANGE);
    highlighter.setCustomRenderer(new CustomHighlighterRenderer() {
      @Override
      public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
        g.setColor(color);
        Point point = editor.logicalPositionToXY(editor.offsetToLogicalPosition(highlighter.getStartOffset()));
        Point pointEnd = editor.logicalPositionToXY(editor.offsetToLogicalPosition(highlighter.getEndOffset()));
        g.drawRect(point.x, point.y, (pointEnd.x - point.x), editor.getLineHeight() + 1);
      }
    });
    editor.getCaretModel().moveToOffset(startOffset);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);
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
    AnswerPlaceholder answerPlaceholder = (AnswerPlaceholder)o;
    if (answerPlaceholder.getTaskFile() != myTaskFile) {
      throw new ClassCastException();
    }
    int lineDiff = line - answerPlaceholder.line;
    if (lineDiff == 0) {
      return start - answerPlaceholder.start;
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

  public void setHint(@NotNull final String hint) {
    this.hint = hint;
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
      final VirtualFile windowCopy =
        answerFile.copy(this, answerFile.getParent(), answerFile.getNameWithoutExtension() + myIndex + WINDOW_POSTFIX + "." + answerFile.getExtension());
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document windowDocument = documentManager.getDocument(windowCopy);
      if (windowDocument != null) {
        final File resourceFile = StudyUtils.copyResourceFile(virtualFile.getName(), windowCopy.getName(), project, usersTaskFile.getTask());
        final TaskFile windowTaskFile = new TaskFile();
        TaskFile.copy(answerTaskFile, windowTaskFile);
        StudyDocumentListener listener = new StudyDocumentListener(windowTaskFile);
        windowDocument.addDocumentListener(listener);
        int start = getRealStartOffset(windowDocument);
        int end = start + getLength();
        final AnswerPlaceholder userAnswerPlaceholder = usersTaskFile.getAnswerPlaceholders().get(getIndex());
        int userStart = userAnswerPlaceholder.getRealStartOffset(usersDocument);
        int userEnd = userStart + userAnswerPlaceholder.getLength();
        String text = usersDocument.getText(new TextRange(userStart, userEnd));
        windowDocument.replaceString(start, end, text);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            documentManager.saveDocument(windowDocument);
          }
        });
        VirtualFile fileWindows = StudyUtils.flushWindows(windowTaskFile, windowCopy);
        Process smartTestProcess = testRunner.createCheckProcess(project, windowCopy.getPath());
        final CapturingProcessHandler handler = new CapturingProcessHandler(smartTestProcess);
        final ProcessOutput output = handler.runProcess();
        boolean res = testRunner.getTestsOutput(output).equals(StudyTestRunner.TEST_OK);
        userAnswerPlaceholder.setStatus(res ? StudyStatus.Solved : StudyStatus.Failed, StudyStatus.Unchecked);
        StudyUtils.deleteFile(windowCopy);
        if (fileWindows != null) {
          StudyUtils.deleteFile(fileWindows);
        }
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