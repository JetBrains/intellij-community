package com.intellij.psi.impl.source.tree;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

public abstract class CompositePsiElement extends CompositeElement implements PsiElement, NavigationItem {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.CompositePsiElement");

  protected CompositePsiElement(IElementType type) {
    super(type);
  }

  public final PsiElement[] getChildren() {
    return getChildrenAsPsiElements(null, PSI_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(this);
  }

  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(this);
  }

  public void acceptChildren(PsiElementVisitor visitor) {
    for (PsiElement child = getFirstChild(); child != null; ) {
      final PsiElement nextSibling = child.getNextSibling();
      child.accept(visitor);
      child = nextSibling;
    }
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
    TreeElement leaf = findLeafElementAt(offset);
    return SourceTreeToPsiMap.treeElementToPsi(leaf);
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
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    elementCopy = addInternal(elementCopy, elementCopy, null, null);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    elementCopy = addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    elementCopy = addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public final void checkAdd(PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public final void checkAddBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public final void checkAddAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public final PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, null, null);
  }

  public final PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
  }

  public final PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
  }

  public final void checkAddRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public final void checkAddRangeBefore(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public final void checkAddRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
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
    CheckUtil.checkWritable(this);
    TreeElement firstElement = SourceTreeToPsiMap.psiElementToTree(first);
    TreeElement lastElement = SourceTreeToPsiMap.psiElementToTree(last);
    LOG.assertTrue(firstElement.getTreeParent() == this);
    LOG.assertTrue(lastElement.getTreeParent() == this);
    CodeEditUtil.removeChildren(this, firstElement, lastElement);
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

  public void accept(PsiElementVisitor visitor) { //TODO: remove this method!!
    visitor.visitElement(this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     PsiElement place) {
    return true;
  }

  public String toString() {
    return "PsiElement" + "(" + getElementType().toString() + ")";
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

  public ItemPresentation getPresentation() {
    return null;
  }

  public String getName() {
    return null;
  }

  public void navigate(boolean requestFocus) {
    EditSourceUtil.getDescriptor(getNavigationElement()).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return true;
  }

  public FileStatus getFileStatus() {
    if (!isPhysical()) return FileStatus.NOT_CHANGED;
    PsiFile contFile = getContainingFile();
    if (contFile == null) return FileStatus.NOT_CHANGED;
    VirtualFile vFile = contFile.getVirtualFile();
    return vFile != null ? FileStatusManager.getInstance(getProject()).getStatus(vFile) : FileStatus.NOT_CHANGED;
  }

  public Project getProject() {
    return getManager().getProject();
  }
}
