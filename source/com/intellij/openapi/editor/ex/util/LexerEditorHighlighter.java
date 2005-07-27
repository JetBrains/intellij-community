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
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.parsing.jsp.JspHighlightLexer;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.codeHighlighting.CopyCreatorLexer;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class LexerEditorHighlighter extends DocumentAdapter implements EditorHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.LexerEditorHighlighter");
  private Editor myEditor;
  private Lexer myLexer;
  private Map<IElementType, TextAttributes> myAttributesMap;
  private SegmentArrayWithData mySegments = new SegmentArrayWithData();
  private SyntaxHighlighter myHighlighter;
  private EditorColorsScheme myScheme;
  private int myInitialState;

  public LexerEditorHighlighter(SyntaxHighlighter highlighter, EditorColorsScheme scheme) {
    myScheme = scheme;
    myLexer = highlighter.getHighlightingLexer();
    myLexer.start("".toCharArray());
    myInitialState = myLexer.getState();
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

  private int packData(IElementType tokenType, int state) {
    final short idx = tokenType.getIndex();
    return state == myInitialState ? idx : -idx;
  }

  private static boolean isInitialState(int data) {
    return data >= 0;
  }

  private static IElementType unpackToken(int data) {
    return IElementType.find((short)Math.abs(data));
  }

  public synchronized void documentChanged(DocumentEvent e) {
    Document document = e.getDocument();
    if(myLexer instanceof JspHighlightLexer && myEditor != null && myEditor.getProject() != null){
      final PsiDocumentManager instance = PsiDocumentManager.getInstance(myEditor.getProject());
      final PsiFile psiFile = instance.getPsiFile(document);
      if(psiFile instanceof JspFile) ((JspHighlightLexer)myLexer).setBaseFile((JspFile)psiFile);
    }
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
    SegmentArrayWithData insertSegments = new SegmentArrayWithData();
    int oldEndIndex = -1;
    int insertSegmentCount = 0;
    int repaintEnd = -1;

    int lastTokenStart = -1;

    while (myLexer.getTokenType() != null) {
      if (startIndex >= oldStartIndex) break;

      int tokenStart = myLexer.getTokenStart();
      if (tokenStart == lastTokenStart) {
        throw new IllegalStateException("Error while updating lexer: " + e + " document text: " + e.getDocument().getText());
      }

      int tokenEnd = myLexer.getTokenEnd();
      int lexerState = myLexer.getState();
      data = packData(myLexer.getTokenType(), lexerState);
      if (mySegments.getSegmentStart(startIndex) != tokenStart ||
          mySegments.getSegmentEnd(startIndex) != tokenEnd ||
          mySegments.getSegmentData(startIndex) != data) {
        break;
      }
      startIndex++;
      myLexer.advance();
      lastTokenStart = tokenStart;
    }

    startOffset = mySegments.getSegmentStart(startIndex);

    while(myLexer.getTokenType() != null) {
      int tokenStart = myLexer.getTokenStart();
      if (tokenStart == lastTokenStart) {
        throw new IllegalStateException("Error while updating lexer: " + e + " document text: " + e.getDocument().getText());
      }

      lastTokenStart = tokenStart;

      int tokenEnd = myLexer.getTokenEnd();
      int lexerState = myLexer.getState();
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
    char[] chars = CharArrayUtil.fromSequence(text);
    if(myLexer instanceof JspHighlightLexer && myEditor != null && myEditor.getProject() != null){
      final PsiDocumentManager instance = PsiDocumentManager.getInstance(myEditor.getProject());
      final PsiFile psiFile = instance.getPsiFile(myEditor.getDocument());
      if(psiFile instanceof JspFile) ((JspHighlightLexer)myLexer).setBaseFile((JspFile)psiFile);
    }
    myLexer.start(chars, startOffset, text.length());
    int i = 0;
    mySegments.removeAll();
    while(myLexer.getTokenType() != null) {
      int data = packData(myLexer.getTokenType(), myLexer.getState());
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
      IElementType tokenType = getRawToken();
      return getAttributes(tokenType);
    }

    public int getStart() {
      return mySegments.getSegmentStart(mySegmentIndex);
    }

    public int getEnd() {
      return mySegments.getSegmentEnd(mySegmentIndex);
    }

    public IElementType getTokenType(){
      IElementType token = getRawToken();
      if (token instanceof CopyCreatorLexer.HighlightingCopyElementType) {
        token = ((CopyCreatorLexer.HighlightingCopyElementType)token).getBase();
      }
      return token;
    }

    private IElementType getRawToken() {
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
