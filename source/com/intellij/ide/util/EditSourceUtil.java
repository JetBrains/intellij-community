package com.intellij.ide.util;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;

public class EditSourceUtil {
  public static Navigatable getDescriptor(final PsiElement element) {
    if (!canNavigate(element)) {
      return null;
    }
    final PsiElement navigationElement = element.getNavigationElement();
    final int offset = navigationElement instanceof PsiFile ? -1 : navigationElement.getTextOffset();
    final VirtualFile virtualFile = PsiUtil.getVirtualFile(navigationElement);
    if (virtualFile == null || !virtualFile.isValid()) {
      return null;
    }
    return new OpenFileDescriptor(navigationElement.getProject(), virtualFile, offset);
  }

  public static boolean canNavigate (PsiElement element) {
    if (element == null || !element.isValid()) {
      return false;
    }
    final PsiElement navigationElement = element.getNavigationElement();
    final VirtualFile virtualFile = PsiUtil.getVirtualFile(navigationElement);
    return virtualFile != null && virtualFile.isValid();
  }
}