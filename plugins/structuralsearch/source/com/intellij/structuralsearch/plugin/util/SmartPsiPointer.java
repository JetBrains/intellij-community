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
    pointer = element != null ? SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element):null;
  }

  public VirtualFile getFile() {
    return pointer != null ? pointer.getElement().getContainingFile().getVirtualFile():null;
  }

  public int getOffset() {
    return pointer != null ? pointer.getElement().getTextRange().getStartOffset():-1;
  }

  public int getLength() {
    return pointer != null ? pointer.getElement().getTextRange().getEndOffset():0;
  }

  public PsiElement getElement() {
    return pointer != null ? pointer.getElement():null;
  }

  public void clear() {
    pointer = null;
  }

  public Project getProject() {
    return pointer != null ? pointer.getElement().getProject():null;
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
    return pointer != null ? getElement().hashCode():0;
  }
}
