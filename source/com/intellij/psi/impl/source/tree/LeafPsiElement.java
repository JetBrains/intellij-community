package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

public class LeafPsiElement extends LeafElementImpl implements PsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.LeafPsiElement");

  protected LeafPsiElement(IElementType type, char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(type, buffer, startOffset, endOffset, table);
    setState(lexerState);
  }

  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getFirstChild() {
    return null;
  }

  public PsiElement getLastChild() {
    return null;
  }

  public void acceptChildren(PsiElementVisitor visitor) {
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(this);
  }

  public PsiElement getNextSibling() {
    return SharedImplUtil.getNextSibling(this);
  }

  public PsiElement getPrevSibling() {
    return SharedImplUtil.getPrevSibling(this);
  }

  public PsiFile getContainingFile() {
    return SharedImplUtil.getContainingFile(this);
  }

  public PsiElement findElementAt(int offset) {
    return this;
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  public PsiElement copy() {
    TreeElement elementCopy = copyElement();
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public boolean isValid() {
    return SharedImplUtil.isValid(this);
  }

  public boolean isWritable() {
    return SharedImplUtil.isWritable(this);
  }

  public PsiReference getReference() {
    return null;
  }

  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddRangeBefore(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException {
    LOG.assertTrue(getTreeParent() != null);
    CheckUtil.checkWritable(this);
    getTreeParent().deleteChildInternal(this);
  }

  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    LOG.assertTrue(getTreeParent() != null);
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(newElement);
    getTreeParent().replaceChildInternal(this, elementCopy);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    final PsiElement result = SourceTreeToPsiMap.treeElementToPsi(elementCopy);

    // invalidate replaced element
    setTreeNext(null);
    setTreePrev(null);
    setTreeParent(null);
    return result;
  }

  public void checkReplace(PsiElement newElement) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public String toString() {
    return "PsiElement" + "(" + getElementType().toString() + ")";
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     PsiElement place) {
    return true;
  }

  public PsiElement getContext() {
    return ResolveUtil.getContext(this);
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public boolean isPhysical() {
    PsiFile file = getContainingFile();
    if (file == null) return false;
    return file.isPhysical();
  }

  public GlobalSearchScope getResolveScope() {
    return ((PsiManagerImpl)getManager()).getFileManager().getResolveScope(this);
  }

  public GlobalSearchScope getUseScope() {
    return ((PsiManagerImpl)getManager()).getFileManager().getUseScope(this);
  }

  public Project getProject() {
    return getManager().getProject();
  }
}
