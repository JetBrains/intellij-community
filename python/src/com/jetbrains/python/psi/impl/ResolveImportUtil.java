package com.jetbrains.python.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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

    final PyExpression qualifier = importRef.getQualifier();
    if (qualifier instanceof PyReferenceExpression) {
      PsiElement qualifierElement = ((PyReferenceExpression) qualifier).resolve();
      if (qualifierElement == null) return null;
      return resolveChild(qualifierElement, referencedName, importRef);
    }

    if (importRef.getParent() instanceof PyImportElement) {
      PyImportElement parent = (PyImportElement) importRef.getParent();
      if (parent.getParent() instanceof PyFromImportStatement) {
        PyFromImportStatement stmt = (PyFromImportStatement) parent.getParent();
        final PyReferenceExpression source = stmt.getImportSource();
        if (source == null) return null;
        PsiElement sourceFile = resolveImportReference(source);
        return resolveChild(sourceFile, referencedName, importRef);
      }
    }
    final PsiFile[] files = FilenameIndex.getFilesByName(importRef.getProject(), referencedName + ".py",
                                                         GlobalSearchScope.allScope(importRef.getProject()));
    if (files.length == 1) return files[0];

    final Module module = ModuleUtil.findModuleForPsiElement(importRef);
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    for(VirtualFile root: contentRoots) {
      final VirtualFile childFile = root.findChild(referencedName + ".py");
      if (childFile != null) {
        return PsiManager.getInstance(importRef.getProject()).findFile(childFile);
      }

      final VirtualFile childDir = root.findChild(referencedName);
      if (childDir != null) {
        return PsiManager.getInstance(importRef.getProject()).findDirectory(childDir);
      }
    }

    return null;
  }

  @Nullable
  private static PsiElement resolveChild(final PsiElement parent, final String referencedName, final PyReferenceExpression importRef) {
    if (parent instanceof PyFile) {
      return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), parent, null, importRef);
    }
    else if (parent instanceof PsiDirectory) {
      return ((PsiDirectory)parent).findFile(referencedName + ".py");
    }
    else {
      return null;
    }
  }
}
