package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ResolveImportUtil {
  private ResolveImportUtil() {
  }

  @Nullable
  static PsiElement resolveImportReference(final PyReferenceExpression importRef) {
    String referencedName = importRef.getReferencedName();
    if (referencedName == null) return null;
    if (importRef.getParent() instanceof PyImportElement) {
      PyImportElement parent = (PyImportElement) importRef.getParent();
      if (parent.getParent() instanceof PyFromImportStatement) {
        PyFromImportStatement stmt = (PyFromImportStatement) parent.getParent();
        final PyReferenceExpression source = stmt.getImportSource();
        if (source == null) return null;
        PsiElement sourceFile = resolveImportReference(source);
        if (sourceFile instanceof PyFile) {
          return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), sourceFile, null, importRef);
        }
      }
    }
    final PsiFile[] files = FilenameIndex.getFilesByName(importRef.getProject(), referencedName + ".py",
                                                         GlobalSearchScope.allScope(importRef.getProject()));
    if (files.length == 1) return files[0];
    return null;
  }
}
