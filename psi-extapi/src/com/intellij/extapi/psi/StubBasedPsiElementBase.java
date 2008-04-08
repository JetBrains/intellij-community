/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
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
      assert file.getTreeElement() == null;
      file.loadTreeElement();
      if (myNode == null) {
        assert false: "failed to bind stub to AST for element " + this + " in " +
                      (file.getVirtualFile() == null ? "<unknown file>" : file.getVirtualFile().getPath());
      }
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

      return (PsiFile)((PsiFileStub)stub).getPsi();
    }

    return super.getContainingFile();
  }

  public boolean isWritable() {
    return getContainingFile().isWritable();
  }

  public boolean isValid() {
    if (myStub != null) {
      if (myStub instanceof PsiFileStub) {
        return myStub.getPsi().isValid();
      }

      return myStub.getParentStub().getPsi().isValid();
    }

    return super.isValid();
  }

  public PsiManagerEx getManager() {
    return (PsiManagerEx)getContainingFile().getManager();
  }

  @NotNull
  public Project getProject() {
    return getContainingFile().getProject();
  }

  public boolean isPhysical() {
    return getContainingFile().isPhysical();
  }

  public PsiElement getContext() {
    if (myStub != null) {
      if (!(myStub instanceof PsiFileStub)) {
        return myStub.getParentStub().getPsi();
      }
    }
    return super.getContext();
  }

  protected final PsiElement getParentByStub() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getParentStub().getPsi();
    }

    return SharedImplUtil.getParent(getNode());
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
  public <Stub extends StubElement, Psi extends PsiElement> Psi getStubOrPsiChild(final IStubElementType<Stub, Psi> elementType) {
    if (myStub != null) {
      final StubElement<Psi> element = myStub.findChildStubByType(elementType);
      if (element != null) {
        return element.getPsi();
      }
    }
    else {
      final ASTNode childNode = getNode().findChildByType(elementType);
      if (childNode != null) {
        return (Psi)childNode.getPsi();
      }
    }
    return null;
  }

  @NotNull
  public <Stub extends StubElement, Psi extends PsiElement> Psi getRequiredStubOrPsiChild(final IStubElementType<Stub, Psi> elementType) {
    Psi result = getStubOrPsiChild(elementType);
    assert result != null: "Missing required child of type " + elementType;
    return result;
  }


  public <Stub extends StubElement, Psi extends PsiElement> Psi[] getStubOrPsiChildren(final IStubElementType<Stub, Psi> elementType, Psi[] array) {
    if (myStub != null) {
      //noinspection unchecked
      return (Psi[])myStub.getChildrenByType(elementType, array);
    }
    else {
      final ASTNode[] nodes = getNode().getChildren(TokenSet.create(elementType));
      Psi[] psiElements = (Psi[])java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), nodes.length);
      for (int i = 0; i < nodes.length; i++) {
        psiElements[i] = (Psi)nodes[i].getPsi();
      }
      return psiElements;
    }
  }

  @Nullable
  protected <E extends PsiElement> E getStubOrPsiParentOfType(final Class<E> parentClass) {
    if (myStub != null) {
      //noinspection unchecked
      return (E)myStub.getParentStubOfType(parentClass);
    }
    return PsiTreeUtil.getParentOfType(this, parentClass);
  }

  protected Object clone() {
    final StubBasedPsiElementBase stubbless = (StubBasedPsiElementBase)super.clone();
    stubbless.myStub = null;
    return stubbless;
  }
}