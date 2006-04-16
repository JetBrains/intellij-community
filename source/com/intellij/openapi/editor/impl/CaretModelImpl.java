/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 18, 2002
 * Time: 9:12:05 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class CaretModelImpl implements CaretModel, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.CaretModelImpl");
  private EditorImpl myEditor;
  private CopyOnWriteArrayList<CaretListener> myCaretListeners = new CopyOnWriteArrayList<CaretListener>();
  private LogicalPosition myLogicalCaret;
  private VisualPosition myVisibleCaret;
  private int myOffset;
  private int myVisualLineStart;
  private int myVisualLineEnd;
  private TextAttributes myTextAttributes;
  private boolean myIsInUpdate;

  public CaretModelImpl(EditorImpl editor) {
    myEditor = editor;
    myLogicalCaret = new LogicalPosition(0, 0);
    myVisibleCaret = new VisualPosition(0, 0);
    myOffset = 0;
    myVisualLineStart = 0;
    Document doc = editor.getDocument();
    if (doc.getLineCount() > 1) {
      myVisualLineEnd = doc.getLineStartOffset(1);
    }
    else {
      myVisualLineEnd = doc.getLineCount() == 0 ? 0 : doc.getLineEndOffset(0);
    }
  }

  public void moveToVisualPosition(VisualPosition pos) {
    validateCallContext();
    int column = pos.column;
    int line = pos.line;

    if (column < 0) column = 0;

    if (line < 0) line = 0;

    int lastLine = myEditor.getVisibleLineCount() - 1;
    if (lastLine <= 0) {
      lastLine = 0;
    }

    if (line > lastLine) {
      line = lastLine;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace() && line <= lastLine) {
      int lineEndColumn = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      if (column > lineEndColumn) {
        column = lineEndColumn;
      }

      if (column < 0 && line > 0) {
        line--;
        column = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      }
    }

    int oldY = myEditor.visibleLineNumberToYPosition(myVisibleCaret.line);

    myVisibleCaret = new VisualPosition(line, column);

    LogicalPosition oldPosition = myLogicalCaret;

    myLogicalCaret = myEditor.visualToLogicalPosition(myVisibleCaret);
    myOffset = myEditor.logicalPositionToOffset(myLogicalCaret);
    LOG.assertTrue(myOffset >= 0 && myOffset <= myEditor.getDocument().getTextLength());

    myVisualLineStart =
    myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd =
    myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));

    ((FoldingModelImpl)myEditor.getFoldingModel()).flushCaretPosition();

    myEditor.setLastColumnNumber(myVisibleCaret.column);
    myEditor.updateCaretCursor();
    requestRepaint(oldY);

    if (oldPosition.column != myLogicalCaret.column || oldPosition.line != myLogicalCaret.line) {
      CaretEvent event = new CaretEvent(myEditor, oldPosition, myLogicalCaret);

      for (CaretListener listener : myCaretListeners) {
        listener.caretPositionChanged(event);
      }
    }
  }

  public void moveToOffset(int offset) {
    validateCallContext();
    moveToLogicalPosition(myEditor.offsetToLogicalPosition(offset));
    if (myOffset != offset) {
      LOG.error("caret moved to wrong offset. Requested:" + offset + " but actual:" + myOffset);
    }
  }

  public void moveCaretRelatively(int columnShift,
                                  int lineShift,
                                  boolean withSelection,
                                  boolean blockSelection,
                                  boolean scrollToCaret) {
    SelectionModel selectionModel = myEditor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : getLogicalPosition();
    EditorSettings editorSettings = myEditor.getSettings();
    VisualPosition visualCaret = getVisualPosition();

    int newColumnNumber = visualCaret.column + columnShift;
    int newLineNumber = visualCaret.line + lineShift;

    if (!editorSettings.isVirtualSpace() && columnShift == 0) {
      newColumnNumber = myEditor.getLastColumnNumber();
    }
    else if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == 1) {
      int lastLine = myEditor.getDocument().getLineCount() - 1;
      if (lastLine < 0) lastLine = 0;
      if (EditorModificationUtil.calcAfterLineEnd(myEditor) >= 0 &&
          newLineNumber < myEditor.logicalToVisualPosition(new LogicalPosition(lastLine, 0)).line) {
        newColumnNumber = 0;
        newLineNumber++;
      }
    }
    else if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == -1) {
      if (newColumnNumber < 0 && newLineNumber > 0) {
        newLineNumber--;
        newColumnNumber = EditorUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber);
      }
    }

    if (newColumnNumber < 0) newColumnNumber = 0;
    if (newLineNumber < 0) newLineNumber = 0;

    int lastColumnNumber = newColumnNumber;
    if (!editorSettings.isCaretInsideTabs()) {
      LogicalPosition log = myEditor.visualToLogicalPosition(new VisualPosition(newLineNumber, newColumnNumber));
      int offset = myEditor.logicalPositionToOffset(log);
      CharSequence text = myEditor.getDocument().getCharsSequence();
      if (offset >= 0 && offset < myEditor.getDocument().getTextLength()) {
        if (text.charAt(offset) == '\t') {
          if (columnShift <= 0) {
            newColumnNumber = myEditor.offsetToVisualPosition(offset).column;
          }
          else {
            if (myEditor.offsetToVisualPosition(offset).column < newColumnNumber) {
              newColumnNumber = myEditor.offsetToVisualPosition(offset + 1).column;
            }
          }
        }
      }
    }

    VisualPosition pos = new VisualPosition(newLineNumber, newColumnNumber);
    moveToVisualPosition(pos);

    if (!editorSettings.isVirtualSpace() && columnShift == 0) {
      myEditor.setLastColumnNumber(lastColumnNumber);
    }

    if (withSelection) {
      if (blockSelection) {
        selectionModel.setBlockSelection(blockSelectionStart, getLogicalPosition());
      }
      else {
        selectionModel.setSelection(selectionStart, getOffset());
      }
    }
    else {
      selectionModel.removeSelection();
    }

    if (scrollToCaret) {
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  public void moveToLogicalPosition(LogicalPosition pos) {
    validateCallContext();
    int column = pos.column;
    int line = pos.line;

    Document doc = myEditor.getDocument();

    if (column < 0) column = 0;
    if (line < 0) line = 0;

    int lineCount = doc.getLineCount();
    if (lineCount == 0) {
      line = 0;
    }
    else if (line > lineCount - 1) {
      line = lineCount - 1;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace() && line < lineCount) {
      int lineEndOffset = doc.getLineEndOffset(line);
      int lineEndColumnNumber = myEditor.offsetToLogicalPosition(lineEndOffset).column;
      if (column > lineEndColumnNumber) {
        column = lineEndColumnNumber;
      }
    }

    ((FoldingModelImpl)myEditor.getFoldingModel()).flushCaretPosition();

    int oldY = myEditor.visibleLineNumberToYPosition(myVisibleCaret.line);

    LogicalPosition oldCaretPosition = myLogicalCaret;

    myLogicalCaret = new LogicalPosition(line, column);

    final int offset = myEditor.logicalPositionToOffset(myLogicalCaret);

    FoldRegion collapsedAt = ((FoldingModelImpl)myEditor.getFoldingModel()).getCollapsedRegionAtOffset(offset);

    if (collapsedAt != null && offset > collapsedAt.getStartOffset()) {
      Runnable runnable = new Runnable() {
        public void run() {
          FoldRegion[] allCollapsedAt = ((FoldingModelImpl)myEditor.getFoldingModel()).fetchCollapsedAt(offset);
          for (FoldRegion foldRange : allCollapsedAt) {
            foldRange.setExpanded(true);
          }
        }
      };

      myEditor.getFoldingModel().runBatchFoldingOperation(runnable);
    }

    myEditor.setLastColumnNumber(myLogicalCaret.column);
    myVisibleCaret = myEditor.logicalToVisualPosition(myLogicalCaret);

    myOffset = myEditor.logicalPositionToOffset(myLogicalCaret);
    LOG.assertTrue(myOffset >= 0 && myOffset <= myEditor.getDocument().getTextLength());

    myVisualLineStart =
    myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd =
    myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));

    myEditor.updateCaretCursor();
    requestRepaint(oldY);

    if (oldCaretPosition.column != myLogicalCaret.column || oldCaretPosition.line != myLogicalCaret.line) {
      CaretEvent event = new CaretEvent(myEditor, oldCaretPosition, myLogicalCaret);
      for (CaretListener listener : myCaretListeners) {
        listener.caretPositionChanged(event);
      }
    }
  }

  private void requestRepaint(int oldY) {
    int newY = myEditor.visibleLineNumberToYPosition(myVisibleCaret.line);
    int lineHeight = myEditor.getLineHeight();
    Rectangle visibleRect = myEditor.getScrollingModel().getVisibleArea();

    if (Math.abs(newY - oldY) <= 2 * lineHeight) {
      int minY = Math.min(oldY, newY);
      int maxY = Math.max(oldY + lineHeight, newY + lineHeight);
      ((EditorComponentImpl)myEditor.getContentComponent()).repaintEditorComponent(0, minY,
                                                                                   myEditor.getScrollPane()
                                                                                   .getHorizontalScrollBar()
                                                                                   .getValue() +
                                                                                   visibleRect.width,
                                                                                   maxY - minY);
    }
    else {
      ((EditorComponentImpl)myEditor.getContentComponent()).repaintEditorComponent(0, oldY,
                                                                                   myEditor.getScrollPane()
                                                                                   .getHorizontalScrollBar()
                                                                                   .getValue() +
                                                                                   visibleRect.width,
                                                                                   2 * lineHeight);
      ((EditorComponentImpl)myEditor.getContentComponent()).repaintEditorComponent(0, newY,
                                                                                   myEditor.getScrollPane()
                                                                                   .getHorizontalScrollBar()
                                                                                   .getValue() +
                                                                                   visibleRect.width,
                                                                                   2 * lineHeight);
    }
  }

  public LogicalPosition getLogicalPosition() {
    validateCallContext();
    return myLogicalCaret;
  }

  private void validateCallContext() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(!myIsInUpdate, "Caret model is in its update process. All requests are illegal at this point.");
  }

  public VisualPosition getVisualPosition() {
    validateCallContext();
    return myVisibleCaret;
  }

  public int getOffset() {
    validateCallContext();
    return myOffset;
  }

  public int getVisualLineStart() {
    return myVisualLineStart;
  }

  public int getVisualLineEnd() {
    return myVisualLineEnd;
  }

  public void addCaretListener(CaretListener listener) {
    myCaretListeners.add(listener);
  }

  public void removeCaretListener(CaretListener listener) {
    boolean success = myCaretListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public TextAttributes getTextAttributes() {
    if (myTextAttributes == null) {
      myTextAttributes = new TextAttributes();
      myTextAttributes.setBackgroundColor(myEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR));
    }

    return myTextAttributes;
  }

  public void reinitSettings() {
    myTextAttributes = null;
  }

  public void documentChanged(DocumentEvent e) {
    myIsInUpdate = false;

    DocumentEventImpl event = (DocumentEventImpl)e;
    if (event.isWholeTextReplaced()) {
      int newLength = myEditor.getDocument().getTextLength();
      if (myOffset == newLength - e.getNewLength() + e.getOldLength()) {
        moveToOffset(newLength);
      }
      else {
        final int line = event.translateLineViaDiff(myLogicalCaret.line);
        moveToLogicalPosition(new LogicalPosition(line, myLogicalCaret.column));
      }
    }
    else {
      int startOffset = e.getOffset();
      int oldEndOffset = startOffset + e.getOldLength();

      int newOffset = myOffset;
      VisualPosition oldPosition = getVisualPosition();

      if (myOffset > oldEndOffset || myOffset >= oldEndOffset && e.getOldLength() > 0) {
        newOffset += e.getNewLength() - e.getOldLength();
      }
      else if (myOffset >= startOffset && myOffset <= oldEndOffset) {
        newOffset = Math.min(newOffset, startOffset + e.getNewLength());
      }

      newOffset = Math.min(newOffset, myEditor.getDocument().getTextLength());

      if (newOffset != myOffset) {
        moveToOffset(newOffset);
      }
      else {
        moveToVisualPosition(oldPosition);
      }
    }

    myVisualLineStart =
    myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd =
    myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));
  }

  public void beforeDocumentChange(DocumentEvent e) {
    myIsInUpdate = true;
  }

  public int getPriority() {
    return 3;
  }
}
