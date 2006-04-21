package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
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
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class LeafPsiElement extends CharTableBasedLeafElementImpl implements PsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.LeafPsiElement");

  public LeafPsiElement(IElementType type, char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(type, buffer, startOffset, endOffset, table);
    setState(lexerState);
  }

  @NotNull
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
    if (!isValid())
      throw new PsiInvalidElementAccessException(this);
    return SharedImplUtil.getContainingFile(this);
  }

  public PsiElement findElementAt(int offset) {
    return this;
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  public PsiElement copy() {
    ASTNode elementCopy = copyElement();
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

  @NotNull
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

  public void delete() throws IncorrectOperationException {
    LOG.assertTrue(getTreeParent() != null);
    CheckUtil.checkWritable(this);
    getTreeParent().deleteChildInternal(this);
    TreeUtil.invalidate(this);
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

    TreeUtil.invalidate(this);
    return result;
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

  @NotNull
  public SearchScope getUseScope() {
    return getManager().getSearchHelper().getUseScope(this);
  }

  @NotNull
  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) throw new PsiInvalidElementAccessException(this);

    return manager.getProject();
  }

  @NotNull
  public Language getLanguage() {
    return getElementType().getLanguage();
  }

  public ASTNode getNode() {
    return this;
  }

  public PsiElement getPsi() {
    return this;
  }
}
