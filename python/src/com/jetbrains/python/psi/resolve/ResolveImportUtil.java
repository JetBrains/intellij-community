package com.jetbrains.python.psi.resolve;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportResolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class ResolveImportUtil {

  /** Name of the __init__.py special file. */
  @NonNls public static final String INIT_PY = "__init__.py";
  @NonNls public static final String PY_SUFFIX = ".py";

  private ResolveImportUtil() {
  }

  private static final ThreadLocal<Set> myBeingImported = new ThreadLocal<Set>() {
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
  public static PsiElement resolveImportReference(final PyReferenceExpression importRef) {
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

  /*
   * Finds a named submodule file/dir under given root.
   */
  @Nullable
  private static PsiElement matchToFile(String name, PsiManager manager, VirtualFile root_file) {
    VirtualFile child_file = root_file.findChild(name);
    if (child_file != null) {
      if (name.equals(child_file.getName())) {
        VirtualFile initpy = child_file.findChild(INIT_PY);
        if (initpy != null) {
          PsiFile initfile = manager.findFile(initpy);
          if (initfile != null) {
            initfile.putCopyableUserData(PyFile.KEY_IS_DIRECTORY, Boolean.TRUE); // we really resolved to the dir
            return initfile;
          }
        }
      }
    }
    return null;
  }

  /**
   * Resolves either <tt>import foo</tt> or <tt>from foo import bar</tt>.
   * @param importRef refers to the name of the module being imported (the <tt>foo</tt>).
   * @param referencedName the name imported from the module (the <tt>bar</tt> in <tt>import from</tt>), or null (for just <tt>import foo</tt>).
   * @return element the name resolves to, or null.
   */
  @Nullable
  public static PsiElement resolvePythonImport2(final PyReferenceExpression importRef, final String referencedName) {
    if (! importRef.isValid()) return null; // we often catch a reparse while in a process of resolution
    final String the_name = referencedName != null? referencedName : importRef.getName();
    Set being_imported = myBeingImported.get();
    PsiFile containing_file = importRef.getContainingFile();
    PsiElement last_resolved = null;
    List<PyReferenceExpression> ref_path = PyResolveUtil.unwindQualifiers(importRef);
    // join path to form the FQN: (a, b, c) -> a.b.c.
    StringBuffer pathbuf = new StringBuffer();
    for (PyQualifiedExpression pathelt : ref_path) pathbuf.append(pathelt.getName()).append(".");
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
          last_resolved =  resolveChild(last_resolved, it.next().getName(), containing_file, true);
          if (last_resolved == null) return null; // anything in the chain unresolved means that the whole chain fails
        }
        if (referencedName != null) {
          return resolveChild(last_resolved, referencedName, containing_file, false);
        }
        else return last_resolved;
      }

      // non-qualified name
      if (referencedName != null) {
        return resolveChild(importRef.resolve(), referencedName, containing_file, false);
        // the importRef.resolve() does not recurse infinitely because we're asked to resolve referencedName, not importRef itself
      }
      // unqualified import can be found:
      // in the same dir
      PsiElement root_elt = resolveInRoots(importRef, the_name);
      if (root_elt != null) return root_elt;
    }
    finally {
      being_imported.remove(import_fqname); // unmark
    }
    return null; // not resolved by any means
  }


  public static void visitRoots(final PsiElement elt, @NotNull final SdkRootVisitor visitor) {
    // real search
    final Module module = ModuleUtil.findModuleForPsiElement(elt);
    if (module != null) {
      // TODO: implement a proper module-like approach in PyCharm for "project's dirs on pythonpath", minding proper search order
      // Module-based approach works only in the IDEA plugin.
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      // look in module sources
      boolean source_entries_missing = true;
      for (ContentEntry entry: rootManager.getContentEntries()) {
        VirtualFile root_file = entry.getFile();

        if (!visitor.visitRoot(root_file)) return;
        for (VirtualFile folder : entry.getSourceFolderFiles()) {
          source_entries_missing = false;
          if (!visitor.visitRoot(folder)) return;
        }
      }
      if (source_entries_missing) {
        // fallback for a case without any source entries: use project root
        VirtualFile project_root = module.getProject().getBaseDir();
        if (!visitor.visitRoot(project_root)) return;
      }
      // else look in SDK roots
      rootManager.processOrder(new SdkRootVisitingPolicy(visitor), null);
    }
    else {
      // no module, another way to look in SDK roots
      final PsiFile elt_psifile = elt.getContainingFile();
      if (elt_psifile != null) {  // formality
        final VirtualFile elt_vfile = elt_psifile.getVirtualFile();
        if (elt_vfile != null) { // reality
          for (OrderEntry entry: ProjectRootManager.getInstance(elt.getProject()).getFileIndex().getOrderEntriesForFile(elt_vfile)) {
            if (!visitGivenRoots(entry.getFiles(OrderRootType.SOURCES), visitor)) break;
          }
        }
      }
    }
  }


  private static boolean visitGivenRoots(final VirtualFile[] roots, SdkRootVisitor visitor) {
    for (VirtualFile root: roots) {
      if (! visitor.visitRoot(root)) return false;
    }
    return true;
  }

  // TODO: rewrite using visitRoots
  /**
   * Looks for a name among element's module's roots; if there's no module, then among project's roots.
   * @param elt PSI element that defines the module and/or the project.
   * @param refName module name to be found among roots.
   * @return a PsiFile, a child of a root.
   */
  @Nullable
  public static PsiElement resolveInRoots(@NotNull final PsiElement elt, final String refName) {
    // NOTE: a quick and ditry temporary fix for "current dir" root path, which is assumed to be present first (but may be not).
    PsiFile pfile = elt.getContainingFile();
    VirtualFile vfile = pfile.getVirtualFile();
    if (vfile == null) { // we're probably within a copy, e.g. for completion; get the real thing
      pfile = pfile.getOriginalFile();
    }
    if (pfile != null) {
      PsiDirectory pdir = pfile.getContainingDirectory();
      if (pdir != null) {
        PsiElement child_elt = resolveChild(pdir, refName, pfile, true);
        if (child_elt != null) return child_elt;
      }

    }
    // real search
    final Module module = ModuleUtil.findModuleForPsiElement(elt);
    if (module != null) {
      // TODO: implement a proper module-like approach in PyCharm for "project's dirs on pythonpath", minding proper search order
      // Module-based approach works only in the IDEA plugin.
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      // look in module sources
      boolean source_entries_missing = true;
      for (ContentEntry entry: rootManager.getContentEntries()) {
        VirtualFile root_file = entry.getFile();

        PsiElement ret = matchToFile(refName, elt.getManager(), root_file);
        if (ret != null) return ret;
        for (VirtualFile folder : entry.getSourceFolderFiles()) {
          source_entries_missing = false;
          ret = matchToFile(refName, elt.getManager(), folder);
          if (ret != null) return ret;
        }
      }
      if (source_entries_missing) {
        // fallback for a case without any source entries: use project root
        VirtualFile project_root = module.getProject().getBaseDir();
        PsiElement ret = matchToFile(refName, elt.getManager(), project_root);
        if (ret != null) return ret;
      }
      // else look in SDK roots
      LookupRootVisitor visitor = new LookupRootVisitor(refName, elt.getManager());
      rootManager.processOrder(new SdkRootVisitingPolicy(visitor), null);
      return visitor.getResult();
    }
    else {
      // no module, another way to look in SDK roots
      try {
        final PsiFile elt_psifile = elt.getContainingFile();
        if (elt_psifile != null) {  // formality
          final VirtualFile elt_vfile = elt_psifile.getVirtualFile();
          if (elt_vfile != null) { // reality
            for (OrderEntry entry: ProjectRootManager.getInstance(elt.getProject()).getFileIndex().getOrderEntriesForFile(elt_vfile
              )
            ) {
              PsiElement root_elt = resolveWithinRoots(entry.getFiles(OrderRootType.SOURCES), refName, elt.getProject());
              if (root_elt != null) return root_elt;
            }
          }
        }
      }
      catch (NullPointerException ex) { // NOTE: not beautiful
        return null; // any cut corners might result in an NPE; resolution fails, but not the IDE.
      }
    }
    return null; // nothing matched
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
  private static PsiElement resolveWithinRoots(final VirtualFile[] roots, final String referencedName, final Project project) {
    for(VirtualFile contentRoot: roots) {
      PsiElement result = resolveWithinRoot(contentRoot, referencedName, project);
      if (result != null) return result;
    }
    return null;
  }

  /**
  Tries to find referencedName under a root.
  @param root where to look for the referenced name.
  @param referencedName which name to look for.
  @param project Project to use.
  @return the element the referencedName resolves to, or null.
  */
  @Nullable
  private static PsiElement resolveWithinRoot(final VirtualFile root, final String referencedName, final Project project) {
    final PsiManager psi_mgr = PsiManager.getInstance(project);
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
      if (result != null) return false;
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
    Set<String> result;
    PsiManager psimgr;

    static String cutExt(String name) {
      return name.substring(0, Math.max(name.length() - PY_SUFFIX.length(), 0));
    }

    public CollectingRootVisitor(PsiManager psimgr) {
      result = new HashSet<String>();
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

    public Collection<String> getResult() {
      return result;
    }
  }

  /**
  Tries to find referencedName under the parent element. Used to resolve any names that look imported.
  Parent might happen to be a PyFile(__init__.py), then it is treated <i>both</i> as a file and as ist base dir.
  For details of this ugly magic, see {@link com.jetbrains.python.psi.impl.PyReferenceExpressionImpl#resolve()}.
  @param parent element under which to look for referenced name.
  @param referencedName which name to look for.
  @param containingFile where we're in.
  @param fileOnly if true, considers only a PsiFile child as a valid result; non-file hits are ignored.
  @return the element the referencedName resolves to, or null.
  @todo: Honor module's __all__ value.
  @todo: Honor package's __path__ value (hard).
  */
  @Nullable
  public static PsiElement resolveChild(final PsiElement parent, final String referencedName, final PsiFile containingFile, boolean fileOnly) {
    PsiDirectory dir = null;
    PsiElement ret = null;
    ResolveProcessor processor = null;
    if (parent instanceof PyFile) {
      boolean is_dir = (parent.getCopyableUserData(PyFile.KEY_IS_DIRECTORY) == Boolean.TRUE);
      PyFile pfparent = (PyFile)parent;
      if (! is_dir) {
        // look for name in the file:
        processor = new ResolveProcessor(referencedName);
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
    else if (parent instanceof PsiDirectoryContainer) {
      final PsiDirectoryContainer container = (PsiDirectoryContainer)parent;
      for(PsiDirectory childDir: container.getDirectories()) {
        final PsiElement result = resolveInDirectory(referencedName, containingFile, childDir, processor);
        if (fileOnly && ! (result instanceof PsiFile) && ! (result instanceof PsiDirectory)) return null;
        if (result != null) return result;
      }
    }
    if (dir != null) {
      final PsiElement result =  resolveInDirectory(referencedName, containingFile, dir, processor);
      if (fileOnly && ! (result instanceof PsiFile) && ! (result instanceof PsiDirectory)) return null;
      return result;
    }
    return ret;
  }

  @Nullable
  private static PsiElement resolveInDirectory(final String referencedName,
                                               final PsiFile containingFile,
                                               final PsiDirectory dir, ResolveProcessor processor) {
    final PsiFile file = dir.findFile(referencedName + PY_SUFFIX);
    if (file != null) return file;
    final PsiDirectory subdir = dir.findSubdirectory(referencedName);
    if (subdir != null) return subdir;
    else { // not a subdir, not a file; could be a name in parent/__init__.py
      final PsiFile initPy = dir.findFile(INIT_PY);
      if (initPy == containingFile) return null; // don't dive into the file we're in
      if (initPy != null) {
        if (processor == null) processor = new ResolveProcessor(referencedName); // should not normally happen
        return PyResolveUtil.treeCrawlUp(processor, true, initPy);//PyResolveUtil.treeWalkUp(processor, initPy, null, importRef);
      }
    }
    return null;
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
            final VariantsProcessor processor = new VariantsProcessor();
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
            if (a_file.isDirectory()) {
              if (a_file.findChild(INIT_PY) != null) {
                final String name = a_file.getName();
                if (PyUtil.isIdentifier(name)) variants.add(name);
              }
            }
            else { // plain file
              String fname = a_file.getName();
              if (fname.endsWith(PY_SUFFIX)) {
                final String name = fname.substring(0, fname.length() - PY_SUFFIX.length());
                if (PyUtil.isIdentifier(name)) variants.add(name);
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
      ModuleRootManager.getInstance(module).processOrder(new SdkRootVisitingPolicy(visitor), null);
      for (String name : visitor.getResult()) {
        if (PyUtil.isIdentifier(name)) variants.add(name); // to thwart stuff like "__phello__.foo"
      }
    }

    return variants.toArray(new Object[variants.size()]);
  }

  /**
   * Tries to find roots that contain given vfile, and among them the root that contains at the smallest depth.
   */
  private static class PathChoosingVisitor implements SdkRootVisitor {

    private final VirtualFile myFile;
    private String myFname;
    private String myResult = null;
    private int myDots = Integer.MAX_VALUE; // how many dots in the path

    private PathChoosingVisitor(VirtualFile file) {
      myFile = file;
      myFname = file.getPath();
      // cut off the ext
      int pos = myFname.lastIndexOf('.');
      if (pos > 0) myFname = myFname.substring(0, pos);
      // cut off the final __init__ if it's there; we want imports directly from a module
      pos = myFname.lastIndexOf(PyNames.INIT);
      if (pos > 0) myFname = myFname.substring(0, pos-1); // pos-1 also cuts the '/' that came before "__init__" 
    }

    public boolean visitRoot(VirtualFile root) {
      // does it ever fit?
      String root_name = root.getPath()+"/";
      if (myFname.startsWith(root_name)) {
        String bet = myFname.substring(root_name.length()).replace('/', '.'); // "/usr/share/python/foo/bar" -> "foo.bar"
        // count the dots
        int dots = 0;
        for (int i = 0; i < bet.length(); i += 1) if (bet.charAt(i) == '.') dots += 1;
        // a better variant?
        if (dots < myDots) {
          myDots = dots;
          myResult = bet;
        }
      }
      return true; // visit all roots
    }

    public String getResult() {
      return myResult;
    }
  }

  /**
   * Looks for a way to import given file.
   * @param foothold an element in the file to import to (maybe the file itself); used to determine module, roots, etc.
   * @param vfile file which importable name we want to find.
   * @return a possibly qualified name under which the file may be imported, or null. If there's more than one way (overlapping roots),
   * the name with fewest qualifiers is selected.
   */
  @Nullable
  public static String findShortestImportableName(PsiElement foothold, VirtualFile vfile) {
    PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
    visitRoots(foothold, visitor);
    return visitor.getResult();
  }


  private static class SdkRootVisitingPolicy extends RootPolicy<PsiElement> {
    private final SdkRootVisitor myVisitor;

    public SdkRootVisitingPolicy(SdkRootVisitor visitor) {
      myVisitor = visitor;
    }

    @Nullable
    public PsiElement visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final PsiElement value) {
      if (value != null) return value;  // for chaining in processOrder()
      visitGivenRoots(jdkOrderEntry.getRootFiles(OrderRootType.SOURCES), myVisitor);
      return null;
    }

    @Nullable
    @Override
    public PsiElement visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, PsiElement value) {
      if (value != null) return value;  // for chaining in processOrder()
      visitGivenRoots(libraryOrderEntry.getRootFiles(OrderRootType.SOURCES), myVisitor);
      return null;
    }
  }
}
