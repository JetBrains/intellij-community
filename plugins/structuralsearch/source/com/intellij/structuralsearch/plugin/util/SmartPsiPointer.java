package com.intellij.structuralsearch.plugin.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.lang.ref.WeakReference;

/**
 * Reference to element have been matched
 */
public class SmartPsiPointer {
  private VirtualFile file;
  private int offset;
  private int length;
  private Project project;
  private WeakReference reference;

  public SmartPsiPointer(PsiElement element) {
    if (element!=null &&
        element.getContainingFile()!=null
       ) {
      if (element.getManager()!=null) {
        project = element.getProject();
      }

      file = element.getContainingFile().getVirtualFile();
      offset = element.getTextRange().getStartOffset();
      length = element.getTextLength();
    }
    setElement(element);
  }

  public VirtualFile getFile() {
    return file;
  }

  public int getOffset() {
    return offset;
  }

  public int getLength() {
    return length;
  }

  public PsiElement getElement() {
    PsiElement el = (PsiElement)reference.get();

    if (el==null && file!=null && project!=null) {
      PsiFile psifile = PsiManager.getInstance(project).findFile(file);
      el = psifile.findElementAt(offset);

      if (el!=null && el.getTextLength() < length) {
        el = el.getParent();
      }
      if (el == null || el.getTextLength()!=length) {
        throw new RuntimeException("Problem restoring gc'ed element");
      }

      setElement(el);
    }

    return el;
  }

  private void setElement(final PsiElement _element) {
    reference = new WeakReference(_element);
  }

  public void clear() {
    reference.clear();
    reference = null;
    project = null;
    file = null;
  }

  public Project getProject() {
    return project;
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
    return getFile().hashCode() + offset + length;
  }
}
