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
  protected final boolean isDown;
  @NotNull protected LineRange toMove;
  protected LineRange toMove2; // can be null if the move is illegal
  protected RangeMarker range1;
  protected RangeMarker range2;

  protected Mover(final boolean isDown) {
    this.isDown = isDown;
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

  public final void move(Editor editor, final PsiFile file) {
    beforeMove(editor);
    final Document document = editor.getDocument();
    final int start = getLineStartSafeOffset(document, toMove.startLine);
    final int end = getLineStartSafeOffset(document, toMove.endLine);
    range1 = document.createRangeMarker(start, end);

    String textToInsert = document.getCharsSequence().subSequence(start, end).toString();
    if (!StringUtil.endsWithChar(textToInsert,'\n')) textToInsert += '\n';

    final int start2 = document.getLineStartOffset(toMove2.startLine);
    final int end2 = getLineStartSafeOffset(document,toMove2.endLine);
    String textToInsert2 = document.getCharsSequence().subSequence(start2, end2).toString();
    if (!StringUtil.endsWithChar(textToInsert2,'\n')) textToInsert2 += '\n';
    range2 = document.createRangeMarker(start2, end2);
    if (range1.getStartOffset() < range2.getStartOffset()) {
      range1.setGreedyToLeft(true);
      range1.setGreedyToRight(false);
      range2.setGreedyToLeft(true);
      range2.setGreedyToRight(true);
    }
    else {
      range1.setGreedyToLeft(true);
      range1.setGreedyToRight(true);
      range2.setGreedyToLeft(true);
      range2.setGreedyToRight(false);
    }

    final CaretModel caretModel = editor.getCaretModel();
    final int caretRelativePos = caretModel.getOffset() - start;
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    final boolean hasSelection = selectionModel.hasSelection();

    // to prevent flicker
    caretModel.moveToOffset(0);

    document.insertString(range1.getStartOffset(), textToInsert2);
    document.deleteString(range1.getStartOffset()+textToInsert2.length(), range1.getEndOffset());

    document.insertString(range2.getStartOffset(), textToInsert);
    document.deleteString(range2.getStartOffset()+textToInsert.length(), range2.getEndOffset());

    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    if (hasSelection) {
      restoreSelection(editor, selectionStart, selectionEnd, start, range2.getStartOffset());
    }

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final int line1 = editor.offsetToLogicalPosition(range2.getStartOffset()).line;
    final int line2 = editor.offsetToLogicalPosition(range2.getEndOffset()).line;
    caretModel.moveToOffset(range2.getStartOffset() + caretRelativePos);

    for (int line = line1; line <= line2; line++) {
      if (lineContainsNonSpaces(document, line)) {
        int lineStart = document.getLineStartOffset(line);
        codeStyleManager.adjustLineIndent(document, lineStart);
      }
    }

    afterMove(editor, file);
    toMove.firstElement = null;
    toMove.lastElement = null;
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  protected static int getLineStartSafeOffset(final Document document, int line) {
    if (line == document.getLineCount()) return document.getTextLength();
    return document.getLineStartOffset(line);
  }

  private static boolean lineContainsNonSpaces(final Document document, final int line) {
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line);
    @NonNls String text = document.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString();
    return text.trim().length() != 0;
  }

  private static void restoreSelection(final Editor editor, final int selectionStart, final int selectionEnd, final int moveOffset, int insOffset) {
    final int selectionRelativeOffset = selectionStart - moveOffset;
    int newSelectionStart = insOffset + selectionRelativeOffset;
    int newSelectionEnd = newSelectionStart + selectionEnd - selectionStart;
    editor.getSelectionModel().setSelection(newSelectionStart, newSelectionEnd);
  }
}
