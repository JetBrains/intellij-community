package com.intellij.ide.util;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class EditSourceUtil {
  public static Navigatable getDescriptor(final PsiElement element) {
    if (element == null) {
      return null;
    }
    if (!element.isValid()) {
      return null;
    }

    PsiElement navigationElement = element.getNavigationElement();

    PsiFile psiFile = navigationElement instanceof PsiFile ? (PsiFile)navigationElement : navigationElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }

    int offset = -1;
    if (!(navigationElement instanceof PsiFile)) {
      offset = navigationElement.getTextOffset();
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    return new OpenFileDescriptor(element.getProject(), virtualFile, offset);
  }
}