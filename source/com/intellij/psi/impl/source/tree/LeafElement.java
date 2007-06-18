package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LeafElement extends TreeElement {
  private final IElementType myType;
  private static final int TEXT_MATCHES_THRESHOLD = 5;

  public abstract char charAt(int position);

  public abstract int searchWord(int startOffset, StringSearcher searcher);

  public abstract int copyTo(char[] buffer, int start);

  protected LeafElement(IElementType type) {
    myType = type;
  }

  public final LeafElement findLeafElementAt(int offset) {
    return this;
  }

  public String getText() {
    return getInternedText().toString();
  }

  public abstract void setText(String text);
  public abstract int textMatches(CharSequence buffer, int start);

  @SuppressWarnings({"MethodOverloadsMethodOfSuperclass"})
  public boolean textMatches(final CharSequence buf, int start, int end) {
    final CharSequence text = getInternedText();
    final int len = text.length();

    if (end - start != len) return false;
    if (buf == text) return true;

    if (len > TEXT_MATCHES_THRESHOLD && text instanceof String && buf instanceof String) {
      return ((String)text).regionMatches(0,(String)buf,start,len);
    }

    for (int i = 0; i < len; i++) {
      if (text.charAt(i) != buf.charAt(start + i)) return false;
    }

    return true;
  }

  public void registerInCharTable(CharTable table) { }

  public IElementType getElementType() {
    return myType;
  }

  public void acceptTree(TreeElementVisitor visitor) {
    visitor.visitLeaf(this);
  }

  public abstract CharSequence getInternedText();

  public ASTNode findChildByType(IElementType type) {
    return null;
  }

  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet typesSet) {
    return null;
  }

  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet typesSet, @Nullable ASTNode anchor) {
    return null;
  }

  public int hc() {
    final CharSequence text = getInternedText();
    final int len = text.length();
    int hc = 0;

    if (len > TEXT_MATCHES_THRESHOLD && text instanceof String) {
      final String str=(String)text;

      for (int i = 0; i < len; i++) {
        hc += str.charAt(i);
      }

      return hc;
    } else {
      for (int i = 0; i < len; i++) {
        hc += text.charAt(i);
      }
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

  public void addLeaf(@NotNull final IElementType leafType, final CharSequence leafText, final ASTNode anchorBefore) {
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
