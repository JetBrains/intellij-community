package com.jetbrains.python.psi.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class ResolveImportUtil {
  
  /** Name of the __init__.py special file. */
  @NonNls public static final String INIT_PY = "__init__.py";
  @NonNls public static final String PY_SUFFIX = ".py"; 
  
  private ResolveImportUtil() {
  }

  static ThreadLocal<Set> myBeingImported = new ThreadLocal<Set>() {
    @Override protected Set initialValue() {
      return new HashSet();
    }
  };



  /**
   * Resolves a reference in an import statement into whatever object it refers to.
   * @param importRef a reference within an import element.
   * @return the object importRef refers to, or null. 
   */
  @Nullable
  static PsiElement resolveImportReference(final PyReferenceExpression importRef) {
    if (importRef == null) return null; // fail fast
    final String referencedName = importRef.getReferencedName();
    if (referencedName == null) return null;

    PyReferenceExpression source = null;
    if (importRef.getParent() instanceof PyImportElement) {
      PyImportElement parent = (PyImportElement) importRef.getParent();
      if (parent.getParent() instanceof PyFromImportStatement) {
        PyFromImportStatement stmt = (PyFromImportStatement) parent.getParent();
        source =  stmt.getImportSource();
        if (source == null) return null;
      }
    }

    PsiElement result;
    if (source != null) {
      result = resolvePythonImport2(source, referencedName);
    }
    else result = resolvePythonImport2(importRef, null);
    if (result != null) {
      return result;
    }
    return resolveForeignImport(importRef, resolveImportReference(source));
  }

  /**
   * Resolves either <tt>import foo</tt> or <tt>from foo import bar</tt>.
   * @param importRef refers to the name of the module being imported (the <tt>foo</tt>).
   * @param referencedName the name imported from the module (the <tt>bar</tt> in <tt>import from</tt>), or null (for just <tt>import foo</tt>).
   * @return element the name resolves to, or null.
   */
  @Nullable
  public static PsiElement resolvePythonImport2(final PyReferenceExpression importRef, final String referencedName) {
    final String the_name = referencedName != null? referencedName : importRef.getName();
    Set being_imported = myBeingImported.get();
    PsiFile containing_file = importRef.getContainingFile();
    PsiElement last_resolved = null;
    List<PyReferenceExpression> ref_path = PyResolveUtil.unwindRefPath(importRef);
    // join path to form the FQN: (a, b, c) -> a.b.c.
    StringBuffer pathbuf = new StringBuffer();
    for (PyReferenceExpression pathelt : ref_path) pathbuf.append(pathelt.getName()).append(".");
    if (referencedName != null) pathbuf.append(referencedName);
    final String import_fqname = pathbuf.toString();
    if (being_imported.contains(import_fqname)) return null; // already trying this path, unable to recurse
    try {
      being_imported.add(import_fqname); // mark
      // resolve qualifiers
      Iterator<PyReferenceExpression> it = ref_path.iterator();
      if (ref_path.size() > 1) { // it was a qualified name
        if (it.hasNext()) {
          last_resolved = it.next().resolve(); // our topmost qualifier, not ourselves for certain
        }
        else return null; // topmost qualifier not found
        while (it.hasNext()) {
          last_resolved =  resolveChild(last_resolved, it.next().getName(), containing_file);
          if (last_resolved == null) return null; // anything in the chain unresolved means that the whole chain fails
        }
        if (referencedName != null) {
          return resolveChild(last_resolved, referencedName, containing_file);
        }
        else return last_resolved;
      }

      // non-qualified name
      if (referencedName != null) {
        return resolveChild(importRef.resolve(), referencedName, containing_file);
        // the importRef.resolve() does not recurse infinitely because we're asked to resolve referencedName, not importRef itself
      }
      // unqualified import can be found:
      // in the same dir
      final PsiFile pfile = importRef.getContainingFile();
      if (pfile != null) {
        PsiDirectory pdir = pfile.getContainingDirectory();
        if (pdir != null) {
          PsiElement elt = resolveChild(pdir, the_name, containing_file);
          if (elt != null) return elt;
        }

      }

      // .. or in SDK roots
      final Module module = ModuleUtil.findModuleForPsiElement(importRef);
      if (module != null) {
        RootPolicy<PsiElement> resolvePolicy = new RootPolicy<PsiElement>() {
          @Nullable
          public PsiElement visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final PsiElement value) {
            if (value != null) return value;
            LookupRootVisitor visitor = new LookupRootVisitor(the_name, importRef.getManager());
            visitRoots(jdkOrderEntry.getRootFiles(OrderRootType.SOURCES), visitor);
            return visitor.getResult();
            /*return resolveInRoots(jdkOrderEntry.getRootFiles(OrderRootType.SOURCES), the_name, importRef);*/
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
            PsiElement elt = resolveInRoots(entry.getFiles(OrderRootType.CLASSES), the_name, importRef);
            if (elt != null) return elt;
          }
        }
        catch (NullPointerException ex) {
          return null; // any cut corners might result in an NPE; resolution fails, but not the IDE.
        }
      }
    }
    finally {
      being_imported.remove(import_fqname); // unmark
    }
    return null; // not resolved by any means
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

  public static void visitRoots(final VirtualFile[] roots, SdkRootVisitor visitor) {
    for (VirtualFile root: roots) {
      if (! visitor.visitRoot(root)) break;
    }
  }

  /**
  Tries to find referencedName under a root.
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

    return null;
  }


  interface SdkRootVisitor {
    /**
     * @param root what we're visiting.
     * @return false when visiting must stop.
     */
    boolean visitRoot(VirtualFile root);
  }

  static class LookupRootVisitor implements SdkRootVisitor {
    String name;
    PsiManager psimgr;
    PsiElement result;

    public LookupRootVisitor(String name, PsiManager psimgr) {
      this.name = name;
      this.psimgr = psimgr;
      this.result = null;
    }

    public boolean visitRoot(final VirtualFile root) {
      final VirtualFile childFile = root.findChild(name + PY_SUFFIX);
      if (childFile != null) {
        result = psimgr.findFile(childFile);
        return (result == null);
      }

      final VirtualFile childDir = root.findChild(name);
      if (childDir != null) {
        result = psimgr.findDirectory(childDir);
        return (result == null);
      }
      return true;
    }

    public PsiElement getResult() {
      return result;
    }
  }

  static class CollectingRootVisitor implements SdkRootVisitor {
    List<String> result;
    PsiManager psimgr;

    static String cutExt(String name) {
      return name.substring(0, Math.max(name.length() - PY_SUFFIX.length(), 0));
    }

    public CollectingRootVisitor(PsiManager psimgr) {
      result = new ArrayList<String>(25);
      this.psimgr = psimgr;
    }

    public boolean visitRoot(final VirtualFile root) {
      for (VirtualFile vfile : root.getChildren()) {
        if (vfile.getName().endsWith(PY_SUFFIX)) {
          PsiFile pfile = psimgr.findFile(vfile);
          if (pfile != null) result.add(cutExt(pfile.getName()));
        }
        else if (vfile.isDirectory() && (vfile.findChild(INIT_PY) != null)) {
          PsiDirectory pdir = psimgr.findDirectory(vfile);
          if (pdir != null) result.add(pdir.getName());
        }
      }
      return true; // continue forever
    }

    public List<String> getResult() {
      return result;
    }
  }

  /**
  Tries to find referencedName under the parent element. Used to resolve any names that look imported.
  Parent might happen to be a PyFile(__init__.py), then it is treated <i>both</i> as a file and as ist base dir.
  For details of this ugly magic, see {@link com.jetbrains.python.psi.impl.PyReferenceExpressionImpl#resolve()}.
   @param parent element under which to look for referenced name.
    * @param referencedName which name to look for.
   * @param containingFile
  @return the element the referencedName resolves to, or null.
  @todo: Honor module's __all__ value.
  @todo: Honor package's __path__ value (hard).
  */
  @Nullable
  public static PsiElement resolveChild(final PsiElement parent, final String referencedName, final PsiFile containingFile) {
    PsiDirectory dir = null;
    PsiElement ret = null;
    PyResolveUtil.ResolveProcessor processor = null;
    if (parent instanceof PyFile) {
      boolean is_dir = (parent.getCopyableUserData(PyFile.KEY_IS_DIRECTORY) == Boolean.TRUE);
      PyFile pfparent = (PyFile)parent; 
      if (! is_dir) {
        // look for name in the file:
        processor = new PyResolveUtil.ResolveProcessor(referencedName);
        //ret = PyResolveUtil.treeWalkUp(processor, parent, null, importRef);
        ret = PyResolveUtil.treeCrawlUp(processor, true, parent);
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
      else { // XXX faulty: not a subdir, not a file; could be a name in parent/__init__.py
        final PsiFile initPy = dir.findFile(INIT_PY);
        if (initPy == containingFile) return ret; // don't dive into the file we're in
        if (initPy != null) {
          if (processor == null) processor = new PyResolveUtil.ResolveProcessor(referencedName); // should not normally happen 
          return PyResolveUtil.treeCrawlUp(processor, true, initPy);//PyResolveUtil.treeWalkUp(processor, initPy, null, importRef);
        }
      }
    }
    return ret;
  }


  /**
   * Finds reasonable names to import to complete a patrial name.
   * @param partial_ref reference containing the partial name.
   * @return an array of names ready for getVariants().
   */
  public static Object[] suggestImportVariants(final PyReferenceExpression partial_ref) {
    List<Object> variants = new ArrayList<Object>();
    // are we in "import _" or "from foo import _"?
    PyFromImportStatement maybe_from_import = PsiTreeUtil.getParentOfType(partial_ref, PyFromImportStatement.class);
    if (maybe_from_import != null) {
      if (partial_ref.getParent() != maybe_from_import) { // in "from foo import _"
        PyReferenceExpression src = maybe_from_import.getImportSource();
        if (src != null) {
          PsiElement mod = src.resolve();
          if (mod != null) {
            final PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor();
            PyResolveUtil.treeCrawlUp(processor, true, mod);
            /*
            for (LookupElement le : processor.getResult()) {
              if (le.getObject() instanceof PsiNamedElement) variants.add(le);
              else variants.add(le.toString()); // NOTE: a rather silly way to handle assignment targets
            }
            */
            return processor.getResult();
          }
        }
      }
    }
    // in "import _" or "from _ import"
    // look in builtins
    DataContext dataContext = DataManager.getInstance().getDataContext();
    // look at current dir
    final VirtualFile pfile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (pfile != null) {
      VirtualFile pdir = pfile.getParent();
      if (pdir != null) {
        for (VirtualFile a_file : pdir.getChildren()) {
          if (a_file != pfile) {
            if (pfile.isDirectory()) {
              if (pfile.findChild(INIT_PY) != null) variants.add(a_file.getName());
            }
            else { // plain file
              String fname = a_file.getName();
              if (fname.endsWith(PY_SUFFIX)) {
                variants.add(fname.substring(0, fname.length() - PY_SUFFIX.length()));
              }
            }
          }
        }
      }
    }
    // look in SDK
    final CollectingRootVisitor visitor = new CollectingRootVisitor(partial_ref.getManager());
    final Module module = ModuleUtil.findModuleForPsiElement(partial_ref);
    if (module != null) {
      RootPolicy<PsiElement> resolvePolicy = new RootPolicy<PsiElement>() {
        @Nullable
        public PsiElement visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final PsiElement value) {
          if (value != null) return value;
          visitRoots(jdkOrderEntry.getRootFiles(OrderRootType.SOURCES), visitor);
          return null;
        }
      };
      ModuleRootManager.getInstance(module).processOrder(resolvePolicy, null);
      variants.addAll(visitor.getResult());
    }

    return variants.toArray(new String[variants.size()]);
  }

}
