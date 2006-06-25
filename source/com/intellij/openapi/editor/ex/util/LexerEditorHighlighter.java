package com.intellij.openapi.editor.ex.util;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class LexerEditorHighlighter implements EditorHighlighter, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.LexerEditorHighlighter");
  private HighlighterClient myEditor;
  private Lexer myLexer;
  private Map<IElementType, TextAttributes> myAttributesMap;
  private SegmentArrayWithData mySegments;
  private SyntaxHighlighter myHighlighter;
  private EditorColorsScheme myScheme;
  private int myInitialState;
  public static final Key<Integer> CHANGED_TOKEN_START_OFFSET = Key.create("CHANGED_TOKEN_START_OFFSET");

  public LexerEditorHighlighter(SyntaxHighlighter highlighter, EditorColorsScheme scheme) {
    myScheme = scheme;
    myLexer = highlighter.getHighlightingLexer();
    myLexer.start("".toCharArray());
    myInitialState = myLexer.getState();
    myAttributesMap = new HashMap<IElementType, TextAttributes>();
    myHighlighter = highlighter;
    mySegments = new SegmentArrayWithData();
  }

  protected final Document getDocument() {
    return myEditor != null ? myEditor.getDocument() : null;
  }

  public EditorColorsScheme getScheme() {
    return myScheme;
  }

  protected final void setSegmentStorage(SegmentArrayWithData storage) {
    mySegments = storage;
  }

  public Lexer getLexer() {
    return myLexer;
  }

  public void setEditor(HighlighterClient editor) {
    LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
    myEditor = editor;
  }

  public void setColorScheme(EditorColorsScheme scheme) {
    myScheme = scheme;
    myAttributesMap.clear();
  }

  public HighlighterIterator createIterator(int startOffset) {
    return new HighlighterIteratorImpl(mySegments, startOffset);
  }

  private int packData(IElementType tokenType, int state) {
    final short idx = tokenType.getIndex();
    return state == myInitialState ? idx : -idx;
  }

  private static boolean isInitialState(int data) {
    return data >= 0;
  }

  protected static IElementType unpackToken(int data) {
    return IElementType.find((short)Math.abs(data));
  }

  public synchronized void documentChanged(DocumentEvent e) {
    Document document = e.getDocument();

    if(mySegments.getSegmentCount() == 0) {
      setText(document.getCharsSequence());
      return;
    }

    CharSequence text = document.getCharsSequence();
    int oldStartOffset = e.getOffset();

    final int oldStartIndex = Math.max(0, mySegments.findSegmentIndex(oldStartOffset) - 2);
    int startIndex = oldStartIndex;

    int data;
    do {
      data = mySegments.getSegmentData(startIndex);
      if (isInitialState(data)|| startIndex == 0) break;
      startIndex--;
    }
    while (true);

    int startOffset = mySegments.getSegmentStart(startIndex);
    int newEndOffset = e.getOffset() + e.getNewLength();

    myLexer.start(CharArrayUtil.fromSequence(text), startOffset, text.length(), myInitialState);

    int lastTokenStart = -1;
    int lastLexerState = -1;

    while (myLexer.getTokenType() != null) {
      if (startIndex >= oldStartIndex) break;

      int tokenStart = myLexer.getTokenStart();
      int lexerState = myLexer.getState();

      if (tokenStart == lastTokenStart && lexerState == lastLexerState) {
        throw new IllegalStateException("Error while updating lexer: " + e + " document text: " + document.getText());
      }

      int tokenEnd = myLexer.getTokenEnd();
      data = packData(myLexer.getTokenType(), lexerState);
      if (mySegments.getSegmentStart(startIndex) != tokenStart ||
          mySegments.getSegmentEnd(startIndex) != tokenEnd ||
          mySegments.getSegmentData(startIndex) != data) {
        break;
      }
      startIndex++;
      myLexer.advance();
      lastTokenStart = tokenStart;
      lastLexerState = lexerState;
    }

    startOffset = mySegments.getSegmentStart(startIndex);
    int repaintEnd = -1;
    int insertSegmentCount = 0;
    int oldEndIndex = -1;
    SegmentArrayWithData insertSegments = new SegmentArrayWithData();

    while(myLexer.getTokenType() != null) {
      int tokenStart = myLexer.getTokenStart();
      int lexerState = myLexer.getState();

      if (tokenStart == lastTokenStart && lexerState == lastLexerState) {
        throw new IllegalStateException("Error while updating lexer: " + e + " document text: " + document.getText());
      }

      lastTokenStart = tokenStart;
      lastLexerState = lexerState;

      int tokenEnd = myLexer.getTokenEnd();
      data = packData(myLexer.getTokenType(), lexerState);
      if(tokenStart >= newEndOffset && lexerState == myInitialState) {
        int shiftedTokenStart = tokenStart - e.getNewLength() + e.getOldLength();
        int index = mySegments.findSegmentIndex(shiftedTokenStart);
        if (mySegments.getSegmentStart(index) == shiftedTokenStart && mySegments.getSegmentData(index) == data) {
          repaintEnd = tokenStart;
          oldEndIndex = index;
          break;
        }
      }
      insertSegments.setElementAt(insertSegmentCount, tokenStart, tokenEnd, data);
      insertSegmentCount++;
      myLexer.advance();
    }

    final int shift = e.getNewLength() - e.getOldLength();
    if (repaintEnd > 0) {
      while (insertSegmentCount > 0 && oldEndIndex > startIndex) {
        if (!segmentsEqual(mySegments, oldEndIndex - 1, insertSegments, insertSegmentCount - 1, shift)) {
          break;
        }
        insertSegmentCount--;
        oldEndIndex--;
        repaintEnd = insertSegments.getSegmentStart(insertSegmentCount);
        insertSegments.remove(insertSegmentCount, insertSegmentCount + 1);
      }
    }

    if(repaintEnd == -1) {
      repaintEnd = text.length();
    }

    if (oldEndIndex < 0){
      oldEndIndex = mySegments.getSegmentCount();
    }
    int changedIndex = changedOffsetIndex(startIndex, oldEndIndex, insertSegments);
    mySegments.shiftSegments(oldEndIndex, shift);
    mySegments.replace(startIndex, oldEndIndex, insertSegments);

    synchronized (document) {
      int tokenStartOffset;
      if (changedIndex == -1) {
        tokenStartOffset = -1;
      }
      else {
        tokenStartOffset = mySegments.getSegmentStart(changedIndex);
        Integer oldTokenStartOffset = document.getUserData(CHANGED_TOKEN_START_OFFSET);
        if (oldTokenStartOffset != null && oldTokenStartOffset.intValue() != tokenStartOffset) {
          tokenStartOffset = -1;
        }
      }
      document.putUserData(CHANGED_TOKEN_START_OFFSET, new Integer(tokenStartOffset));
    }

    int lastDocOffset = document.getTextLength();
    checkUpdateCorrect(lastDocOffset);

    if (insertSegmentCount == 0 ||
        oldEndIndex == startIndex + 1 && insertSegmentCount == 1 && data == mySegments.getSegmentData(startIndex)) {
      return;
    }

    myEditor.repaint(startOffset, repaintEnd);
  }

  // -1 means data has been changed
  private int changedOffsetIndex(final int startIndex, final int endIndex, final SegmentArrayWithData insertSegments) {
    if (endIndex - startIndex != insertSegments.getSegmentCount()) return -1;
    int changedIndex = -1;
    for (int i = startIndex; i < endIndex; i++) {
      short oldData = mySegments.getSegmentData(i);
      int insertIndex = i - startIndex;
      short newData = insertSegments.getSegmentData(insertIndex);
      if (oldData != newData) return -1;
      if (mySegments.getSegmentStart(i) != insertSegments.getSegmentStart(insertIndex)
          || mySegments.getSegmentEnd(i) != insertSegments.getSegmentEnd(insertIndex)) {
        changedIndex = i;
      }
    }
    return changedIndex;
  }

  public void beforeDocumentChange(DocumentEvent event) {
  }

  public int getPriority() {
    return 2;
  }

  private static boolean segmentsEqual(SegmentArrayWithData a1, int idx1, SegmentArrayWithData a2, int idx2, final int offsetShift) {
    return a1.getSegmentStart(idx1) + offsetShift == a2.getSegmentStart(idx2) &&
           a1.getSegmentEnd(idx1) + offsetShift == a2.getSegmentEnd(idx2) &&
           a1.getSegmentData(idx1) == a2.getSegmentData(idx2);
  }

  private void checkUpdateCorrect(int lastDocOffset) {
    /*
    int lastLexerOffset = mySegments.getSegmentEnd(mySegments.getSegmentCount() - 1);
    if (lastDocOffset != lastLexerOffset) {
      LOG.error("Lexer update failed: lastDocOffset = " + lastDocOffset + ", lastLexerOffset = " + lastLexerOffset);
    }
    */
  }


  public HighlighterClient getClient() {
    return myEditor;
  }

  public void setText(CharSequence text) {
    char[] chars = CharArrayUtil.fromSequence(text);

    myLexer.start(chars, 0, text.length());
    mySegments.removeAll();
    int i = 0;
    while(myLexer.getTokenType() != null) {
      int data = packData(myLexer.getTokenType(), myLexer.getState());
      mySegments.setElementAt(i, myLexer.getTokenStart(), myLexer.getTokenEnd(), data);
      i++;
      myLexer.advance();
    }

    checkUpdateCorrect(text.length());

    if(myEditor != null) {
      myEditor.repaint(0, text.length());
    }
  }

  private TextAttributes getAttributes(IElementType tokenType) {
    TextAttributes attrs = myAttributesMap.get(tokenType);
    if (attrs == null) {
      attrs = convertAttributes(myHighlighter.getTokenHighlights(tokenType));
      myAttributesMap.put(tokenType, attrs);
    }
    return attrs;
  }

  protected TextAttributes convertAttributes(TextAttributesKey[] keys) {
    EditorColorsScheme scheme = myScheme;
    TextAttributes attrs = scheme.getAttributes(HighlighterColors.TEXT);
    for (TextAttributesKey key : keys) {
      TextAttributes attrs2 = scheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }
    return attrs;
  }

  public class HighlighterIteratorImpl implements HighlighterIterator {
    private int mySegmentIndex = 0;
    private SegmentArrayWithData mySegments;

    HighlighterIteratorImpl(SegmentArrayWithData segments,
                            int startOffset) {
      mySegments = segments;
      mySegmentIndex = mySegments.findSegmentIndex(startOffset);
    }

    public int currentIndex() {
      return mySegmentIndex;
    }

    public TextAttributes getTextAttributes() {
      return getAttributes(getTokenType());
    }

    public int getStart() {
      return mySegments.getSegmentStart(mySegmentIndex);
    }

    public int getEnd() {
      return mySegments.getSegmentEnd(mySegmentIndex);
    }

    public IElementType getTokenType(){
      return unpackToken(mySegments.getSegmentData(mySegmentIndex));
    }

    public void advance() {
      mySegmentIndex++;
    }

    public void retreat(){
      mySegmentIndex--;
    }

    public boolean atEnd() {
      return mySegmentIndex >= mySegments.getSegmentCount() || mySegmentIndex < 0;
    }
  }
}
