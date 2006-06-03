package com.intellij.lang.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 21, 2005
 * Time: 3:30:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class PsiBuilderImpl implements PsiBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.impl.PsiBuilderImpl");

  private final List<Token> myLexems = new ArrayList<Token>();
  private final MyList myProduction = new MyList();

  private final Lexer myLexer;
  private final boolean myFileLevelParsing;
  private final TokenSet myWhitespaces;
  private final TokenSet myComments;

  private CharTable myCharTable;
  private int myCurrentLexem;
  private CharSequence myText;
  private boolean myDebugMode = false;

  public PsiBuilderImpl(Language lang, final Project project, CharTable charTable, CharSequence text) {
    myText = text;
    ParserDefinition parserDefinition = lang.getParserDefinition();
    assert parserDefinition != null;
    myLexer = parserDefinition.createLexer(project);
    myWhitespaces = parserDefinition.getWhitespaceTokens();
    myComments = parserDefinition.getCommentTokens();
    myCharTable = charTable;
    myFileLevelParsing = myCharTable == null;

    char[] chars = CharArrayUtil.fromSequence(text);
    myLexer.start(chars, 0, text.length());
  }

  /**
   * For tests only!
   */
  public PsiBuilderImpl(final Lexer lexer, final TokenSet whitespaces, final TokenSet comments, CharTable charTable, CharSequence text) {
    myWhitespaces = whitespaces;
    myLexer = lexer;
    myComments = comments;
    myText = text;
    myCharTable = charTable;

    myFileLevelParsing = myCharTable == null;

    char[] chars = CharArrayUtil.fromSequence(text);
    myLexer.start(chars, 0, text.length());
  }

  private class StartMarker extends ProductionMarker implements Marker {
    public IElementType myType;
    public DoneMarker myDoneMarker = null;
    public Throwable myDebugAllocationPosition = null;

    public StartMarker(int idx) {
      super(idx);
      if (myDebugMode) {
        myDebugAllocationPosition = new Throwable("Created at the following trace.");
      }
    }

    public Marker precede() {
      return PsiBuilderImpl.this.preceed(this);
    }

    public void drop() {
      PsiBuilderImpl.this.drop(this);
    }

    public void rollbackTo() {
      PsiBuilderImpl.this.rollbackTo(this);
    }

    public void done(IElementType type) {
      myType = type;
      PsiBuilderImpl.this.done(this);
    }
  }

  private Marker preceed(final StartMarker marker) {
    int idx = myProduction.lastIndexOf(marker);
    LOG.assertTrue(idx >= 0, "Cannot precede dropped or rolled-back marker");
    StartMarker pre = new StartMarker(marker.myLexemIndex);
    myProduction.add(idx, pre);
    return pre;
  }

  public class Token {
    private IElementType myTokenType;
    private int myTokenStart;
    private int myTokenEnd;
    private int myState;

    public Token() {
      myTokenType = myLexer.getTokenType();
      myTokenStart = myLexer.getTokenStart();
      myTokenEnd = myLexer.getTokenEnd();
      myState = myLexer.getState();
    }

    public IElementType getTokenType() {
      return myTokenType;
    }

    public String getTokenText() {
      return new String(myLexer.getBuffer(), myTokenStart, myTokenEnd - myTokenStart);
    }
  }

  private static class ProductionMarker {
    public int myLexemIndex;

    public ProductionMarker(final int lexemIndex) {
      myLexemIndex = lexemIndex;
    }
  }

  private static class DoneMarker extends ProductionMarker {
    public StartMarker myStart;

    public DoneMarker(final StartMarker marker, int currentLexem) {
      super(currentLexem);
      myStart = marker;
    }
  }

  private static class ErrorItem extends ProductionMarker {
    String myMessage;

    public ErrorItem(final String message, int idx) {
      super(idx);
      myMessage = message;
    }
  }

  public CharSequence getOriginalText() {
    return myText;
  }

  public IElementType getTokenType() {
    final Token lex = getCurrentToken();
    if (lex == null) return null;
    
    final IElementType tokenType = lex.getTokenType();
    LOG.assertTrue(!whitespaceOrComment(tokenType));
    return tokenType;
  }

  public void advanceLexer() {
    myCurrentLexem++;
  }

  public int getCurrentOffset() {
    final PsiBuilderImpl.Token token = getCurrentToken();
    if (token == null) return getOriginalText().length();
    return token.myTokenStart;
  }

  @Nullable
  public String getTokenText() {
    final PsiBuilderImpl.Token token = getCurrentToken();
    return token != null ? token.getTokenText() : null;
  }

  @Nullable
  public Token getCurrentToken() {
    Token lastToken;
    while (true) {
      lastToken = getTokenOrWhitespace();
      if (lastToken == null) return null;
      if (whitespaceOrComment(lastToken.getTokenType())) {
        myCurrentLexem++;
      }
      else {
        break;
      }
    }

    return lastToken;
  }

  @Nullable
  private Token getTokenOrWhitespace() {
    while(myCurrentLexem >= myLexems.size()) {
      if (myLexer.getTokenType() == null) return null;
      myLexems.add(new Token());
      myLexer.advance();
    }
    return myLexems.get(myCurrentLexem);
  }

  private boolean whitespaceOrComment(IElementType token) {
    return myWhitespaces.contains(token) || myComments.contains(token);
  }

  public Marker mark() {
    StartMarker marker = new StartMarker(myCurrentLexem);
    myProduction.add(marker);
    return marker;
  }

  public boolean eof() {
    if (myCurrentLexem + 1 < myLexems.size()) return false;
    return getCurrentToken() == null;
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  public void rollbackTo(Marker marker) {
    myCurrentLexem = ((StartMarker)marker).myLexemIndex;
    int idx = myProduction.lastIndexOf(marker);

    LOG.assertTrue(idx >= 0, "The marker must be added before rolled back to.");
    myProduction.removeRange(idx, myProduction.size());
  }

  public void drop(Marker marker) {
    final boolean removed = myProduction.remove(myProduction.lastIndexOf(marker)) == marker;
    LOG.assertTrue(removed, "The marker must be added before it is dropped.");
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "SuspiciousMethodCalls"})
  public void done(Marker marker) {
    LOG.assertTrue(((StartMarker)marker).myDoneMarker == null, "Marker already done.");
    int idx = myProduction.lastIndexOf(marker);
    LOG.assertTrue(idx >= 0, "Marker never been added.");

    for (int i = myProduction.size() - 1; i > idx; i--) {
      Object item = myProduction.get(i);
      if (item instanceof Marker) {
        StartMarker otherMarker = (StartMarker)item;
        if (otherMarker.myDoneMarker == null) {
          final Throwable debugAllocOther = otherMarker.myDebugAllocationPosition;
          final Throwable debugAllocThis = ((StartMarker)marker).myDebugAllocationPosition;
          if (debugAllocOther != null) {
            debugAllocThis.printStackTrace(System.err);
            debugAllocOther.printStackTrace(System.err);
          }
          LOG.error("Another not done marker added after this one. Must be done before this.");
        }
      }
    }

    DoneMarker doneMarker = new DoneMarker((StartMarker)marker, myCurrentLexem);
    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  public void error(String messageText) {
    if (myProduction.get(myProduction.size() - 1) instanceof ErrorItem) return;
    myProduction.add(new ErrorItem(messageText, myCurrentLexem));
  }

  public ASTNode getTreeBuilt() {
    StartMarker rootMarker = (StartMarker)myProduction.get(0);

    final ASTNode rootNode;
    if (myFileLevelParsing) {
      rootNode = new FileElement(rootMarker.myType);
      myCharTable = ((FileElement)rootNode).getCharTable();
    }
    else {
      rootNode = new CompositeElement(rootMarker.myType);
      rootNode.putUserData(CharTable.CHAR_TABLE_KEY, myCharTable);
    }

    for (int i = 1; i < myProduction.size() - 1; i++) {
      ProductionMarker item = myProduction.get(i);

      if (item instanceof StartMarker) {
        while (item.myLexemIndex < myLexems.size() && myWhitespaces.contains(myLexems.get(item.myLexemIndex).getTokenType())) item.myLexemIndex++;
      }
      else if (item instanceof DoneMarker || item instanceof ErrorItem) {
        int prevProductionLexIndex = myProduction.get(i - 1).myLexemIndex;
        while (item.myLexemIndex > prevProductionLexIndex && item.myLexemIndex < myLexems.size() &&
               myWhitespaces.contains(myLexems.get(item.myLexemIndex - 1).getTokenType())) {
          item.myLexemIndex--;
        }
      }
    }

    ASTNode curNode = rootNode;
    int curToken = 0;
    int lastErrorIndex = -1;
    for (int i = 1; i < myProduction.size(); i++) {
      ProductionMarker item = myProduction.get(i);

      LOG.assertTrue(curNode != null, "Unexpected end of the production");
      int lexIndex = item.myLexemIndex;
      if (item instanceof StartMarker) {
        StartMarker marker = (StartMarker)item;
        curToken = insertLeafs(curToken, lexIndex, curNode);
        ASTNode childNode = new CompositeElement(marker.myType);
        TreeUtil.addChildren((CompositeElement)curNode, (TreeElement)childNode);
        curNode = childNode;
      }
      else if (item instanceof DoneMarker) {
        DoneMarker doneMarker = (DoneMarker)item;
        curToken = insertLeafs(curToken, lexIndex, curNode);
        LOG.assertTrue(doneMarker.myStart.myType == curNode.getElementType());
        curNode = curNode.getTreeParent();
      }
      else if (item instanceof ErrorItem) {
        curToken = insertLeafs(curToken, lexIndex, curNode);
        if (curToken == lastErrorIndex) continue;
        lastErrorIndex = curToken;
        final PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
        errorElement.setErrorDescription(((ErrorItem)item).myMessage);
        TreeUtil.addChildren((CompositeElement)curNode, errorElement);
      }
    }

    LOG.assertTrue(curToken == myLexems.size(), "Not all of the tokens inserted to the tree");
    LOG.assertTrue(curNode == null, "Unbalanced tree");

    return rootNode;
  }

  public void setDebugMode(boolean dbgMode) {
    myDebugMode = dbgMode;
  }

  private int insertLeafs(int curToken, int lastIdx, final ASTNode curNode) {
    lastIdx = Math.min(lastIdx, myLexems.size());
    while (curToken < lastIdx) {
      Token lexem = myLexems.get(curToken++);
      final LeafElement childNode = createLeaf(lexem);
      if (childNode != null) {
        TreeUtil.addChildren((CompositeElement)curNode, childNode);
      }
    }
    return curToken;
  }

  @Nullable
  private LeafElement createLeaf(final Token lexem) {
    if (lexem.myTokenStart == lexem.myTokenEnd) return null; // Empty token. Most probably a parser directive like indent/dedent in phyton
    final IElementType type = lexem.getTokenType();
    if (myWhitespaces.contains(type)) {
      return new PsiWhiteSpaceImpl(myLexer.getBuffer(), lexem.myTokenStart, lexem.myTokenEnd, lexem.myState, myCharTable);
    }
    else if (myComments.contains(type)) {
      return new PsiCommentImpl(type, myLexer.getBuffer(), lexem.myTokenStart, lexem.myTokenEnd, lexem.myState, myCharTable);
    }
    else if (type instanceof IChameleonElementType) {
      return new ChameleonElement(type, myLexer.getBuffer(), lexem.myTokenStart, lexem.myTokenEnd, lexem.myState);
    }
    return new LeafPsiElement(type, myLexer.getBuffer(), lexem.myTokenStart, lexem.myTokenEnd, lexem.myState, myCharTable);
  }

  /**
   * just to make removeRange method available.
   */
  private static class MyList extends ArrayList<ProductionMarker> {
    public void removeRange(final int fromIndex, final int toIndex) {
      super.removeRange(fromIndex, toIndex);
    }
  }
}
