package com.intellij.psi.impl.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public abstract class LightElement extends PsiElementBase {
  protected final PsiManager myManager;

  protected LightElement(PsiManager manager) {
    myManager = manager;
  }

  public PsiManager getManager(){
    return myManager;
  }

  public PsiElement getParent(){
    return null;
  }

  public PsiElement[] getChildren(){
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiFile getContainingFile(){
    return null;
  }

  public TextRange getTextRange(){
    return null;
  }

  public int getStartOffsetInParent(){
    return -1;
  }

  public final int getTextLength(){
    String text = getText();
    return text != null ? text.length() : 0;
  }

  public char[] textToCharArray(){
    return getText().toCharArray();
  }

  public boolean textMatches(CharSequence text) {
    return getText().equals(text.toString());
  }

  public boolean textMatches(PsiElement element) {
    return getText().equals(element.getText());
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset(){
    return -1;
  }

  public boolean isValid(){
    return true;
  }

  public boolean isWritable(){
    return false;
  }

  public boolean isPhysical() {
    return false;
  }

  public abstract String toString();

  public void checkAdd(PsiElement element) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkAddBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkAddAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkDelete() throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkReplace(PsiElement newElement) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }
}
