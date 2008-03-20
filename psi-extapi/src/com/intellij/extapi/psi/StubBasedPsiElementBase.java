/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StubBasedPsiElementBase<T extends StubElement> extends ASTDelegatePsiElement {
  private volatile T myStub;
  private volatile ASTNode myNode;
  private final IElementType myElementType;

  public StubBasedPsiElementBase(final T stub, IStubElementType nodeType) {
    myStub = stub;
    myElementType = nodeType;
    myNode = null;
  }

  public StubBasedPsiElementBase(final ASTNode node) {
    myNode = node;
    myElementType = node.getElementType();
  }

  @NotNull
  public ASTNode getNode() {
    if (myNode == null) {
      PsiFileImpl file = (PsiFileImpl)getContainingFile();
      file.loadTreeElement();
      assert myNode != null;
    }

    return myNode;
  }

  public void setNode(final ASTNode node) {
    myNode = node;
  }

  public PsiFile getContainingFile() {
    if (myStub != null) {
      StubElement stub = myStub;
      while (!(stub instanceof PsiFileStub)) {
        stub = stub.getParentStub();
      }

      return ((PsiFileStub)stub).getPsi();
    }

    return super.getContainingFile();
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(getNode());
  }

  public IStubElementType getElementType() {
    return (IStubElementType)myElementType;
  }

  public T getStub() {
    return myStub;
  }

  public void setStub(T stub) {
    myStub = stub;
  }

  @Nullable
  protected PsiElement getStubOrPsiChild(final IElementType elementType) {
    if (myStub != null) {
      final StubElement element = myStub.findChildStubByType(elementType);
      if (element != null) {
        return element.getPsi();
      }
    }
    else {
      final ASTNode childNode = getNode().findChildByType(elementType);
      if (childNode != null) {
        return childNode.getPsi();
      }
    }
    return null;
  }

  @NotNull
  protected PsiElement getRequiredStubOrPsiChild(final IElementType elementType) {
    PsiElement result = getStubOrPsiChild(elementType);
    assert result != null: "Missing required child of type " + elementType;
    return result;
  }


  protected <E> E[] getStubOrPsiChildren(final IElementType elementType, E[] array) {
    if (myStub != null) {
      //noinspection unchecked
      return (E[])myStub.getChildrenByType(elementType, array);
    }
    else {
      final ASTNode[] nodes = getNode().getChildren(TokenSet.create(elementType));
      E[] psiElements = (E[])java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        psiElements[i] = (E)nodes[i].getPsi();
      }
      return psiElements;
    }
  }
}