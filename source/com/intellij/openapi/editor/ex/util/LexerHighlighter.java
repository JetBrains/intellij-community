package com.intellij.openapi.editor.ex.util;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.Highlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileHighlighter;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class LexerHighlighter extends DocumentAdapter implements Highlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.LexerHighlighter");
  private Editor myEditor;
  private Lexer myLexer;
  private Map<IElementType, TextAttributes> myAttributesMap;
  private SegmentArrayWithData mySegments = new SegmentArrayWithData();
  private FileHighlighter myHighlighter;
  private EditorColorsScheme myScheme;

  public LexerHighlighter(FileHighlighter highlighter, EditorColorsScheme scheme) {
    myScheme = scheme;
    myLexer = highlighter.getHighlightingLexer();
    myAttributesMap = new HashMap<IElementType, TextAttributes>();
    myHighlighter = highlighter;
  }

  public Lexer getLexer() {
    return myLexer;
  }

  public void setEditor(Editor editor) {
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

  private long packData(IElementType tokenType, int state) {
    return tokenType.getIndex() | (((long)state) << 16);
  }

  private int unpackState(long data) {
    return (int)(data >> 16);
  }

  private IElementType unpackToken(long data) {
    return IElementType.find((short)(data & 0xFFFF));
  }

  public synchronized void documentChanged(DocumentEvent e) {
    Document document = e.getDocument();
    if(mySegments.getSegmentCount() == 0 || myLexer.getSmartUpdateShift() < 0) {
      setText(document.getCharsSequence());
      return;
    }
    CharSequence text = document.getCharsSequence();
    int oldStartOffset = e.getOffset();

    int startIndex = Math.max(0, mySegments.findSegmentIndex(oldStartOffset) - 2);
    int startOffset = mySegments.getSegmentStart(startIndex);

    long data = mySegments.getSegmentData(startIndex);
    final int lexerState = unpackState(data);

    int newEndOffset = e.getOffset() + e.getNewLength();

    myLexer.start(CharArrayUtil.fromSequence(text), startOffset, text.length(), lexerState);
    SegmentArrayWithData insertSegments = new SegmentArrayWithData();
    int oldEndIndex = -1;
    int insertSegmentCount = 0;
    int repaintEnd = -1;

    int lastTokenStart = -1;
    while(myLexer.getTokenType() != null) {
      int tokenStart = myLexer.getTokenStart();
      if (tokenStart == lastTokenStart) {
        throw new IllegalStateException("Error while updating lexer: " + e + " document text: " + e.getDocument().getText());
      }

      lastTokenStart = tokenStart;

      int tokenEnd = myLexer.getTokenEnd();
      data = packData(myLexer.getTokenType(), myLexer.getState());
      if(tokenStart >= newEndOffset) {
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

    if(repaintEnd == -1) {
      repaintEnd = text.length();
    }

    if (oldEndIndex < 0){
      oldEndIndex = mySegments.getSegmentCount();
    }
    mySegments.shiftSegments(oldEndIndex, e.getNewLength() - e.getOldLength());
    mySegments.remove(startIndex, oldEndIndex);
    mySegments.insert(insertSegments, startIndex);

    int lastDocOffset = e.getDocument().getTextLength();
    checkUpdateCorrect(lastDocOffset);

    if (insertSegmentCount == 0 ||
        oldEndIndex == startIndex + 1 && insertSegmentCount == 1 && data == mySegments.getSegmentData(startIndex)) {
      return;
    }

    ((EditorEx) myEditor).repaint(startOffset, repaintEnd);
  }

  private void checkUpdateCorrect(int lastDocOffset) {
    /*
    int lastLexerOffset = mySegments.getSegmentEnd(mySegments.getSegmentCount() - 1);
    if (lastDocOffset != lastLexerOffset) {
      LOG.error("Lexer update failed: lastDocOffset = " + lastDocOffset + ", lastLexerOffset = " + lastLexerOffset);
    }
    */
  }

  public void setText(CharSequence text) {
    int startOffset = 0;
    myLexer.start(text.toString().toCharArray(), startOffset, text.length());
    int i = 0;
    mySegments.removeAll();
    while(myLexer.getTokenType() != null) {
      long data = packData(myLexer.getTokenType(), myLexer.getState());
      mySegments.setElementAt(i, myLexer.getTokenStart(), myLexer.getTokenEnd(), data);
      i++;
      myLexer.advance();
    }

    checkUpdateCorrect(text.length());

    if(myEditor != null) {
      ((EditorEx) myEditor).repaint(0, text.length());
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

  private TextAttributes convertAttributes(TextAttributesKey[] keys) {
    EditorColorsScheme scheme = myScheme;
    TextAttributes attrs = scheme.getAttributes(HighlighterColors.TEXT);
    for (int i = 0; i < keys.length; i++) {
      TextAttributesKey key = keys[i];
      TextAttributes attrs2 = scheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }
    return attrs;
  }

  private class HighlighterIteratorImpl implements HighlighterIterator {
    private int mySegmentIndex = 0;
    private SegmentArrayWithData mySegments;

    HighlighterIteratorImpl(SegmentArrayWithData segments,
                            int startOffset) {
      mySegments = segments;
      mySegmentIndex = mySegments.findSegmentIndex(startOffset);
    }

    public TextAttributes getTextAttributes() {
      IElementType tokenType = getTokenType();
      return getAttributes(tokenType);
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
