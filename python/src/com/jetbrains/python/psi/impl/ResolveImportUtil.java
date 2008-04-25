package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.*;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
    final String referencedName = importRef.getReferencedName();
    if (referencedName == null) return null;

    PsiElement importFrom = null;

    if (importRef.getParent() instanceof PyImportElement) {
      PyImportElement parent = (PyImportElement) importRef.getParent();
      if (parent.getParent() instanceof PyFromImportStatement) {
        PyFromImportStatement stmt = (PyFromImportStatement) parent.getParent();
        final PyReferenceExpression source = stmt.getImportSource();
        if (source == null) return null;
        importFrom = resolveImportReference(source);
      }
    }

    PsiElement result = resolvePythonImport(importRef, importFrom, referencedName);
    if (result != null) {
      return result;
    }
    return resolveForeignImport(importRef, importFrom);
  }

  @Nullable
  private static PsiElement resolvePythonImport(final PyReferenceExpression importRef, final PsiElement importFrom,
                                                final String referencedName) {
    final PyExpression qualifier = importRef.getQualifier();
    if (qualifier instanceof PyReferenceExpression) {
      PsiElement qualifierElement = ((PyReferenceExpression) qualifier).resolve();
      if (qualifierElement == null) return null;
      return resolveChild(qualifierElement, referencedName, importRef);
    }

    if (importFrom != null) {
      return resolveChild(importFrom, referencedName, importRef);
    }

    final Module module = ModuleUtil.findModuleForPsiElement(importRef);
    if (module == null) return null;

    RootPolicy<PsiElement> resolvePolicy = new RootPolicy<PsiElement>() {
      public PsiElement visitModuleSourceOrderEntry(final ModuleSourceOrderEntry moduleOrderEntry, final PsiElement value) {
        if (value != null) return value;
        return resolveInRoots(moduleOrderEntry.getRootModel().getContentRoots(), referencedName, importRef);
      }

      public PsiElement visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final PsiElement value) {
        if (value != null) return value;
        return resolveInRoots(jdkOrderEntry.getRootFiles(OrderRootType.SOURCES), referencedName, importRef);
      }
    };
    return ModuleRootManager.getInstance(module).processOrder(resolvePolicy, null);
  }

  @Nullable
  private static PsiElement resolveForeignImport(final PyReferenceExpression importRef, final PsiElement importFrom) {
    for(PyImportResolver resolver: Extensions.getExtensions(PyImportResolver.EP_NAME)) {
      PsiElement result = resolver.resolveImportReference(importRef, importFrom);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveInRoots(final VirtualFile[] roots, final String referencedName, final PyReferenceExpression importRef) {
    for(VirtualFile contentRoot: roots) {
      PsiElement result = resolveInRoot(contentRoot, referencedName, importRef);
      if (result != null) return result;
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveInRoot(final VirtualFile root, final String referencedName, final PyReferenceExpression importRef) {
    final PsiManager psi_mgr = PsiManager.getInstance(importRef.getProject());
    final VirtualFile childFile = root.findChild(referencedName + ".py");
    if (childFile != null) {
      return psi_mgr.findFile(childFile);
    }

    final VirtualFile childDir = root.findChild(referencedName);
    if (childDir != null) {
      return psi_mgr.findDirectory(childDir);
    }

    // NOTE: a preliminary attempt to resolve to a C lib
    VirtualFile clib_file = root.findChild(referencedName + ".so"); // TODO: platform-dependent choice of .so | .pyd
    if (clib_file != null) {
      return psi_mgr.findFile(clib_file);
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveChild(final PsiElement parent, final String referencedName, final PyReferenceExpression importRef) {
    if (parent instanceof PyFile) {
      return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), parent, null, importRef);
    }
    else if (parent instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)parent;
      final PsiFile file = dir.findFile(referencedName + ".py");
      if (file != null) return file;
      final PsiDirectory subdir = dir.findSubdirectory(referencedName);
      if (subdir != null) return subdir;
      final PsiFile initPy = dir.findFile("__init__.py");
      if (initPy != null) {
        return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), initPy, null, importRef);
      }
    }
    return null;
  }
}
