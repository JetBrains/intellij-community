package com.jetbrains.python.psi.impl;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyElement;

/**
 * @author yole
 */
public class ResolveImportUtil {
  private ResolveImportUtil() {
  }

  @Nullable
  static PsiElement resolveImportReference(final PyElement context, final String referencedName) {
    final PsiFile[] files = FilenameIndex.getFilesByName(context.getProject(), referencedName + ".py",
                                                         GlobalSearchScope.allScope(context.getProject()));
    if (files.length == 1) return files[0];
    return null;
  }
}
