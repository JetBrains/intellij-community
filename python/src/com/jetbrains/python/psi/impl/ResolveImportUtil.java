package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
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
    /*
    True resolve order is:
    - local modules,
    - builtins? (check),
    - modules from sys.path (aka SdkOrderEntries).
    (http://docs.python.org/ref/import.html)
    */
    // TODO: assume some things like sys to be only from __builtins__
    // TODO: rewrite entirely imitating Python import process: global module table, under-initialisation, etc.

    // qualified imports resolve their children    
    final PyExpression qualifier = importRef.getQualifier();
    if (qualifier instanceof PyReferenceExpression) {
      PsiElement qualifierElement = ((PyReferenceExpression) qualifier).resolve();
      if (qualifierElement == null) return null;
      return resolveChild(qualifierElement, referencedName, importRef);
    }

    if (importFrom != null) {
      return resolveChild(importFrom, referencedName, importRef);
    }
    
    // unqualified import can be found:
    // in the same dir
    final PsiFile pfile = importRef.getContainingFile();
    if (pfile != null) {
      PsiDirectory pdir = pfile.getContainingDirectory();
      if (pdir != null) {
        PsiElement elt = resolveChild(pdir, referencedName, importRef);
        if (elt != null) return elt;
      }
      
    } 
    
    // .. or in SDK roots
    final Module module = ModuleUtil.findModuleForPsiElement(importRef);
    if (module != null) {
      RootPolicy<PsiElement> resolvePolicy = new RootPolicy<PsiElement>() {
        /*         
        public PsiElement visitModuleSourceOrderEntry(final ModuleSourceOrderEntry moduleOrderEntry, final PsiElement value) {
          if (value != null) return value;
          return resolveInRoots(moduleOrderEntry.getRootModel().getContentRoots(), referencedName, importRef);
        }
        */
        public PsiElement visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final PsiElement value) {
          if (value != null) return value;
          return resolveInRoots(jdkOrderEntry.getRootFiles(OrderRootType.SOURCES), referencedName, importRef);
        }
      };
      return ModuleRootManager.getInstance(module).processOrder(resolvePolicy, null);
    }
    else {
      try {
        for (OrderEntry entry: ProjectRootManager.getInstance(importRef.getProject()).getFileIndex().getOrderEntriesForFile(
              importRef.getContainingFile().getVirtualFile()
          )
        ) {
          PsiElement elt = resolveInRoots(entry.getFiles(OrderRootType.CLASSES), referencedName, importRef);
          if (elt != null) return elt;
        }
      }
      catch (NullPointerException ex) {
        return null;
      }
    }
    return null; // normally unreachable
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
    PyResolveUtil.ResolveProcessor processor = null;
    if (parent instanceof PyFile) {
      boolean is_dir = (parent.getCopyableUserData(PyFile.KEY_IS_DIRECTORY) == Boolean.TRUE);
      PyFile pfparent = (PyFile)parent; 
      if (! is_dir) {
        /*
        if (INIT_PY.equals(pfparent.getName())) {
          // try both file and dir, for we can't tell.
          dir = pfparent.getContainingDirectory();
        }
        */
        // look for name in the file:
        processor = new PyResolveUtil.ResolveProcessor(referencedName);
        ret = PyResolveUtil.treeWalkUp(processor, parent, null, importRef);
        if (ret != null) return ret;
      }
      else { // the file was a fake __init__.py covering a reference to dir
        dir = pfparent.getContainingDirectory();
      }
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
        if ((importRef != null) && (initPy == importRef.getContainingFile())) return ret; // don't dive into the file we're in
        if (initPy != null) {
          if (processor == null) processor = new PyResolveUtil.ResolveProcessor(referencedName); // should not normally happen 
          return PyResolveUtil.treeWalkUp(processor, initPy, null, importRef);
        }
      }
    }
    return ret;
  }
}
