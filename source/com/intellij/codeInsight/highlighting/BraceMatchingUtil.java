
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.tree.jsp.IJspElementType;
import com.intellij.psi.tree.xml.IXmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.xml.util.HtmlUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class BraceMatchingUtil {
  private static final int JAVA_TOKEN_GROUP = 0;
  private static final int XML_TAG_TOKEN_GROUP = 1;
  private static final int XML_VALUE_DELIMITER_GROUP = 2;
  private static final int JSP_TOKEN_GROUP = 3;
  private static final int JSP_VALUE_DELIMITER_GROUP = 4;
  private static final int DOC_TOKEN_GROUP = 5;

  public interface BraceMatcher {
    int getTokenGroup(IElementType tokenType);

    boolean isLBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
    boolean isRBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
    boolean isPairBraces(IElementType tokenType,IElementType tokenType2);
    boolean isStructuralBrace(HighlighterIterator iterator,CharSequence text, FileType fileType);
    IElementType getTokenType(char ch, HighlighterIterator iterator);
  }

  private static class DefaultBraceMatcher implements BraceMatcher {
    private static final BidirectionalMap<IElementType, IElementType> PAIRING_TOKENS = new BidirectionalMap<IElementType, IElementType>();
    static {
      PAIRING_TOKENS.put(JavaTokenType.LPARENTH, JavaTokenType.RPARENTH);
      PAIRING_TOKENS.put(JavaTokenType.LBRACE, JavaTokenType.RBRACE);
      PAIRING_TOKENS.put(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);
      PAIRING_TOKENS.put(XmlTokenType.XML_TAG_END, XmlTokenType.XML_START_TAG_START);
      PAIRING_TOKENS.put(XmlTokenType.XML_EMPTY_ELEMENT_END, XmlTokenType.XML_START_TAG_START);
      PAIRING_TOKENS.put(XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER);
      PAIRING_TOKENS.put(JspTokenType.JSP_SCRIPTLET_START, JspTokenType.JSP_SCRIPTLET_END);
      PAIRING_TOKENS.put(JspTokenType.JSP_EXPRESSION_START, JspTokenType.JSP_EXPRESSION_END);
      PAIRING_TOKENS.put(JspTokenType.JSP_DECLARATION_START, JspTokenType.JSP_DECLARATION_END);
      PAIRING_TOKENS.put(JspTokenType.JSP_DIRECTIVE_START, JspTokenType.JSP_DIRECTIVE_END);
      PAIRING_TOKENS.put(JspTokenType.JSP_ACTION_END, JspTokenType.JSP_ACTION_START);
      PAIRING_TOKENS.put(JspTokenType.JSP_EMPTY_ACTION_END, JspTokenType.JSP_ACTION_START);
      PAIRING_TOKENS.put(JspTokenType.JSP_ACTION_ATTRIBUTE_VALUE_START_DELIMITER, JspTokenType.JSP_ACTION_ATTRIBUTE_VALUE_END_DELIMITER);
      PAIRING_TOKENS.put(JspTokenType.JSP_DIRECTIVE_ATTRIBUTE_VALUE_START_DELIMITER, JspTokenType.JSP_DIRECTIVE_ATTRIBUTE_VALUE_END_DELIMITER);
      PAIRING_TOKENS.put(JavaDocTokenType.DOC_INLINE_TAG_START, JavaDocTokenType.DOC_INLINE_TAG_END);
    }

    public int getTokenGroup(IElementType tokenType) {
      if (tokenType instanceof IJavaElementType) {
        return JAVA_TOKEN_GROUP;
      }
      else if (tokenType instanceof IXmlElementType) {
        return tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER || tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
               ? XML_VALUE_DELIMITER_GROUP
               : XML_TAG_TOKEN_GROUP;
      }
      else if (tokenType instanceof IJspElementType) {
        return tokenType == JspTokenType.JSP_ACTION_ATTRIBUTE_VALUE_START_DELIMITER ||
               tokenType == JspTokenType.JSP_DIRECTIVE_ATTRIBUTE_VALUE_START_DELIMITER ||
               tokenType == JspTokenType.JSP_ACTION_ATTRIBUTE_VALUE_END_DELIMITER ||
               tokenType == JspTokenType.JSP_DIRECTIVE_ATTRIBUTE_VALUE_END_DELIMITER ? JSP_VALUE_DELIMITER_GROUP : JSP_TOKEN_GROUP;
      }
      else if (tokenType instanceof IJavaDocElementType) {
        return DOC_TOKEN_GROUP;
      }
      else{
        return -1;
      }
    }

    public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      IElementType tokenType = iterator.getTokenType();
      return tokenType == JavaTokenType.LPARENTH ||
             tokenType == JavaTokenType.LBRACE ||
             tokenType == JavaTokenType.LBRACKET ||
             tokenType == XmlTokenType.XML_START_TAG_START ||
             tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER ||
             tokenType == JspTokenType.JSP_SCRIPTLET_START ||
             tokenType == JspTokenType.JSP_EXPRESSION_START ||
             tokenType == JspTokenType.JSP_DECLARATION_START ||
             tokenType == JspTokenType.JSP_DIRECTIVE_START ||
             tokenType == JspTokenType.JSP_ACTION_START ||
             tokenType == JspTokenType.JSP_ACTION_ATTRIBUTE_VALUE_START_DELIMITER ||
             tokenType == JspTokenType.JSP_DIRECTIVE_ATTRIBUTE_VALUE_START_DELIMITER ||
             tokenType == JavaDocTokenType.DOC_INLINE_TAG_START;
    }

    public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      final IElementType tokenType = iterator.getTokenType();

      if (tokenType == JavaTokenType.RPARENTH ||
          tokenType == JavaTokenType.RBRACE ||
          tokenType == JavaTokenType.RBRACKET ||
          tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END ||
          tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER ||
          tokenType == JspTokenType.JSP_SCRIPTLET_END ||
          tokenType == JspTokenType.JSP_EXPRESSION_END ||
          tokenType == JspTokenType.JSP_DECLARATION_END ||
          tokenType == JspTokenType.JSP_DIRECTIVE_END ||
          tokenType == JspTokenType.JSP_EMPTY_ACTION_END ||
          tokenType == JspTokenType.JSP_ACTION_ATTRIBUTE_VALUE_END_DELIMITER ||
          tokenType == JspTokenType.JSP_DIRECTIVE_ATTRIBUTE_VALUE_END_DELIMITER ||
          tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) {
        return true;
      }
      else if (tokenType == XmlTokenType.XML_TAG_END) {
        boolean result = findTokenBack(iterator, XmlTokenType.XML_END_TAG_START, XmlTokenType.XML_START_TAG_START);
        if (!result && fileType == StdFileTypes.HTML) {
          result = isEndOfSingleHtmlTag(fileText, iterator);
        }

        return result;
      }
      else if (tokenType == JspTokenType.JSP_ACTION_END) {
        return findTokenBack(iterator, JspTokenType.JSP_ACTION_END_TAG_START, JspTokenType.JSP_ACTION_START);
      }
      else {
        return false;
      }
    }

    public boolean isPairBraces(IElementType tokenType1, IElementType tokenType2) {
      if (tokenType2.equals(PAIRING_TOKENS.get(tokenType1))) return true;
      List<IElementType> keys = PAIRING_TOKENS.getKeysByValue(tokenType1);
      if (keys != null && keys.contains(tokenType2)) return true;
      return false;
    }

    public boolean isStructuralBrace(HighlighterIterator iterator,CharSequence text, FileType fileType) {
      IElementType tokenType = iterator.getTokenType();
      if (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.ASPECT) {
        return tokenType == JavaTokenType.RBRACE || tokenType == JavaTokenType.LBRACE;
      }
      else if (fileType == StdFileTypes.HTML || fileType == StdFileTypes.XML) {
        return tokenType == XmlTokenType.XML_START_TAG_START ||
               tokenType == XmlTokenType.XML_TAG_END ||
               tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END ||
               ( tokenType == XmlTokenType.XML_TAG_END &&
                 fileType == StdFileTypes.HTML &&
                 isEndOfSingleHtmlTag(text, iterator)
               );
      }
      else if (fileType == StdFileTypes.JSP || fileType == StdFileTypes.JSPX) {
        return tokenType == JavaTokenType.LBRACE ||
               tokenType == JavaTokenType.RBRACE ||
               tokenType == JspTokenType.JSP_SCRIPTLET_START ||
               tokenType == JspTokenType.JSP_EXPRESSION_START ||
               tokenType == JspTokenType.JSP_DECLARATION_START ||
               tokenType == JspTokenType.JSP_DIRECTIVE_START ||
               tokenType == JspTokenType.JSP_ACTION_START ||
               tokenType == JspTokenType.JSP_SCRIPTLET_END ||
               tokenType == JspTokenType.JSP_EXPRESSION_END ||
               tokenType == JspTokenType.JSP_DECLARATION_END ||
               tokenType == JspTokenType.JSP_DIRECTIVE_END ||
               tokenType == JspTokenType.JSP_EMPTY_ACTION_END ||
               tokenType == JspTokenType.JSP_ACTION_END ||
               tokenType == XmlTokenType.XML_START_TAG_START ||
               tokenType == XmlTokenType.XML_TAG_END ||
               tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END;
      }
      else{
        return false;
      }
    }

    public IElementType getTokenType(char ch, HighlighterIterator iterator) {
      if (ch == '}') return JavaTokenType.RBRACE;
      if (ch == '{') return JavaTokenType.LBRACE;
      if (ch == ']') return JavaTokenType.RBRACKET;
      if (ch == '[') return JavaTokenType.LBRACKET;
      if (ch == ')') return JavaTokenType.RPARENTH;
      if (ch == '(') return JavaTokenType.LPARENTH;

      return null;  //TODO: add more here!
    }
  }

  public static class HtmlBraceMatcher extends DefaultBraceMatcher {
    private static BraceMatcher ourStyleBraceMatcher;
    private static BraceMatcher ourScriptBraceMatcher;

    public static final void setStyleBraceMatcher(BraceMatcher braceMatcher) {
      ourStyleBraceMatcher = braceMatcher;
    }

    public static void setScriptBraceMatcher(BraceMatcher _scriptBraceMatcher) {
      ourScriptBraceMatcher = _scriptBraceMatcher;
    }

    public int getTokenGroup(IElementType tokenType) {
      int tokenGroup = super.getTokenGroup(tokenType);

      if(tokenGroup==-1 && ourStyleBraceMatcher!=null) {
        tokenGroup = ourStyleBraceMatcher.getTokenGroup(tokenType);
      }

      if(tokenGroup==-1 && ourScriptBraceMatcher!=null) {
        tokenGroup = ourScriptBraceMatcher.getTokenGroup(tokenType);
      }

      return tokenGroup;
    }

    public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      boolean islbrace = super.isLBraceToken(iterator, fileText, fileType);

      if (!islbrace && ourStyleBraceMatcher!=null) {
        islbrace = ourStyleBraceMatcher.isLBraceToken(iterator, fileText, fileType);
      }

      if (!islbrace && ourScriptBraceMatcher!=null) {
        islbrace = ourScriptBraceMatcher.isLBraceToken(iterator, fileText, fileType);
      }

      return islbrace;
    }

    public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      boolean rBraceToken = super.isRBraceToken(iterator, fileText, fileType);

      if (!rBraceToken && ourStyleBraceMatcher!=null) {
        rBraceToken = ourStyleBraceMatcher.isRBraceToken(iterator, fileText, fileType);
      }

      if (!rBraceToken && ourScriptBraceMatcher!=null) {
        rBraceToken = ourScriptBraceMatcher.isRBraceToken(iterator, fileText, fileType);
      }

      return rBraceToken;
    }

    public boolean isPairBraces(IElementType tokenType1, IElementType tokenType2) {
      boolean pairBraces = super.isPairBraces(tokenType1, tokenType2);

      if (!pairBraces && ourStyleBraceMatcher!=null) {
        pairBraces = ourStyleBraceMatcher.isPairBraces(tokenType1, tokenType2);
      }
      if (!pairBraces && ourScriptBraceMatcher!=null) {
        pairBraces = ourScriptBraceMatcher.isPairBraces(tokenType1, tokenType2);
      }

      return pairBraces;
    }

    public boolean isStructuralBrace(HighlighterIterator iterator, CharSequence text, FileType fileType) {
      boolean structuralBrace = super.isStructuralBrace(iterator, text, fileType);

      if (!structuralBrace && ourStyleBraceMatcher!=null) {
        structuralBrace = ourStyleBraceMatcher.isStructuralBrace(iterator, text, fileType);
      }
      if (!structuralBrace && ourScriptBraceMatcher!=null) {
        structuralBrace = ourScriptBraceMatcher.isStructuralBrace(iterator, text, fileType);
      }
      return structuralBrace;
    }

    public IElementType getTokenType(char ch, HighlighterIterator iterator) {
      IElementType pairedParenType = null;

      if (ourScriptBraceMatcher!=null) {
        pairedParenType = ourScriptBraceMatcher.getTokenType(ch, iterator);
      }

      if (pairedParenType == null && ourStyleBraceMatcher!=null) {
        pairedParenType = ourStyleBraceMatcher.getTokenType(ch, iterator);
      }

      if (pairedParenType == null) {
        pairedParenType = super.getTokenType(ch,iterator);
      }

      return pairedParenType;
    }
  }

  private static final HashMap<FileType,BraceMatcher> BRACE_MATCHERS = new HashMap<FileType, BraceMatcher>();

  static {
    final BraceMatcher defaultBraceMatcher = new DefaultBraceMatcher();
    registerBraceMatcher(StdFileTypes.JAVA,defaultBraceMatcher);
    registerBraceMatcher(StdFileTypes.XML,defaultBraceMatcher);
    registerBraceMatcher(StdFileTypes.JSPX,defaultBraceMatcher);

    HtmlBraceMatcher braceMatcher = new HtmlBraceMatcher();
    registerBraceMatcher(StdFileTypes.HTML,braceMatcher);
    registerBraceMatcher(StdFileTypes.XHTML,braceMatcher);
    registerBraceMatcher(StdFileTypes.JSP, braceMatcher);
  }

  public static void registerBraceMatcher(FileType fileType,BraceMatcher braceMatcher) {
    BRACE_MATCHERS.put(fileType, braceMatcher);
  }

  private static final Stack<IElementType> ourBraceStack = new Stack<IElementType>();
  private static final Stack<String> ourTagNameStack = new Stack<String>();

  public static synchronized boolean matchBrace(CharSequence fileText, FileType fileType, HighlighterIterator iterator, boolean forward) {
    IElementType brace1Token = iterator.getTokenType();
    int group = getTokenGroup(brace1Token, fileType);
    String brace1TagName = getTagName(fileText, iterator);
    boolean isStrict = isStrictTagMatching(fileType, group);
    boolean isCaseSensitive = areTagsCaseSensitive(fileType, group);

    ourBraceStack.clear();
    ourTagNameStack.clear();
    ourBraceStack.push(brace1Token);
    if (isStrict){
      ourTagNameStack.push(brace1TagName);
    }
    boolean matched = false;
    while(true){
      if (!forward){
        iterator.retreat();
      }
      else{
        iterator.advance();
      }
      if (iterator.atEnd()) {
        break;
      }

      IElementType tokenType = iterator.getTokenType();

      if (getTokenGroup(tokenType, fileType) == group) {
        String tagName = getTagName(fileText, iterator);
        if (!isStrict && !Comparing.equal(brace1TagName, tagName, isCaseSensitive)) continue;
        if (forward ? isLBraceToken(iterator, fileText, fileType) : isRBraceToken(iterator, fileText, fileType)){
          ourBraceStack.push(tokenType);
          if (isStrict){
            ourTagNameStack.push(tagName);
          }
        }
        else if (forward ? isRBraceToken(iterator, fileText,fileType) : isLBraceToken(iterator, fileText, fileType)){
          IElementType topTokenType = ourBraceStack.pop();
          String topTagName = null;
          if (isStrict){
            topTagName = ourTagNameStack.pop();
          }

          if (!isPairBraces(topTokenType, tokenType, fileType)
            || isStrict && !Comparing.equal(topTagName, tagName, isCaseSensitive)
          ){
            matched = false;
            break;
          }

          if (ourBraceStack.size() == 0){
            matched = true;
            break;
          }
        }
      }
    }
    return matched;
  }

  public static boolean findStructuralLeftBrace(FileType fileType, HighlighterIterator iterator, CharSequence fileText) {
    ourBraceStack.clear();
    ourTagNameStack.clear();

    while (!iterator.atEnd()) {
      if (isStructuralBraceToken(fileType, iterator,fileText)) {
        if (isRBraceToken(iterator, fileText, fileType)) {
          ourBraceStack.push(iterator.getTokenType());
          ourTagNameStack.push(getTagName(fileText, iterator));
        }
        if (isLBraceToken(iterator, fileText, fileType)) {
          if (ourBraceStack.size() == 0) return true;

          final int group = getTokenGroup(iterator.getTokenType(), fileType);

          final IElementType topTokenType = ourBraceStack.pop();
          final IElementType tokenType = iterator.getTokenType();

          boolean isStrict = isStrictTagMatching(fileType, group);
          boolean isCaseSensitive = areTagsCaseSensitive(fileType, group);

          String topTagName = null;
          String tagName = null;
          if (isStrict){
            topTagName = ourTagNameStack.pop();
            tagName = getTagName(fileText, iterator);
          }

          if (!isPairBraces(topTokenType, tokenType, fileType)
            || isStrict && !Comparing.equal(topTagName, tagName, isCaseSensitive)) {
            return false;
          }
        }
      }

      iterator.retreat();
    }

    return false;
  }

  public static boolean isStructuralBraceToken(FileType fileType, HighlighterIterator iterator,CharSequence text) {
    BraceMatcher matcher = BRACE_MATCHERS.get(fileType);
    if (matcher!=null) return matcher.isStructuralBrace(iterator, text, fileType);
    return false;
  }

  private static boolean isEndOfSingleHtmlTag(CharSequence text,HighlighterIterator iterator) {
    String tagName = getTagName(text,iterator);
    if (tagName!=null) return HtmlUtil.isSingleHtmlTag(tagName);
    return false;
  }

  public static boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType){
    final BraceMatcher braceMatcher = BRACE_MATCHERS.get(fileType);

    if (braceMatcher!=null) return braceMatcher.isLBraceToken(iterator, fileText, fileType);
    return false;
  }

  public static boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType){
    final BraceMatcher braceMatcher = BRACE_MATCHERS.get(fileType);

    if (braceMatcher!=null) return braceMatcher.isRBraceToken(iterator, fileText, fileType);
    return false;
  }

  public static boolean isPairBraces(IElementType tokenType1, IElementType tokenType2, FileType fileType){
    BraceMatcher matcher = BRACE_MATCHERS.get(fileType);
    if (matcher!=null) return matcher.isPairBraces(tokenType1, tokenType2);
    return false;
  }

  private static int getTokenGroup(IElementType tokenType, FileType fileType){
    BraceMatcher matcher = BRACE_MATCHERS.get(fileType);
    if (matcher!=null) return matcher.getTokenGroup(tokenType);
    return -1;
  }

  private static boolean isStrictTagMatching(FileType fileType, int tokenGroup) {
    switch(tokenGroup){
      case XML_TAG_TOKEN_GROUP:
        return fileType == StdFileTypes.XML;

      case JSP_TOKEN_GROUP:
        return true;

      default:
        return false;
    }
  }

  private static boolean areTagsCaseSensitive(FileType fileType, int tokenGroup) {
    switch(tokenGroup){
      case XML_TAG_TOKEN_GROUP:
        return fileType == StdFileTypes.XML;

      case JSP_TOKEN_GROUP:
        return true;

      default:
        return false;
    }
  }

  private static String getTagName(CharSequence fileText, HighlighterIterator iterator) {
    IElementType tokenType = iterator.getTokenType();
    String name = null;
    if (tokenType == XmlTokenType.XML_START_TAG_START) {
      {
        iterator.advance();
        IElementType tokenType1;
        if (!iterator.atEnd() &&
            ( (tokenType1 = iterator.getTokenType()) == XmlTokenType.XML_TAG_NAME ||
              tokenType1 == XmlTokenType.XML_NAME
            )
           ) {
          name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
        }
        iterator.retreat();
      }
    }
    else if (tokenType == XmlTokenType.XML_TAG_END || tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
      {
        int count = 0;
        while (true) {
          iterator.retreat();
          count++;
          if (iterator.atEnd()) break;
          IElementType tokenType1 = iterator.getTokenType();
          if (tokenType1 == XmlTokenType.XML_NAME) {
            iterator.retreat();
            tokenType1 = iterator.getTokenType();
            iterator.advance();
            if (tokenType1 == XmlTokenType.XML_START_TAG_START || tokenType1== XmlTokenType.XML_END_TAG_START) {
              name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
              break;
            }
          } else if (tokenType1 == XmlTokenType.XML_TAG_NAME) {
            name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
            break;
          }
        }
        for (int i = 0; i < count; i++) {
          iterator.advance();
        }
      }
    }
    else if (tokenType == JspTokenType.JSP_ACTION_START) {
      {
        iterator.advance();
        if (!iterator.atEnd() && iterator.getTokenType() == JspTokenType.JSP_ACTION_NAME) {
          name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
        }
        iterator.retreat();
      }
    }
    else if (tokenType == JspTokenType.JSP_ACTION_END || tokenType == JspTokenType.JSP_EMPTY_ACTION_END) {
      {
        int count = 0;
        while (true) {
          iterator.retreat();
          count++;
          if (iterator.atEnd()) break;
          IElementType tokenType1 = iterator.getTokenType();
          if (tokenType1 == JspTokenType.JSP_ACTION_NAME) {
            name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
            break;
          }
        }
        for (int i = 0; i < count; i++) {
          iterator.advance();
        }
      }
    }

    return name;
  }

  private static boolean findTokenBack(HighlighterIterator iterator, IElementType tokenType, IElementType stopTokenType) {
    int count = 0;
    boolean found = false;
    while(true){
      iterator.retreat();
      count++;
      if (iterator.atEnd()) break;
      IElementType tokenType1 = iterator.getTokenType();
      if (tokenType1 == tokenType){
        found = true;
        break;
      }
      if (tokenType1 == stopTokenType){
        break;
      }
    }
    for(int i = 0; i < count; i++){
      iterator.advance();
    }
    return found;
  }

  // TODO: better name for this method
  public static int findLeftmostLParen(HighlighterIterator iterator, IElementType lparenTokenType, CharSequence fileText, FileType fileType){
    int lastLbraceOffset = -1;

    Stack<IElementType> braceStack = new Stack<IElementType>();
    for( ; !iterator.atEnd(); iterator.retreat()){
      final IElementType tokenType = iterator.getTokenType();

      if (isLBraceToken(iterator, fileText, fileType)){
        if (braceStack.size() > 0){
          IElementType topToken = braceStack.pop();
          if (isPairBraces(tokenType, topToken, fileType)){
            continue;
          }
          else{
            break; // unmatched braces
          }
        }
        else{
          if (tokenType == lparenTokenType){
            lastLbraceOffset = iterator.getStart();
          }
          else{
            break;
          }
        }
      }
      else if (isRBraceToken(iterator, fileText, fileType )){
        braceStack.push(iterator.getTokenType());
      }
    }

    return lastLbraceOffset;
  }

  // TODO: better name for this method
  public static int findRightmostRParen(HighlighterIterator iterator, IElementType rparenTokenType, CharSequence fileText, FileType fileType) {
    int lastRbraceOffset = -1;

    Stack<IElementType> braceStack = new Stack<IElementType>();
    for(; !iterator.atEnd(); iterator.advance()){
      final IElementType tokenType = iterator.getTokenType();

      if (isRBraceToken(iterator, fileText, fileType)){
        if (braceStack.size() > 0){
          IElementType topToken = braceStack.pop();
          if (isPairBraces(tokenType, topToken, fileType)){
            continue;
          }
          else{
            break; // unmatched braces
          }
        }
        else{
          if (tokenType == rparenTokenType){
            lastRbraceOffset = iterator.getStart();
          }
          else{
            break;
          }
        }
      }
      else if (isLBraceToken(iterator, fileText, fileType)){
        braceStack.push(iterator.getTokenType());
      }
    }

    return lastRbraceOffset;
  }

  public static BraceMatcher getBraceMatcher(FileType fileType) {
    BraceMatcher braceMatcher = BRACE_MATCHERS.get(fileType);
    if (braceMatcher==null) braceMatcher = BRACE_MATCHERS.get(StdFileTypes.JAVA);
    return braceMatcher;
  }
}
