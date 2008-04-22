package com.intellij.psi.impl.compiled;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ClsRepositoryPsiElement<T extends StubElement> extends ClsElementImpl {
  private final T myStub;

  protected ClsRepositoryPsiElement(final T stub) {
    myStub = stub;
  }

  public PsiElement getParent() {
    return myStub.getParentStub().getPsi();
  }

  public PsiManager getManager() {
    return getContainingFile().getManager();
  }

  public PsiFile getContainingFile() {
    StubElement p = myStub;
    while (!(p instanceof PsiFileStub)) {
      p = p.getParentStub();
    }
    return (PsiFile)p.getPsi();
  }

  public T getStub() {
    return myStub;
  }

  @NotNull
  public PsiElement[] getChildren() {
    final List stubs = getStub().getChildrenStubs();
    PsiElement[] children = new PsiElement[stubs.size()];
    for (int i = 0; i < stubs.size(); i++) {
      children[i] = ((StubElement)stubs.get(i)).getPsi();
    }
    return children;
  }

  public PsiElement getFirstChild() {
    final List<StubElement> children = getStub().getChildrenStubs();
    if (children.isEmpty()) return null;
    return children.get(0).getPsi();
  }

  public PsiElement getLastChild() {
    final List<StubElement> children = getStub().getChildrenStubs();
    if (children.isEmpty()) return null;
    return children.get(children.size() - 1).getPsi();
  }

  public PsiElement getNextSibling() {
    final PsiElement[] psiElements = getParent().getChildren();
    final int i = ArrayUtil.indexOf(psiElements, this);
    if (i < 0 || i >= psiElements.length - 1) {
      return null;
    }
    return psiElements[i + 1];
  }


  public PsiElement getPrevSibling() {
    final PsiElement[] psiElements = getParent().getChildren();
    final int i = ArrayUtil.indexOf(psiElements, this);
    if (i < 1) {
      return null;
    }
    return psiElements[i - 1];
  }
}
