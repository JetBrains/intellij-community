package com.intellij.ide.util;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class EditSourceUtil {
  public static Navigatable getDescriptor(final PsiElement element) {
    if (!canNavigate(element)) return null;
    final PsiElement navigationElement = element.getNavigationElement();
    final int offset = navigationElement instanceof PsiFile ? -1 : navigationElement.getTextOffset();
    return new OpenFileDescriptor(navigationElement.getProject(), navigationElement.getContainingFile().getVirtualFile(),
                                  offset);
  }

  public static boolean canNavigate (PsiElement element) {
    if (element == null || !element.isValid()) {
      return false;
    }

    PsiElement navigationElement = element.getNavigationElement();

    PsiFile psiFile = navigationElement.getContainingFile();
    if (psiFile == null) {
      return false;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }

    return true;
  }
}