package com.intellij.psi.impl.source;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 20, 2005
 * Time: 2:54:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class ASTWrapperPsiElement extends ElementBase implements PsiElement, NavigationItem {
  private ASTNode myNode;

  public ASTWrapperPsiElement(final ASTNode node) {
    myNode = node;
  }

  public Project getProject() {
    final PsiManager manager = getManager();
    return manager != null ? manager.getProject() : null;
  }

  public PsiManager getManager() {
    return getParent().getManager();
  }

  public PsiElement[] getChildren() {
    return new PsiElement[0];
  }

  public PsiElement getParent() {
    return SourceTreeToPsiMap.treeElementToPsi(myNode.getTreeParent());
  }

  public PsiElement getFirstChild() {
    return SourceTreeToPsiMap.treeElementToPsi(myNode.getFirstChildNode());
  }

  public PsiElement getLastChild() {
    return SourceTreeToPsiMap.treeElementToPsi(myNode.getLastChildNode());
  }

  public PsiElement getNextSibling() {
    return SourceTreeToPsiMap.treeElementToPsi(myNode.getTreeNext());
  }

  public PsiElement getPrevSibling() {
    return SourceTreeToPsiMap.treeElementToPsi(myNode.getTreePrev());
  }

  public PsiFile getContainingFile() {
    return getParent().getContainingFile();
  }

  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  public int getStartOffsetInParent() {
    return myNode.getStartOffset() - myNode.getTreeParent().getStartOffset();
  }

  public int getTextLength() {
    return myNode.getTextLength();
  }

  public PsiElement findElementAt(int offset) {
    ASTNode treeElement = myNode.findLeafElementAt(offset);
    return SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  public int getTextOffset() {
    return myNode.getStartOffset();
  }

  public String getText() {
    return myNode.getText();
  }

  public char[] textToCharArray() {
    return myNode.getText().toCharArray();
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  //Q: get rid of these methods?
  public boolean textMatches(CharSequence text) {
    throw new UnsupportedOperationException();
  }

  public boolean textMatches(PsiElement element) {
    throw new UnsupportedOperationException();
  }

  public boolean textContains(char c) {
    return myNode.textContains(c);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public void acceptChildren(PsiElementVisitor visitor) {
    ASTNode child = myNode.getFirstChildNode();
    while (child != null) {
      SourceTreeToPsiMap.treeElementToPsi(child).accept(visitor);
      child = child.getTreeNext();
    }
  }

  public PsiElement copy() {
    return (PsiElement)clone();
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {

    throw new UnsupportedOperationException();
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public void checkAdd(PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public void delete() throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public void checkDelete() throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public boolean isValid() {
    return getManager() != null;
  }

  public boolean isWritable() {
    return getContainingFile().isWritable();
  }

  public PsiReference getReference() {
    return null;
  }

  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     PsiElement place) {
    return true;
  }

  public PsiElement getContext() {
    throw new UnsupportedOperationException();
  }

  public boolean isPhysical() {
    return getContainingFile().isPhysical();
  }

  public GlobalSearchScope getResolveScope() {
    throw new UnsupportedOperationException();
  }

  public SearchScope getUseScope() {
    throw new UnsupportedOperationException();
  }

  public ItemPresentation getPresentation() {
    return null;
  }

  public String getName() {
    return null;
  }

  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
      EditSourceUtil.getDescriptor(getNavigationElement()).navigate(requestFocus);
    }
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

  public <T> T getCopyableUserData(Key<T> key) {
    return myNode.getCopyableUserData(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    myNode.putCopyableUserData(key, value);
  }

  public ASTNode getNode() {
    return myNode;
  }

  public Language getLanguage() {
    return myNode.getElementType().getLanguage();
  }
}
