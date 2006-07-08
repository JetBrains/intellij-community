package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.StringSearcher;

public abstract class LeafElement extends TreeElement {
  private volatile int myState = 0; // 16 bit for type, 15 bit for state and 1 bit for parentFlag

  public abstract char charAt(int position);

  public abstract int searchWord(int startOffset, StringSearcher searcher);

  public abstract int copyTo(char[] buffer, int start);

  protected LeafElement(IElementType type) {
    myState = type != null ? type.getIndex() : 0;
  }

  public final LeafElement findLeafElementAt(int offset) {
    return this;
  }

  public String getText() {
    return getInternedText().toString();
  }

  public abstract void setText(String text);
  public abstract int textMatches(CharSequence buffer, int start);

  public void registerInCharTable(CharTable table) { }

  //boolean isLast = false;
  public synchronized void setState(int state) {
    myState = myState & 0x8000FFFF | (((state + 1) & 0x7FFF) << 16);
  }

  public IElementType getElementType() {
    short sh = 0;
    sh |= (short)(myState & 0xFFFF);
    return IElementType.find(sh);
  }

  public int getState() {
    return ((myState >> 16) & 0x7FFF) - 1;
  }

  private boolean isLast() {
    return (myState & 0x80000000) != 0;
  }

  private void setLast(boolean last) {
    if (last) {
      myState |= 0x80000000;
    }
    else {
      myState &= 0x7FFFFFFF;
    }
  }

  public void acceptTree(TreeElementVisitor visitor) {
    visitor.visitLeaf(this);
  }

  public Object clone() {
    LeafElement clone = (LeafElement)super.clone();
    clone.setState(-1);
    return clone;
  }

  public abstract CharSequence getInternedText();

  public ASTNode findChildByType(IElementType type) {
    return null;
  }

  public int hc() {
    final CharSequence text = getInternedText();

    int hc = 0;
    for (int i = 0; i < text.length(); i++) {
      hc += text.charAt(i);
    }

    return hc;
  }

  public TreeElement getFirstChildNode() {
    return null;
  }

  public TreeElement getLastChildNode() {
    return null;
  }

  public ASTNode[] getChildren(TokenSet filter) {
    return TreeElement.EMPTY_ARRAY;
  }

  public void addChild(ASTNode child, ASTNode anchorBefore) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void addChild(ASTNode child) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void removeChild(ASTNode child) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void replaceChild(ASTNode oldChild, ASTNode newChild) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void replaceAllChildrenToChildrenOf(ASTNode anotherParent) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void removeRange(ASTNode first, ASTNode firstWhichStayInTree) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void addChildren(ASTNode firstChild, ASTNode lastChild, ASTNode anchorBefore) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public PsiElement getPsi() {
    return null;
  }

  public boolean isChameleon(){
    return false;
  }

  public abstract void setInternedText(CharSequence id);
}
