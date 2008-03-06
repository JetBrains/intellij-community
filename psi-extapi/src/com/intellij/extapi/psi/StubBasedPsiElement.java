/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StubBasedPsiElement<T extends StubElement> extends ASTDelegatePsiElement {
  private PsiElement myParent;
  private volatile T myStub;
  private volatile ASTNode myNode;
  private final List<StubBasedPsiElement> myChildStubs = new ArrayList<StubBasedPsiElement>();
  private final IElementType myElementType;

  public StubBasedPsiElement(final PsiElement parent, final T stub, IElementType nodeType) {
    myParent = parent;
    myStub = stub;
    myElementType = nodeType;
    myNode = null;
    ((StubBasedPsiElement)parent).myChildStubs.add(this);
  }

  public StubBasedPsiElement(final ASTNode node) {
    myNode = node;
    myElementType = node.getElementType();
  }

  @NotNull
  public ASTNode getNode() {
    if (myNode == null) {
      ((StubBasedPsiElement)myParent).bindChildTrees();
      assert myNode != null;
    }

    return myNode;
  }

  private void bindChildTrees() {
    final ASTNode node = getNode();
    final Iterator<StubBasedPsiElement> it = myChildStubs.iterator();
    ASTNode childNode = node.getFirstChildNode();
    while (it.hasNext()) {
      StubBasedPsiElement stubChild = it.next();
      while (stubChild.myElementType == childNode.getElementType()) {
        childNode = childNode.getTreeNext();
      }
      stubChild.myNode = childNode;
      stubChild.myStub = null;
      childNode = childNode.getTreeNext();

      // TODO: need assertions we've bind that correctly.
    }

    myChildStubs.clear();
  }

  public PsiElement getParent() {
    if (myParent == null) {
      myParent = myNode.getTreeParent().getPsi();
    }
    return myParent;
  }

  protected T getStub() {
    return myStub;
  }
}