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
  
  /** Name of the __init__.py special file. */
  public static final String INIT_PY = "__init__.py"; 
  public static final String PY_SUFFIX = ".py"; 
  
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

  /**
  Tries to find referencedName under a root. Only used for resolution of import statements.
  @param root where to look for the referenced name.
  @param referencedName which name to look for.
  @param importRef import reference which resolution led to this call.
  @return the element the referencedName resolves to, or null.
  */
  @Nullable
  private static PsiElement resolveInRoot(final VirtualFile root, final String referencedName, final PyReferenceExpression importRef) {
    final PsiManager psi_mgr = PsiManager.getInstance(importRef.getProject());
    final VirtualFile childFile = root.findChild(referencedName + PY_SUFFIX);
    if (childFile != null) {
      return psi_mgr.findFile(childFile);
    }

    final VirtualFile childDir = root.findChild(referencedName);
    if (childDir != null) {
      return psi_mgr.findDirectory(childDir);
    }

    // NOTE: a preliminary attempt to resolve to a C lib
    VirtualFile clib_file = root.findChild(referencedName + ".so"); // XXX: platform-dependent choice of .so | .pyd
    if (clib_file != null) {
      return psi_mgr.findFile(clib_file);
    }
    return null;
  }

  /**
  Tries to find referencedName under the parent element. Used to reesolve any names that look imported.
  Parent might happen to be a PyFile(__init__.py), then it is treated <i>both</i> as a file and as ist base dir.
  For details of this ugly magic, see {@link com.jetbrains.python.psi.impl.PyReferenceExpressionImpl#resolve()}.
  @param parent element under which to look for referenced name.
  @param referencedName which name to look for.
  @param importRef import reference which resolution led to this call.
  @return the element the referencedName resolves to, or null.
  @todo: Honor module's __all__ value.
  @todo: Honor package's __path__ value (hard).
  */
  @Nullable
  public static PsiElement resolveChild(final PsiElement parent, final String referencedName, final PyReferenceExpression importRef) {
    PsiDirectory dir = null;
    PsiElement ret = null;
    if (parent instanceof PyFile) {
      PyFile pfparent = (PyFile)parent; 
      if (INIT_PY.equals(pfparent.getName())) {
        // try both file and dir, for we can't tell.
        dir = pfparent.getContainingDirectory();
      }
      // to be used if dir resolution is not applicable:
      ret = PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), parent, null, importRef);
    }
    else if (parent instanceof PsiDirectory) {
      dir = (PsiDirectory)parent;
    }
    if (dir != null) {
      final PsiFile file = dir.findFile(referencedName + PY_SUFFIX);
      if (file != null) return file;
      final PsiDirectory subdir = dir.findSubdirectory(referencedName);
      if (subdir != null) return subdir;
      else { // not a subdir, not a file; could be a name in parent/__init__.py
        final PsiFile initPy = dir.findFile(INIT_PY);
        if (initPy != null) {
          return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(referencedName), initPy, null, importRef);
        }
      }
    }
    return ret;
  }
}
