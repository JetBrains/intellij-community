package com.intellij.structuralsearch.plugin.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;

/**
 * Reference to element have been matched
 */
public class SmartPsiPointer {
  private SmartPsiElementPointer pointer;

  public SmartPsiPointer(PsiElement element) {
    pointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  public VirtualFile getFile() {
    return pointer.getElement().getContainingFile().getVirtualFile();
  }

  public int getOffset() {
    return pointer.getElement().getTextRange().getStartOffset();
  }

  public int getLength() {
    return pointer.getElement().getTextRange().getEndOffset();
  }

  public PsiElement getElement() {
    return pointer.getElement();
  }

  public void clear() {
    pointer = null;
  }

  public Project getProject() {
    return pointer.getElement().getProject();
  }

  public boolean equals(Object o) {
    if (o instanceof SmartPsiPointer) {
      final SmartPsiPointer ref = ((SmartPsiPointer)o);
      return ref.getFile().equals(getFile()) &&
        ref.getOffset() == getOffset() &&
        ref.getLength() == getLength();
    }
    return false;
  }

  public int hashCode() {
    return getElement().hashCode();
  }
}
