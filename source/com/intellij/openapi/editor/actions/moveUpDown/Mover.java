package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

abstract class Mover {
  protected final boolean myIsDown;
  @NotNull protected LineRange whatToMove;
  protected int insertOffset;    // -1 means we cannot move, e.g method outside class
  protected int myInsertStartAfterCutOffset;
  protected int myInsertEndAfterCutOffset;
  protected int myDeleteStartAfterMoveOffset;

  protected Mover(final boolean isDown) {
    myIsDown = isDown;
  }

  /**
   * @return false if this mover is unable to find a place to move stuff at,
   * otherwise, initialize fields and returns true
   */
  protected abstract boolean checkAvailable(Editor editor, PsiFile file);

  protected void beforeMove(final Editor editor) {

  }
  protected void afterMove(final Editor editor, final PsiFile file) {

  }

  public void move(Editor editor, final PsiFile file) {
    beforeMove(editor);
    final LineRange lineRange = whatToMove;
    final int startLine = lineRange.startLine;
    final int endLine = lineRange.endLine;

    final Document document = editor.getDocument();
    final int start = editor.logicalPositionToOffset(new LogicalPosition(startLine, 0));
    final int end = editor.logicalPositionToOffset(new LogicalPosition(endLine+1, 0));
    String toInsert = document.getCharsSequence().subSequence(start, end).toString();
    if (!StringUtil.endsWithChar(toInsert, '\n')) {
      toInsert += '\n';
    }
    myInsertStartAfterCutOffset = myIsDown ? insertOffset - toInsert.length() : insertOffset;
    myInsertEndAfterCutOffset = myInsertStartAfterCutOffset + toInsert.length();
    myDeleteStartAfterMoveOffset = myIsDown ? start : start + toInsert.length();

    final CaretModel caretModel = editor.getCaretModel();
    final int caretRelativePos = caretModel.getOffset() - start;
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    final boolean hasSelection = selectionModel.hasSelection();

    // to prevent flicker
    caretModel.moveToOffset(0);

    document.deleteString(start, end);
    document.insertString(myInsertStartAfterCutOffset, toInsert);
    final Project project = editor.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    if (hasSelection) {
      restoreSelection(editor, selectionStart, selectionEnd, start, myInsertStartAfterCutOffset);
    }

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final int line1 = editor.offsetToLogicalPosition(myInsertStartAfterCutOffset).line;
    final int line2 = editor.offsetToLogicalPosition(myInsertEndAfterCutOffset).line;
    caretModel.moveToOffset(myInsertStartAfterCutOffset + caretRelativePos);

    for (int line = line1; line <= line2; line++) {
      if (lineContainsNonSpaces(document, line)) {
        int lineStart = document.getLineStartOffset(line);
        int oldLineLength = document.getLineEndOffset(line) - lineStart;
        codeStyleManager.adjustLineIndent(document, lineStart);
        int newLineLength = document.getLineEndOffset(line) - lineStart;
        myInsertEndAfterCutOffset += newLineLength - oldLineLength;
        if (!myIsDown) {
          myDeleteStartAfterMoveOffset += newLineLength - oldLineLength;
        }
      }
    }

    afterMove(editor, file);
    whatToMove.firstElement = null;
    whatToMove.lastElement = null;
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static boolean lineContainsNonSpaces(final Document document, final int line) {
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line);
    @NonNls String text = document.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString();
    return !text.matches("^\\s*$");
  }

  private static void restoreSelection(final Editor editor, final int selectionStart, final int selectionEnd, final int moveOffset, int insOffset) {
    final int selectionRelativeOffset = selectionStart - moveOffset;
    int newSelectionStart = insOffset + selectionRelativeOffset;
    int newSelectionEnd = newSelectionStart + selectionEnd - selectionStart;
    editor.getSelectionModel().setSelection(newSelectionStart, newSelectionEnd);
  }


}
