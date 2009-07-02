package org.jetbrains.idea.maven.dom.converters;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Key;
import com.intellij.lang.Language;
import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public class PsiElementWrapper implements PsiElement {
  private final PsiElement myWrappee;
  private final PsiElement myNavigationElement;

  public PsiElementWrapper(PsiElement wrappeeElement, PsiElement navigationElement) {
    myWrappee = wrappeeElement;
    myNavigationElement = navigationElement;
  }

  public PsiElement getWrappee() {
    return myWrappee;
  }

  @NotNull
  public Project getProject() throws PsiInvalidElementAccessException {
    return myWrappee.getProject();
  }

  @NotNull
  public Language getLanguage() {
    return myWrappee.getLanguage();
  }

  public PsiManager getManager() {
    return myWrappee.getManager();
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myWrappee.getChildren();
  }

  public PsiElement getParent() {
    return myWrappee.getParent();
  }

  @Nullable
  public PsiElement getFirstChild() {
    return myWrappee.getFirstChild();
  }

  @Nullable
  public PsiElement getLastChild() {
    return myWrappee.getLastChild();
  }

  @Nullable
  public PsiElement getNextSibling() {
    return myWrappee.getNextSibling();
  }

  @Nullable
  public PsiElement getPrevSibling() {
    return myWrappee.getPrevSibling();
  }

  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return myWrappee.getContainingFile();
  }

  public TextRange getTextRange() {
    return myWrappee.getTextRange();
  }

  public int getStartOffsetInParent() {
    return myWrappee.getStartOffsetInParent();
  }

  public int getTextLength() {
    return myWrappee.getTextLength();
  }

  @Nullable
  public PsiElement findElementAt(int offset) {
    return myWrappee.findElementAt(offset);
  }

  @Nullable
  public PsiReference findReferenceAt(int offset) {
    return myWrappee.findReferenceAt(offset);
  }

  public int getTextOffset() {
    return myWrappee.getTextOffset();
  }

  @NonNls
  public String getText() {
    return myWrappee.getText();
  }

  @NotNull
  public char[] textToCharArray() {
    return myWrappee.textToCharArray();
  }

  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  public PsiElement getOriginalElement() {
    return myWrappee.getOriginalElement();
  }

  public boolean textMatches(@NotNull @NonNls CharSequence text) {
    return myWrappee.textMatches(text);
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return myWrappee.textMatches(element);
  }

  public boolean textContains(char c) {
    return myWrappee.textContains(c);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    myWrappee.accept(visitor);
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    myWrappee.acceptChildren(visitor);
  }

  public PsiElement copy() {
    return myWrappee.copy();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return myWrappee.add(element);
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myWrappee.addBefore(element, anchor);
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myWrappee.addAfter(element, anchor);
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    myWrappee.checkAdd(element);
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return myWrappee.addRange(first, last);
  }

  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return myWrappee.addRangeBefore(first, last, anchor);
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return myWrappee.addRangeAfter(first, last, anchor);
  }

  public void delete() throws IncorrectOperationException {
    myWrappee.delete();
  }

  public void checkDelete() throws IncorrectOperationException {
    myWrappee.checkDelete();
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    myWrappee.deleteChildRange(first, last);
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return myWrappee.replace(newElement);
  }

  public boolean isValid() {
    return myWrappee.isValid();
  }

  public boolean isWritable() {
    return myWrappee.isWritable();
  }

  @Nullable
  public PsiReference getReference() {
    return myWrappee.getReference();
  }

  @NotNull
  public PsiReference[] getReferences() {
    return myWrappee.getReferences();
  }

  @Nullable
  public <T> T getCopyableUserData(Key<T> key) {
    return myWrappee.getCopyableUserData(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    myWrappee.putCopyableUserData(key, value);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return myWrappee.processDeclarations(processor, state, lastParent, place);
  }

  @Nullable
  public PsiElement getContext() {
    return myWrappee.getContext();
  }

  public boolean isPhysical() {
    return myWrappee.isPhysical();
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myWrappee.getResolveScope();
  }

  @NotNull
  public SearchScope getUseScope() {
    return myWrappee.getUseScope();
  }

  @Nullable
  public ASTNode getNode() {
    return myWrappee.getNode();
  }

  @NonNls
  public String toString() {
    return myWrappee.toString();
  }

  public boolean isEquivalentTo(PsiElement another) {
    return myWrappee.isEquivalentTo(another);
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    return myWrappee.getUserData(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myWrappee.putUserData(key, value);
  }

  public Icon getIcon(int flags) {
    return myWrappee.getIcon(flags);
  }
}
