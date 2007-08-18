/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 1:51:41 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.CopyOnWriteArrayList;

public class SelectionModelImpl implements SelectionModel, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.SelectionModelImpl");

  private CopyOnWriteArrayList<SelectionListener> mySelectionListeners = new CopyOnWriteArrayList<SelectionListener>();
  private MyRangeMarker mySelectionMarker = null;
  private EditorImpl myEditor;
  private int myLastSelectionStart;
  private LogicalPosition myBlockStart;
  private LogicalPosition myBlockEnd;
  private TextAttributes myTextAttributes;
  private boolean myIsInUpdate;

  class MyRangeMarker extends RangeMarkerImpl {
    private boolean myIsReleased;

    public MyRangeMarker(Document document, int start, int end) {
      super(document, start, end);
      myIsReleased = false;
    }

    public void release() {
      myIsReleased = true;
    }

    public void documentChanged(DocumentEvent e) {
      if (!myIsReleased) {
        int startBefore = getStartOffset();
        int endBefore = getEndOffset();
        super.documentChanged(e);

        if (!isValid()) {
          myLastSelectionStart = myEditor.getCaretModel().getOffset();
          release();
          mySelectionMarker = null;
          fireSelectionChanged(startBefore, endBefore, myLastSelectionStart, myLastSelectionStart);
          return;
        }

        if (startBefore != getStartOffset() || endBefore != getStartOffset()) {
          fireSelectionChanged(startBefore, endBefore, getStartOffset(), getEndOffset());
        }
      }
    }

    protected void registerInDocument() {
    }
  }

  public void beforeDocumentChange(DocumentEvent event) {
    myIsInUpdate = true;
  }

  public void documentChanged(DocumentEvent event) {
    myIsInUpdate = false;
    if (mySelectionMarker != null && mySelectionMarker.isValid()) {
      mySelectionMarker.documentChanged(event);
    }
  }

  public int getPriority() {
    return 4;
  }

  public SelectionModelImpl(EditorImpl editor) {
    myEditor = editor;
  }

  public int getSelectionStart() {
    validateContext();
    if (!hasSelection()) return myEditor.getCaretModel().getOffset();
    return mySelectionMarker.getStartOffset();
  }

  private void validateContext() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(!myIsInUpdate, "Selection model is in its update stage. All requests are illegal at this point.");
  }

  public int getSelectionEnd() {
    validateContext();
    if (!hasSelection()) return myEditor.getCaretModel().getOffset();
    return mySelectionMarker.getEndOffset();
  }

  public boolean hasSelection() {
    validateContext();
    if (mySelectionMarker != null && !mySelectionMarker.isValid()) {
      removeSelection();
    }

    return mySelectionMarker != null;
  }

  public void setSelection(int startOffset, int endOffset) {
    validateContext();

    removeBlockSelection();
    Document doc = myEditor.getDocument();

    if (startOffset < 0 || startOffset > doc.getTextLength()) {
      LOG.assertTrue(false, "Wrong startOffset: " + startOffset);
    }
    if (endOffset < 0 || endOffset > doc.getTextLength()) {
      LOG.assertTrue(false, "Wrong endOffset: " + endOffset);
    }

    myLastSelectionStart = startOffset;
    if (startOffset == endOffset) {
      removeSelection();
      return;
    }

    /* Normalize selection */
    if (startOffset > endOffset) {
      int tmp = startOffset;
      startOffset = endOffset;
      endOffset = tmp;
    }

    if (!GraphicsEnvironment.isHeadless()) {
      final Toolkit toolkit = myEditor.getComponent().getToolkit();
      Clipboard clip = toolkit.getSystemSelection();
      if (clip != null) {
        clip.setContents(new StringSelection(getSelectedText()), defaultClipboardOwner);
      }
    }

    int oldSelectionStart;
    int oldSelectionEnd;

    if (hasSelection()) {
      oldSelectionStart = mySelectionMarker.getStartOffset();
      oldSelectionEnd = mySelectionMarker.getEndOffset();
      if (oldSelectionStart == startOffset && oldSelectionEnd == endOffset) return;
    }
    else {
      oldSelectionStart = oldSelectionEnd = myEditor.getCaretModel().getOffset();
    }

    if (mySelectionMarker != null) {
      mySelectionMarker.release();
    }

    mySelectionMarker = new MyRangeMarker(doc, startOffset, endOffset);

    fireSelectionChanged(oldSelectionStart, oldSelectionEnd, startOffset, endOffset);
  }

  private void fireSelectionChanged(int oldSelectionStart, int oldSelectionEnd, int startOffset, int endOffset) {
    repaintBySelectionChange(oldSelectionStart, startOffset, oldSelectionEnd, endOffset);

    SelectionEvent event = new SelectionEvent(myEditor,
                                              oldSelectionStart, oldSelectionEnd,
                                              startOffset, endOffset);

    for (SelectionListener listener : mySelectionListeners) {
      try {
        listener.selectionChanged(event);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void repaintBySelectionChange(int oldSelectionStart, int startOffset, int oldSelectionEnd, int endOffset) {
    myEditor.repaint(Math.min(oldSelectionStart, startOffset), Math.max(oldSelectionStart, startOffset));
    myEditor.repaint(Math.min(oldSelectionEnd, endOffset), Math.max(oldSelectionEnd, endOffset));
  }

  public void setBlockSelection(LogicalPosition blockStart, LogicalPosition blockEnd) {
    removeSelection();

    int oldStartLine = 0;
    int oldEndLine = 0;

    if (hasBlockSelection()) {
      oldStartLine = myBlockStart.line;
      oldEndLine = myBlockEnd.line;
      if (oldStartLine > oldEndLine) {
        int t = oldStartLine;
        oldStartLine = oldEndLine;
        oldEndLine = t;
      }
    }

    int newStartLine = blockStart.line;
    int newEndLine = blockEnd.line;

    if (newStartLine > newEndLine) {
      int t = newStartLine;
      newStartLine = newEndLine;
      newEndLine = t;
    }

    myEditor.repaintLines(Math.min(oldStartLine, newStartLine), Math.max(newEndLine, oldEndLine));
    myBlockStart = blockStart;
    myBlockEnd = blockEnd;
  }

  public void removeSelection() {
    validateContext();
    removeBlockSelection();
    myLastSelectionStart = myEditor.getCaretModel().getOffset();
    if (mySelectionMarker != null) {
      int startOffset = mySelectionMarker.getStartOffset();
      int endOffset = mySelectionMarker.getEndOffset();
      mySelectionMarker.release();
      mySelectionMarker = null;
      fireSelectionChanged(startOffset, endOffset, myLastSelectionStart, myLastSelectionStart);
    }
  }

  public void removeBlockSelection() {
    if (hasBlockSelection()) {
      myEditor.repaint(0, myEditor.getDocument().getTextLength());
      myBlockStart = null;
      myBlockEnd = null;
    }
  }

  public boolean hasBlockSelection() {
    return myBlockStart != null;
  }

  public LogicalPosition getBlockStart() {
    return myBlockStart;
  }

  public LogicalPosition getBlockEnd() {
    return myBlockEnd;
  }

  public boolean isBlockSelectionGuarded() {
    if (!hasBlockSelection()) return false;
    int[] starts = getBlockSelectionStarts();
    int[] ends = getBlockSelectionEnds();
    Document doc = myEditor.getDocument();
    for (int i = 0; i < starts.length; i++) {
      int start = starts[i];
      int end = ends[i];
      if (start == end && doc.getOffsetGuard(start) != null || start != end && doc.getRangeGuard(start, end) != null) {
        return true;
      }
    }
    return false;
  }

  public RangeMarker getBlockSelectionGuard() {
    if (!hasBlockSelection()) return null;

    int[] starts = getBlockSelectionStarts();
    int[] ends = getBlockSelectionEnds();
    Document doc = myEditor.getDocument();
    for (int i = 0; i < starts.length; i++) {
      int start = starts[i];
      int end = ends[i];
      if (start == end) {
        RangeMarker guard = doc.getOffsetGuard(start);
        if (guard != null) return guard;
      }
      if (start != end) {
        RangeMarker guard = doc.getRangeGuard(start, end);
        if (guard != null) return guard;
      }
    }

    return null;
  }

  @NotNull
  public int[] getBlockSelectionStarts() {
    if (hasSelection()) {
      return new int[]{getSelectionStart()};
    }
    if (!hasBlockSelection()) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }

    int lineCount = Math.abs(myBlockEnd.line - myBlockStart.line) + 1;
    int startLine = Math.min(myBlockStart.line, myBlockEnd.line);

    int startColumn = Math.min(myBlockStart.column, myBlockEnd.column);

    int[] res = new int[lineCount];
    for (int i = startLine; i < startLine + lineCount; i++) {
      res[i - startLine] = myEditor.logicalPositionToOffset(new LogicalPosition(i, startColumn));
    }

    return res;
  }

  @NotNull
  public int[] getBlockSelectionEnds() {
    if (hasSelection()) {
      return new int[]{getSelectionEnd()};
    }

    if (!hasBlockSelection()) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }

    int lineCount = Math.abs(myBlockEnd.line - myBlockStart.line) + 1;
    int startLine = Math.min(myBlockStart.line, myBlockEnd.line);

    int startColumn = Math.min(myBlockStart.column, myBlockEnd.column);
    int columnCount = Math.abs(myBlockEnd.column - myBlockStart.column);

    int[] res = new int[lineCount];
    for (int i = startLine; i < startLine + lineCount; i++) {
      res[i - startLine] = myEditor.logicalPositionToOffset(new LogicalPosition(i, startColumn + columnCount));
    }

    return res;
  }

  public void addSelectionListener(SelectionListener listener) {
    mySelectionListeners.add(listener);
  }

  public void removeSelectionListener(SelectionListener listener) {
    boolean success = mySelectionListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public String getSelectedText() {
    validateContext();
    if (!hasSelection() && !hasBlockSelection()) return null;

    CharSequence text = myEditor.getDocument().getCharsSequence();
    if (hasBlockSelection()) {
      int[] starts = getBlockSelectionStarts();
      int[] ends = getBlockSelectionEnds();
      int width = Math.abs(myBlockEnd.column - myBlockStart.column);
      final StringBuffer buf = new StringBuffer();
      for (int i = 0; i < starts.length; i++) {
        if (i > 0) buf.append('\n');
        final int len = ends[i] - starts[i];
        appendCharSequence(buf, text, starts[i], len);
        for (int j = len; j < width; j++) buf.append(' ');
      }
      return buf.toString();
    }

    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    return text.subSequence(selectionStart, selectionEnd).toString();
  }

  private static void appendCharSequence(StringBuffer buf, CharSequence s, int srcOffset, int len) {
    if (s == null){
      s = "null";
    }
    if (srcOffset < 0 || len < 0 || srcOffset > s.length() - len) {
      throw new IndexOutOfBoundsException("srcOffset " + srcOffset + ", len " + len + ", s.length() " + s.length());
    }
    if (len == 0) {
      return;
    }
    final int limit = srcOffset + len;
    for (int i = srcOffset; i < limit; i++){
      buf.append(s.charAt(i));
    }
  }


  public int getLeadSelectionOffset() {
    validateContext();
    int caretOffset = myEditor.getCaretModel().getOffset();
    if (!hasSelection()) return caretOffset;
    int startOffset = mySelectionMarker.getStartOffset();
    int endOffset = mySelectionMarker.getEndOffset();
    if (caretOffset == endOffset) return startOffset;
    return endOffset;
  }

  public void selectLineAtCaret() {
    validateContext();
    int lineNumber = myEditor.getCaretModel().getLogicalPosition().line;
    Document document = myEditor.getDocument();
    if (lineNumber >= document.getLineCount()) {
      return;
    }

    VisualPosition caret = myEditor.getCaretModel().getVisualPosition();
    LogicalPosition lineStart = myEditor.visualToLogicalPosition(new VisualPosition(caret.line, 0));
    LogicalPosition nextLineStart = myEditor.visualToLogicalPosition(new VisualPosition(caret.line + 1, 0));

    int start = myEditor.logicalPositionToOffset(lineStart);
    int end = myEditor.logicalPositionToOffset(nextLineStart);

    myEditor.getCaretModel().moveToOffset(start);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();
    myEditor.getSelectionModel().setSelection(start, end);
  }

  public void selectWordAtCaret(boolean honorCamelWordsSettings) {
    validateContext();
    removeSelection();
    final EditorSettings settings = myEditor.getSettings();
    boolean camelTemp = settings.isCamelWords();

    final boolean needOverrideSetting = camelTemp && !honorCamelWordsSettings;
    if (needOverrideSetting) {
      settings.setCamelWords(false);
    }

    EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(
      IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    handler.execute(myEditor, myEditor.getDataContext());

    if (needOverrideSetting) {
      settings.setCamelWords(camelTemp);
    }
  }

  int getWordAtCaretStart() {
    Document document = myEditor.getDocument();
    int offset = myEditor.getCaretModel().getOffset();
    if (offset == 0) return offset;
    int lineNumber = myEditor.getCaretModel().getLogicalPosition().line;
    CharSequence text = document.getCharsSequence();
    int newOffset = offset - 1;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    boolean camel = myEditor.getSettings().isCamelWords();
    for (; newOffset > minOffset; newOffset--) {
      if (EditorActionUtil.isWordStart(text, newOffset, camel)) break;
    }

    return newOffset;
  }

  int getWordAtCaretEnd() {
    Document document = myEditor.getDocument();
    int offset = myEditor.getCaretModel().getOffset();

    CharSequence text = document.getCharsSequence();
    if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) return offset;

    int newOffset = offset + 1;

    int lineNumber = myEditor.getCaretModel().getLogicalPosition().line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) return offset;
      maxOffset = document.getLineEndOffset(lineNumber + 1);
    }
    boolean camel = myEditor.getSettings().isCamelWords();
    for (; newOffset < maxOffset; newOffset++) {
      if (EditorActionUtil.isWordEnd(text, newOffset, camel)) break;
    }

    return newOffset;
  }

  private static class ClipboardObserver implements ClipboardOwner {
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
  }

  private static ClipboardOwner defaultClipboardOwner = new ClipboardObserver();

  public void copySelectionToClipboard() {
    validateContext();
    String s = myEditor.getSelectionModel().getSelectedText();
    if (s == null) return;

    s = StringUtil.convertLineSeparators(s, "\n");
    StringSelection contents = new StringSelection(s);

    Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myEditor.getContentComponent()));
    if (project == null) {
      Clipboard clipboard = myEditor.getComponent().getToolkit().getSystemClipboard();
      clipboard.setContents(contents, defaultClipboardOwner);
    }
    else {
      CopyPasteManager.getInstance().setContents(contents);
    }
  }

  public TextAttributes getTextAttributes() {
    if (myTextAttributes == null) {
      myTextAttributes = new TextAttributes();
      EditorColorsScheme scheme = myEditor.getColorsScheme();
      myTextAttributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
      myTextAttributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
    }

    return myTextAttributes;
  }

  public void reinitSettings() {
    myTextAttributes = null;
  }
}
