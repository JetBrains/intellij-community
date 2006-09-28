package com.intellij.psi.impl.compiled;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

abstract class ClsElementImpl extends PsiElementBase implements PsiCompiledElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsElementImpl");

  protected volatile TreeElement myMirror = null;

  @NotNull
  public Language getLanguage() {
    return StdFileTypes.JAVA.getLanguage();
  }

  public PsiManager getManager() {
    return getParent().getManager();
  }

  public PsiFile getContainingFile() {
    if (!isValid()) throw new PsiInvalidElementAccessException(this);
    return getParent().getContainingFile();
  }

  public final boolean isWritable() {
    return false;
  }

  public boolean isPhysical() {
    return true;
  }

  public boolean isValid() {
    PsiElement parent = getParent();
    return parent != null && parent.isValid();
  }

  public PsiElement copy() {
    return this;
  }

  public void checkAdd(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  protected static final String CAN_NOT_MODIFY_MESSAGE = PsiBundle.message("psi.error.attempt.to.edit.class.file");

  public abstract void appendMirrorText(final int indentLevel, final StringBuffer buffer);

  protected static void goNextLine(int indentLevel, StringBuffer buffer) {
    buffer.append('\n');
    for (int i = 0; i < indentLevel; i++) buffer.append(' ');
  }

  protected int getIndentSize() {
    return CodeStyleSettingsManager.getSettings(getProject()).getIndentSize(StdFileTypes.JAVA);
  }

  public abstract void setMirror(TreeElement element);

  public final PsiElement getMirror() {
    synchronized (PsiLock.LOCK) {
      if (myMirror == null) {
        getContainingFile().getText(); // to initialize mirror
      }
    }
    return SourceTreeToPsiMap.treeElementToPsi(myMirror);
  }

  public final TextRange getTextRange() {
    getMirror();
    return myMirror != null ? myMirror.getTextRange() : new TextRange(0, 0);
  }

  public final int getStartOffsetInParent() {
    getMirror();
    return myMirror != null ? myMirror.getStartOffsetInParent() : -1;
  }

  public int getTextLength() {
    String text = getText();
    if (text == null){
      LOG.error("getText() == null, element = " + this + ", parent = " + getParent());
      return 0;
    }
    return text.length();
  }

  public PsiElement findElementAt(int offset) {
    PsiElement mirrorAt = getMirror().findElementAt(offset);
    while(true){
      if (mirrorAt == null) return null;
      PsiElement elementAt = mirrorToElement(mirrorAt);
      if (elementAt != null) return elementAt;
      mirrorAt = mirrorAt.getParent();
    }

    /*
    PsiElement[] children = getChildren();
    if (children.length == 0) return this;
    for(int i = 0; i < children.length; i++){
      int start = children[i].getStartOffsetInParent();
      if (offset < start) return null;
      int end = start + children[i].getTextLength();
      if (offset < end){
        return children[i].findElementAt(offset - start);
      }
    }
    return null;
    */
  }

  public PsiReference findReferenceAt(int offset) {
    PsiReference mirrorRef = getMirror().findReferenceAt(offset);
    if (mirrorRef == null) return null;
    PsiElement mirrorElement = mirrorRef.getElement();
    PsiElement element = mirrorToElement(mirrorElement);
    if (element == null) return null;
    return element.getReference();
  }

  private PsiElement mirrorToElement(PsiElement mirror) {
    getMirror();
    if (myMirror == mirror) return this;

    PsiElement[] children = getChildren();
    if (children.length == 0) return null;

    for (PsiElement child : children) {
      ClsElementImpl clsChild = ((ClsElementImpl) child);
      if (PsiTreeUtil.isAncestor(clsChild.getMirror(), mirror, false)) {
        PsiElement element = clsChild.mirrorToElement(mirror);
        if (element != null) return element;
      }
    }

    return null;
  }

  public final int getTextOffset() {
    getMirror();
    return myMirror != null ? myMirror.getTextOffset() : -1;
  }

  public String getText() {
    getMirror();
    return myMirror != null ? myMirror.getText() : null;
  }

  @NotNull
  public char[] textToCharArray() {
    return getText().toCharArray();
  }

  public boolean textMatches(CharSequence text) {
    return getText().equals(text.toString());
  }

  public boolean textMatches(PsiElement element) {
    return getText().equals(element.getText());
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public ASTNode getNode() {
    return null;
  }
}
