package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class TreeElement extends ElementBase implements ASTNode, Constants, Cloneable {
  public static final TreeElement[] EMPTY_ARRAY = new TreeElement[0];
  public TreeElement next = null;
  public TreeElement prev = null;
  public CompositeElement parent = null;

  public static final Key<PsiManager> MANAGER_KEY = Key.create("Element.MANAGER_KEY");
  public static final Key<PsiElement> PSI_ELEMENT_KEY = Key.create("Element.PsiElement");

  public Object clone() {
    TreeElement clone = (TreeElement)super.clone();
    clone.clearCaches();

    clone.next = null;
    clone.prev = null;
    clone.parent = null;

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
      if (parent == null) return 0;
      int offset = parent.getStartOffset();
      for (TreeElement element1 = parent.firstChild; element1 != this; element1 = element1.next) {
        offset += element1.getTextLength();
      }
      return offset;
    }
  }

  public final int getStartOffsetInParent() {
    if (parent == null) return -1;
    int offset = 0;
    for (TreeElement child = parent.firstChild; child != this; child = child.next) {
      offset += child.getTextLength();
    }
    return offset;
  }

  public int getTextOffset() {
    return getStartOffset();
  }

  public boolean textMatches(CharSequence buffer, int startOffset, int endOffset) {
    synchronized (PsiLock.LOCK) {
      return textMatches(this, buffer, startOffset) == endOffset;
    }
  }

  private static int textMatches(ASTNode element, CharSequence buffer, int startOffset) {
    if (element instanceof LeafElement) {
      final LeafElement leaf = (LeafElement)element;
      return leaf.textMatches(buffer, startOffset);
    }
    else {
      int curOffset = startOffset;
      for (TreeElement child = ((CompositeElement)element).firstChild; child != null; child = child.next) {
        curOffset = textMatches(child, buffer, curOffset);
        if (curOffset == -1) return -1;
      }
      return curOffset;
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

  public final CompositeElement getTreeParent() {
    return parent;
    /*
    if (isLast()) return (CompositeElement)next;

    TreeElement next = this.next;
    while (next instanceof LeafElement && !((LeafElement)next).isLast()) next = next.next;
    if (next != null) return next.getTreeParent();
    return null;
    */
  }

  public final TreeElement getTreePrev() {
    return prev;
    /*
    final CompositeElement parent = getTreeParent();
    if (parent == null) return null;
    TreeElement firstChild = parent.firstChild;
    if (firstChild == this) return null;
    while (firstChild != null && firstChild.next != this) firstChild = firstChild.next;
    return firstChild;
    */
  }

  public final void setTreeParent(CompositeElement parent) {
    this.parent = parent;
    /*
    if (next == null || isLast()) {
      next = parent;
      setLast(true);
    }
    */
  }

  public final void setTreePrev(TreeElement prev) {
    this.prev = prev;
  }

  public final TreeElement getTreeNext() {
    return next;
  }

  public final void setTreeNext(TreeElement next) {
    this.next = next;
  }

  public void clearCaches() { }

  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  public final int hashCode() {
    return super.hashCode();
  }

  public abstract int hc(); // Used in tree diffing

  public ASTNode getTransformedFirstOrSelf() {
    return this;
  }

  public ASTNode getTransformedLastOrSelf() {
    return this;
  }

  public abstract void acceptTree(TreeElementVisitor visitor);

  public void onInvalidated() {
    final Boolean trackInvalidation = getUserData(DebugUtil.TRACK_INVALIDATION);
    if (trackInvalidation != null && trackInvalidation) {
      //noinspection HardCodedStringLiteral
      new Throwable("Element invalidated").printStackTrace();
    }
  }
}

