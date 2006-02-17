package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class LightPsiFileImpl extends PsiFileImplBase {
  public LightPsiFileImpl(final FileViewProvider provider, final Language language) {
    super(provider, language);
  }

  public boolean isContentsLoaded() {
    return true;
  }

  public boolean isWritable() {
    PsiFile file = getContainingFile();
    if (file == null) return false;
    return file.isWritable();
  }

  public PsiElement getParent() {
    return SourceTreeToPsiMap.treeElementToPsi(calcTreeElement().getTreeParent());
  }

  public PsiFile getContainingFile() {
    return SharedImplUtil.getContainingFile(calcTreeElement());
  }

  @NotNull
  public abstract PsiElement[] getChildren();

  public PsiElement getFirstChild(){
    final PsiElement[] children = getChildren();
    return children.length == 0 ? null : children[0];
  }

  public PsiElement getLastChild(){
    final PsiElement[] children = getChildren();
    return children.length == 0 ? null : children[children.length - 1];
  }

  public TextRange getTextRange() {
    return new TextRange(0, getTextLength());
  }

  public int getStartOffsetInParent() {
    return 0;
  }

  public int getTextLength() {
    return getViewProvider().getContents().length();
  }

  public int getTextOffset() {
    return 0;
  }

  public boolean textMatches(PsiElement element) {
    return textMatches(element.getText());
  }

  public boolean textMatches(CharSequence text) {
    return text.equals(getViewProvider().getContents());
  }

  public boolean textContains(char c) {
    return getText().indexOf(c) >= 0;
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public final PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public final PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiReference getReference() {
    return null;
  }

  public ASTNode getNode() {
    return null;
  }
}
