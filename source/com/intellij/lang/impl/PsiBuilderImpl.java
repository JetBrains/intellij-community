package com.intellij.lang.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;

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

  private List<Token> myLexems = new ArrayList<Token>();
  private List<ProductionMarker> myProduction = new ArrayList<ProductionMarker>();

  private Lexer myLexer;
  private boolean myFileLevelParsing;
  private TokenSet myWhitespaces;
  private TokenSet myComments;
  private int myCurrentLexem;
  private CharTable myCharTable;

  public PsiBuilderImpl(final Lexer lexer, final CharTable charTable, boolean fileLevelParsing, TokenSet whitespaces, TokenSet comments) {
    myCharTable = charTable;
    myLexer = lexer;
    myFileLevelParsing = fileLevelParsing;
    myWhitespaces = whitespaces;
    myComments = comments;
  }

  private class StartMarker extends ProductionMarker implements Marker {
    public IElementType myType;
    public DoneMarker myDoneMarker = null;

    public StartMarker(int idx) {
      super(idx);
    }

    public Marker preceed() {
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
    int idx = myProduction.indexOf(marker);
    LOG.assertTrue(idx >= 0, "Cannot preceed dropped or rolled-back marker");
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

  public IElementType getTokenType() {
    final Token lex = getCurrentToken();
    final IElementType tokenType = lex == null ? null : lex.getTokenType();
    LOG.assertTrue(!whitespaceOrComment(tokenType));
    return tokenType;
  }

  public void advanceLexer() {
    myCurrentLexem++;
  }

  public Token getCurrentToken() {
    Token lastToken;
    while(true) {
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

  private Token getTokenOrWhitespace() {
    if (myCurrentLexem >= myLexems.size()) {
      if (myLexer.getTokenType() == null) return null;
      myLexems.add(new Token());
      myLexer.advance();
    }
    return myLexems.get(myCurrentLexem);
  }

  private boolean whitespaceOrComment(IElementType token) {
    return myWhitespaces.isInSet(token) || myComments.isInSet(token);
  }

  public Marker mark() {
    StartMarker marker = new StartMarker(myCurrentLexem);
    myProduction.add(marker);
    return marker;
  }

  public boolean eof() {
    if (myCurrentLexem + 1 < myLexems.size()) return false;
    if (myLexer.getTokenType() == null) return true;
    return getCurrentToken() == null;
  }

  public void rollbackTo(Marker marker) {
    myCurrentLexem = ((StartMarker)marker).myLexemIndex;
    int idx = myProduction.lastIndexOf(marker);

    LOG.assertTrue(idx >= 0, "The marker must be added before rolled back to.");
    for (int i = myProduction.size() - 1; i >= idx; i--) {
      myProduction.remove(i);
    }
  }

  public void drop(Marker marker) {
    final boolean removed = myProduction.remove(marker);
    LOG.assertTrue(removed, "The marker must be added before it is dropped.");
  }

  public void done(Marker marker) {
    LOG.assertTrue(((StartMarker)marker).myDoneMarker == null, "Marker already done.");
    int idx = myProduction.lastIndexOf(marker);
    LOG.assertTrue(idx >= 0, "Marker never been added.");

    for (int i = myProduction.size() - 1; i > idx; i--) {
      Object item = myProduction.get(i);
      if (item instanceof Marker) {
        StartMarker otherMarker = (StartMarker)item;
        if (otherMarker.myDoneMarker == null) {
          LOG.error("Another not done marker of type [" + otherMarker.myType + "] added after this one. Must be done before this.");
        }
      }
    }

    DoneMarker doneMarker = new DoneMarker((StartMarker)marker, myCurrentLexem);
    ((StartMarker)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  public void error(String messageText) {
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

    ASTNode curNode = rootNode;
    int curToken = 0;
    for (int i = 1; i < myProduction.size(); i++) {
      LOG.assertTrue(curNode != null, "Unexpected end of the production");
      ProductionMarker item = myProduction.get(i);
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
        final PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
        errorElement.setErrorDescription(((ErrorItem)item).myMessage);
        TreeUtil.addChildren((CompositeElement)curNode, errorElement);
      }
    }

    LOG.assertTrue(curToken == myLexems.size(), "Not all of the tokens inserted to the tree");
    LOG.assertTrue(curNode == null, "Unbalanced tree");

    return rootNode;
  }

  private int insertLeafs(int curToken, final int lastIdx, final ASTNode curNode) {
    while (curToken < lastIdx) {
      Token lexem = myLexems.get(curToken++);
      final IElementType type = lexem.getTokenType();
      final LeafPsiElement childNode = new LeafPsiElement(type,
                                                          myLexer.getBuffer(),
                                                          lexem.myTokenStart,
                                                          lexem.myTokenEnd,
                                                          lexem.myState,
                                                          myCharTable);
      TreeUtil.addChildren((CompositeElement)curNode, childNode);
    }
    return curToken;
  }
}
