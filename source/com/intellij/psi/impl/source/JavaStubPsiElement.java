/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class JavaStubPsiElement<T extends StubElement> extends StubBasedPsiElementBase<T> implements StubBasedPsiElement<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.JavaStubPsiElement");

  public JavaStubPsiElement(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public JavaStubPsiElement(final ASTNode node) {
    super(node);
  }

  @NotNull
  public Language getLanguage() {
    return StdLanguages.JAVA;
  }

  public PsiElement getParent() {
    return getParentByStub();
  }

  public int getTextOffset() {
    return calcTreeElement().getTextOffset();
  }

  protected CompositeElement calcTreeElement() {
    return (CompositeElement)getNode();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, null, null);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public final void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, null, null);
  }

  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
  }

  public void delete() throws IncorrectOperationException {
    ASTNode treeElement = calcTreeElement();
    LOG.assertTrue(treeElement.getTreeParent() != null);
    CheckUtil.checkWritable(this);
    ((CompositeElement)treeElement.getTreeParent()).deleteChildInternal(treeElement);
  }

  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (first == null) {
      LOG.assertTrue(last == null);
      return;
    }
    ASTNode firstElement = SourceTreeToPsiMap.psiElementToTree(first);
    ASTNode lastElement = SourceTreeToPsiMap.psiElementToTree(last);
    CompositeElement treeElement = calcTreeElement();
    LOG.assertTrue(firstElement.getTreeParent() == treeElement);
    LOG.assertTrue(lastElement.getTreeParent() == treeElement);
    CodeEditUtil.removeChildren(treeElement, firstElement, lastElement);
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    CompositeElement treeElement = calcTreeElement();
    LOG.assertTrue(treeElement.getTreeParent() != null);
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(newElement);
    treeElement.getTreeParent().replaceChildInternal(treeElement, elementCopy);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public void navigate(boolean requestFocus) {
    EditSourceUtil.getDescriptor(this).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    CompositeElement treeElement = calcTreeElement();
    TreeElement childNode = treeElement.getFirstChildNode();

    TreeElement prevSibling = null;
    while (childNode != null) {
      if (childNode instanceof ChameleonElement) {
      TreeElement newChild = (TreeElement)childNode.getTransformedFirstOrSelf();
        if (newChild == null) {
          childNode = prevSibling == null ? treeElement.getFirstChildNode() : prevSibling.getTreeNext();
          continue;
        }
        childNode = newChild;
      }

      final PsiElement psi;
      if (childNode instanceof PsiElement) {
        psi = (PsiElement)childNode;
      }
      else {
        psi = childNode.getPsi();
      }
      psi.accept(visitor);

      prevSibling = childNode;
      childNode = childNode.getTreeNext();
    }
  }

  protected Object clone() {
    CompositeElement treeElement = calcTreeElement();
    CompositeElement treeElementClone
      = (CompositeElement)(treeElement.parent != null ? treeElement.copyElement() : (ASTNode)treeElement.clone());
    /*
    if (treeElementClone.getPsiElement() != null) {
      return treeElementClone.getPsiElement();
    }
    */
    return cloneImpl(treeElementClone);
  }

  protected StubBasedPsiElementBase cloneImpl(CompositeElement treeElementClone) {
    StubBasedPsiElementBase clone = (StubBasedPsiElementBase)super.clone();
    clone.setNode(treeElementClone);
    treeElementClone.setPsi(clone);
    return clone;
  }

  public void subtreeChanged() {
    final CompositeElement compositeElement = calcTreeElement();
    if (compositeElement != null) compositeElement.clearCaches();
  }

  @NotNull
  public PsiElement[] getChildren() {
    PsiElement psiChild = getFirstChild();
    if (psiChild == null) return EMPTY_ARRAY;

    List<PsiElement> result = new ArrayList<PsiElement>();
    while (psiChild != null) {
      result.add(psiChild);
      psiChild = psiChild.getNextSibling();
    }

    return result.toArray(new PsiElement[result.size()]);
  }  
}