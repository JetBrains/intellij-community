package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class TreeElement extends ElementBase implements ASTNode, Constants, Cloneable {
  public static final TreeElement[] EMPTY_ARRAY = new TreeElement[0];
  protected TreeElement next = null; // this var could be not only next element pointer in ChildElements
  // use it _VERY_ carefuly!! If you are not sure use apropariate getter (getTreeNext()).

  public static final Key<PsiManager> MANAGER_KEY = Key.create("Element.MANAGER_KEY");
  public static final Key<PsiElement> PSI_ELEMENT_KEY = Key.create("Element.PsiElement");

  public Object clone() {
    TreeElement clone = (TreeElement)super.clone();
    clone.clearCaches();
    clone.next = null;
    return clone;
  }

  public ASTNode copyElement() {
    CharTable table = SharedImplUtil.findCharTableByTree(this);
    return ChangeUtil.copyElement(this, table);
  }

  public PsiManager getManager() {
    TreeElement element;
    for (element = this; element.getTreeParent() != null; element = element.getTreeParent()) {
    }

    if (element instanceof FileElement) { //TODO!!
      return element.getManager();
    }
    else {
      if (getTreeParent() != null) {
        return getTreeParent().getManager();
      }
      else {
        return getUserData(MANAGER_KEY);
      }
    }
  }

  public abstract int getTextLength();

  public abstract LeafElement findLeafElementAt(int offset);

  public abstract String getText();

  @NotNull
  public abstract char[] textToCharArray();

  public abstract boolean textContains(char c);

  public abstract TreeElement getFirstChildNode();

  public abstract TreeElement getLastChildNode();

  public TextRange getTextRange() {
  synchronized (PsiLock.LOCK) {
    int start = getStartOffset();
    return new TextRange(start, start + getTextLength());
  }
  }

  public int getStartOffset() {
  synchronized (PsiLock.LOCK) {
    final CompositeElement parent = getTreeParent();
    if (parent == null) return 0;
    int offset = parent.getStartOffset();
    for (TreeElement element1 = parent.firstChild; element1 != this; element1 = element1.getTreeNext()) {
      offset += element1.getTextLength();
    }
    return offset;
  }
  }

  public final int getStartOffsetInParent() {
    if (getTreeParent() == null) return -1;
    int offset = 0;
    for (ASTNode child = getTreeParent().getFirstChildNode(); child != this; child = child.getTreeNext()) {
      offset += child.getTextLength();
    }
    return offset;
  }

  public int getTextOffset() {
    return getStartOffset();
  }

  public boolean textMatches(CharSequence buffer, int startOffset, int endOffset) {
    return textMatches(this, buffer, startOffset) == endOffset;
  }

  private static int textMatches(ASTNode element, CharSequence buffer, int startOffset) {
    synchronized (PsiLock.LOCK) {
      if (element instanceof LeafElement) {
        final LeafElement leaf = (LeafElement)element;
        return leaf.textMatches(buffer, startOffset);
      }
      else {
        int curOffset = startOffset;
        for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
          curOffset = textMatches(child, buffer, curOffset);
          if (curOffset == -1) return -1;
        }
        return curOffset;
      }
    }
  }

  public boolean textMatches(CharSequence seq) {
    return textMatches(seq, 0, seq.length());
  }

  public boolean textMatches(PsiElement element) {
    return getTextLength() == element.getTextLength() && textMatches(element.getText());
  }

  @NonNls
  public String toString() {
    return "Element" + "(" + getElementType().toString() + ")";
  }

  public abstract IElementType getElementType();

  public abstract CompositeElement getTreeParent();

  public TreeElement getTreeNext() {
    return next;
  }

  public abstract TreeElement getTreePrev();

  public abstract void setTreePrev(TreeElement prev);

  public void setTreeNext(TreeElement next) {
    this.next = next;
  }

  public abstract void setTreeParent(CompositeElement parent);

  public void clearCaches() { }

  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  public final int hashCode() {
    return super.hashCode();
  }

  public ASTNode getTransformedFirstOrSelf() {
    return this;
  }

  public ASTNode getTransformedLastOrSelf() {
    return this;
  }

  public abstract void acceptTree(TreeElementVisitor visitor);
}

