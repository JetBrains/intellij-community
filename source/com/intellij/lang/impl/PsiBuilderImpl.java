package com.intellij.lang.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
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

  private List<Lexem> myLexems = new ArrayList<Lexem>();
  private List<Object> myProduction = new ArrayList<Object>();

  private Lexer myLexer;
  private int myCurrentLexem;
  private CharTable myCharTable;
  private boolean myStarted = false;

  public PsiBuilderImpl(final Lexer lexer, final CharTable charTable) {
    myCharTable = charTable;
    myLexer = lexer;
  }

  private static class MarkerImpl implements Marker {
    public IElementType myType;
    public int myLexemIndex;
    public DoneMarker myDoneMarker = null;

    public MarkerImpl(final IElementType type, int idx) {
      myType = type;
      myLexemIndex = idx;
    }
  }

  private class LexemImpl implements Lexem {
    private IElementType myTokenType;
    private int myTokenStart;
    private int myTokenEnd;
    private int myState;

    public LexemImpl() {
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

  private static class DoneMarker {
    public MarkerImpl myMarker;

    public DoneMarker(final MarkerImpl marker) {
      myMarker = marker;
    }
  }

  private static class ErrorItem {
    String myMessage;

    public ErrorItem(final String message) {
      myMessage = message;
    }
  }

  public void advanceLexer() {
    myProduction.add(getCurrentLexem());
    myCurrentLexem++;
    if (myCurrentLexem > myLexems.size()) {
      LOG.assertTrue(!eof());
      final Lexem lxm = advanceOriginalLexer();
      if (lxm == null) return;
      myLexems.add(lxm);
    }
  }

  private Lexem advanceOriginalLexer() {
    myLexer.advance();
    return myLexer.getTokenType() == null ? null : new LexemImpl();
  }


  public Lexem getCurrentLexem() {
    return myLexems.get(myCurrentLexem);
  }

  public Marker start(IElementType symbol) {
    if (!myStarted) {
      myStarted = true;
      myLexems.add(new LexemImpl());
    }

    MarkerImpl marker = new MarkerImpl(symbol, myCurrentLexem);
    myProduction.add(marker);
    return marker;
  }

  public boolean eof() {
    if (myCurrentLexem + 1 < myLexems.size()) return false;
    if (myLexer.getTokenType() == null) return true;
    final Lexem lxm = advanceOriginalLexer();
    if (lxm == null) return true;
    myLexems.add(lxm);
    return false;
  }

  public void rollbackTo(Marker marker) {
    myCurrentLexem = ((MarkerImpl)marker).myLexemIndex;
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
    LOG.assertTrue(((MarkerImpl)marker).myDoneMarker == null, "Marker already done.");
    int idx = myProduction.lastIndexOf(marker);
    LOG.assertTrue(idx >= 0, "Marker never been added.");

    for (int i = myProduction.size() - 1; i > idx; i--) {
      Object item = myProduction.get(i);
      if (item instanceof Marker) {
        MarkerImpl otherMarker = (MarkerImpl)item;
        if (otherMarker.myDoneMarker == null) {
          LOG.error("Another not done marker of type [" + otherMarker.myType + "] added after this one. Must be done before this.");
        }
      }
    }

    DoneMarker doneMarker = new DoneMarker((MarkerImpl)marker);
      ((MarkerImpl)marker).myDoneMarker = doneMarker;
    myProduction.add(doneMarker);
  }

  public void insertErrorElement(String messageText) {
    myProduction.add(new ErrorItem(messageText));
  }

  public ASTNode getTreeBuilt() {
    MarkerImpl rootMarker = (MarkerImpl)myProduction.get(0);
    ASTNode rootNode = new CompositeElement(rootMarker.myType);
    rootNode.putUserData(CharTable.CHAR_TABLE_KEY, myCharTable);

    ASTNode curNode = rootNode;
    for (int i = 1; i < myProduction.size(); i++) {
      LOG.assertTrue(curNode != null, "Unexpected end of the production");
      Object item = myProduction.get(i);
      if (item instanceof LexemImpl) {
        LexemImpl lexem = (LexemImpl)item;
        final LeafPsiElement childNode = new LeafPsiElement(lexem.getTokenType(),
                                                            myLexer.getBuffer(),
                                                            lexem.myTokenStart,
                                                            lexem.myTokenEnd,
                                                            lexem.myState,
                                                            myCharTable);
        TreeUtil.addChildren((CompositeElement)curNode, childNode);
      }
      else if (item instanceof MarkerImpl) {
        MarkerImpl marker = (MarkerImpl)item;
        ASTNode childNode = new CompositeElement(marker.myType);
        TreeUtil.addChildren((CompositeElement)curNode, (TreeElement)childNode);
        curNode = childNode;
      }
      else if (item instanceof DoneMarker) {
        DoneMarker doneMarker = (DoneMarker)item;
        LOG.assertTrue(doneMarker.myMarker.myType == curNode.getElementType());
        curNode = curNode.getTreeParent();
      }
      else if (item instanceof ErrorItem) {
        final PsiErrorElementImpl errorElement = new PsiErrorElementImpl();
        errorElement.setErrorDescription(((ErrorItem)item).myMessage);
        TreeUtil.addChildren((CompositeElement)curNode, errorElement);
      }
    }

    LOG.assertTrue(curNode == null, "Unbalanced tree");

    return rootNode;
  }
}
