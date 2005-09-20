package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

abstract class Mover {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.Mover");

  protected final boolean myIsDown;
  @NotNull protected LineRange whatToMove;
  protected int insertOffset;    // -1 means we cannot move, e.g method outside class

  protected Mover(final boolean isDown) {
    myIsDown = isDown;
  }

  /**
   * @return false if this mover is unable to find a place to move stuff at,
   * otherwise, initialize fields and returns true
   */
  protected abstract boolean checkAvailable(Editor editor, PsiFile file);

  protected void beforeMove() {

  }
  protected void afterMove() {

  }

  public void move(Editor editor, final PsiFile file) {
    beforeMove();
    final LineRange lineRange = whatToMove;
    final int startLine = lineRange.startLine;
    final int endLine = lineRange.endLine;

    final Document document = editor.getDocument();
    final int start = editor.logicalPositionToOffset(new LogicalPosition(startLine, 0));
    final int end = editor.logicalPositionToOffset(new LogicalPosition(endLine+1, 0));
    final String toInsert = document.getCharsSequence().subSequence(start, end).toString();
    final int insStartAfterCut = myIsDown ? insertOffset - toInsert.length() : insertOffset;
    final int insEndAfterCut = insStartAfterCut + toInsert.length();

    final CaretModel caretModel = editor.getCaretModel();
    final int caretRelativePos = caretModel.getOffset() - start;
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    final boolean hasSelection = selectionModel.hasSelection();

    // to prevent flicker
    caretModel.moveToOffset(0);

    document.deleteString(start, end);
    document.insertString(insStartAfterCut, toInsert);
    final Project project = editor.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    afterMove();

    if (hasSelection) {
      restoreSelection(editor, selectionStart, selectionEnd, start, insStartAfterCut);
    }

    try {
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      final int line1 = editor.offsetToLogicalPosition(insStartAfterCut).line;
      final int line2 = editor.offsetToLogicalPosition(insEndAfterCut).line;
      caretModel.moveToOffset(insStartAfterCut + caretRelativePos);

      for (int line = line1; line <= line2; line++) {
        if (lineContainsNonSpaces(document, line)) {
          int lineStart = document.getLineStartOffset(line);
          codeStyleManager.adjustLineIndent(file, lineStart);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static boolean lineContainsNonSpaces(final Document document, final int line) {
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line);
    String text = document.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString();
    return !text.matches("^\\s*$");
  }

  private static void restoreSelection(final Editor editor, final int selectionStart, final int selectionEnd, final int moveOffset, int insOffset) {
    final int selectionRelativeOffset = selectionStart - moveOffset;
    int newSelectionStart = insOffset + selectionRelativeOffset;
    int newSelectionEnd = newSelectionStart + selectionEnd - selectionStart;
    editor.getSelectionModel().setSelection(newSelectionStart, newSelectionEnd);
  }


}
