/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ASTDelegatePsiElement extends PsiElementBase {
  public PsiManagerEx getManager() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    return (PsiManagerEx)parent.getManager();
  }

  @NotNull
  public PsiElement[] getChildren() {
    PsiElement psiChild = getFirstChild();
    if (psiChild == null) return EMPTY_ARRAY;

    List<PsiElement> result = new ArrayList<PsiElement>();
    while (psiChild != null) {
      if (psiChild.getNode() instanceof CompositeElement) {
        result.add(psiChild);
      }
      psiChild = psiChild.getNextSibling();
    }
    return result.toArray(new PsiElement[result.size()]);
  }

  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(getNode());
  }

  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(getNode());
  }

  public PsiElement getNextSibling() {
    return SharedImplUtil.getNextSibling(getNode());
  }

  public PsiElement getPrevSibling() {
    return SharedImplUtil.getPrevSibling(getNode());
  }

  public TextRange getTextRange() {
    return getNode().getTextRange();
  }

  public int getStartOffsetInParent() {
    return getNode().getStartOffset() - getNode().getTreeParent().getStartOffset();
  }

  public int getTextLength() {
    return getNode().getTextLength();
  }

  public PsiElement findElementAt(int offset) {
    ASTNode treeElement = getNode().findLeafElementAt(offset);
    return SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public int getTextOffset() {
    return getNode().getStartOffset();
  }

  public String getText() {
    return getNode().getText();
  }

  @NotNull
  public char[] textToCharArray() {
    return getNode().getText().toCharArray();
  }

  public boolean textContains(char c) {
    return getNode().textContains(c);
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getNode().getCopyableUserData(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    getNode().putCopyableUserData(key, value);
  }

  @NotNull
  public abstract ASTNode getNode();

  public void subtreeChanged() {
  }

  @NotNull
  public Language getLanguage() {
    return getNode().getElementType().getLanguage();
  }

  @Nullable
  protected PsiElement findChildByType(IElementType type) {
    ASTNode node = getNode().findChildByType(type);
    return node == null ? null : node.getPsi();
  }

  protected PsiElement findChildByType(TokenSet type) {
    ASTNode node = TreeUtil.findChild(getNode(), type);
    return node == null ? null : node.getPsi();
  }

  @Nullable
  protected PsiElement findChildByFilter(TokenSet tokenSet) {
    ASTNode[] nodes = getNode().getChildren(tokenSet);
    return nodes == null || nodes.length == 0 ? null : nodes[0].getPsi();
  }

  protected <T extends PsiElement> T[] findChildrenByType(IElementType elementType, Class<T> arrayClass) {
    return ContainerUtil.map2Array(SharedImplUtil.getChildrenOfType(getNode(), elementType), arrayClass, new Function<ASTNode, T>() {
      public T fun(final ASTNode s) {
        return (T)s.getPsi();
      }
    });
  }

  protected <T extends PsiElement> T[] findChildrenByType(TokenSet elementType, Class<T> arrayClass) {
    return (T[])ContainerUtil.map2Array(getNode().getChildren(elementType), arrayClass, new Function<ASTNode, PsiElement>() {
      public PsiElement fun(final ASTNode s) {
        return s.getPsi();
      }
    });
  }

  public PsiElement copy() {
    return getNode().copyElement().getPsi();
  }
}
