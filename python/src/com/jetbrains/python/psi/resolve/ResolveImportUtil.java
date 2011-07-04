package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.django.facet.DjangoFacetType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.FutureFeature.ABSOLUTE_IMPORT;

/**
 * @author dcheryasov
 */
public class ResolveImportUtil {
  private ResolveImportUtil() {
  }

  private static final ThreadLocal<Set<String>> ourBeingImported = new ThreadLocal<Set<String>>() {
    @Override
    protected Set<String> initialValue() {
      return new HashSet<String>();
    }
  };

  public static boolean isAbsoluteImportEnabledFor(PsiElement foothold) {
    if (foothold != null) {
      PsiFile file = foothold.getContainingFile();
      if (file instanceof PyFile) {
        final PyFile pyFile = (PyFile)file;
        if (pyFile.getLanguageLevel().isPy3K()) {
          return true;
        }
        return pyFile.hasImportFromFuture(ABSOLUTE_IMPORT);
      }
    }
    // if the relevant import is below the foothold, it is either legal or we've detected the offending statement already
    return false;
  }


  /**
   * Finds a directory that many levels above a given file, making sure that every level has an __init__.py.
   *
   * @param base  file that works as a reference.
   * @param depth must be positive, 1 means the dir that contains base, 2 is one dir above, etc.
   * @return found directory, or null.
   */
  @Nullable
  public static PsiDirectory stepBackFrom(PsiFile base, int depth) {
    if (depth == 0) {
      return base.getContainingDirectory();
    }
    PsiDirectory result;
    if (base != null) {
      base = base.getOriginalFile(); // just to make sure 
      result = base.getContainingDirectory();
      int count = 1;
      while (result != null && result.findFile(PyNames.INIT_DOT_PY) != null) {
        if (count >= depth) return result;
        result = result.getParentDirectory();
        count += 1;
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement resolveImportElement(PyImportElement import_element) {
    return resolveImportElement(import_element, import_element.getImportedQName());
  }

  @Nullable
  public static PsiElement resolveImportElement(PyImportElement import_element, final PyQualifiedName qName) {
    final List<PsiElement> psiElements = multiResolveImportElement(import_element, qName);
    if (psiElements.size() > 1) {
      // prefer the directory which has a non-empty __init__.py
      for (PsiElement element : psiElements) {
        final PsiElement init = PyUtil.turnDirIntoInit(element);
        if (init instanceof PsiFile) {
          VirtualFile vFile = ((PsiFile)init).getVirtualFile();
          if (vFile != null && vFile.getLength() > 0) {
            return element;
          }
        }
      }
    }
    return psiElements.isEmpty() ? null : psiElements.get(0);
  }

  @NotNull
  public static List<PsiElement> multiResolveImportElement(PyImportElement import_element, final PyQualifiedName qName) {
    if (qName == null) return Collections.emptyList();

    // TODO: search for entire names, not for first component only!
    final String first_component = qName.getComponents().get(0);

    final PsiFile file = import_element.getContainingFile().getOriginalFile();
    final PyStatement importStatement = import_element.getContainingImportStatement();

    boolean absolute_import_enabled = isAbsoluteImportEnabledFor(import_element);
    PyQualifiedName moduleQName = null;

    if (importStatement instanceof PyFromImportStatement) {
      PyFromImportStatement from_import_statement = (PyFromImportStatement)importStatement;
      moduleQName = from_import_statement.getImportSourceQName();
      final int relative_level = from_import_statement.getRelativeLevel();

      if (relative_level > 0 && moduleQName == null) { // "from ... import foo"
        final PsiElement element = resolveChild(stepBackFrom(file, relative_level), first_component, file, null, false, true);
        return element != null ? Collections.singletonList(element) : Collections.<PsiElement>emptyList();
      }

      if (moduleQName != null) { // either "from bar import foo" or "from ...bar import foo"
        final List<PsiElement> candidates = resolveModule(moduleQName, file, absolute_import_enabled, relative_level);
        List<PsiElement> resultList = new ArrayList<PsiElement>();
        for (PsiElement candidate : candidates) {
          PsiElement result = resolveChild(PyUtil.turnDirIntoInit(candidate), first_component, file, null, false, true);
          if (result != null) {
            resultList.add(result);
          }
        }
        if (!resultList.isEmpty()) {
          return resultList;
        }
      }
    }
    else if (importStatement instanceof PyImportStatement) { // "import foo"
      final List<PsiElement> result = resolveModule(qName, file, absolute_import_enabled, 0);
      if (result.size() > 0) {
        return result;
      }
    }
    // in-python resolution failed
    final PsiElement result = resolveForeignImport(import_element, qName, moduleQName);
    return result != null ? Collections.singletonList(result) : Collections.<PsiElement>emptyList();
  }

  @NotNull
  public static List<PsiElement> resolveImportReference(final PyReferenceExpression importRef) {
    // prerequisites
    if (importRef == null) return Collections.emptyList();
    if (!importRef.isValid()) return Collections.emptyList(); // we often catch a reparse while in a process of resolution
    final String referencedName = importRef.getReferencedName(); // it will be the "foo" in later comments
    if (referencedName == null) return Collections.emptyList();
    final PsiFile file = importRef.getContainingFile();
    if (file == null || !file.isValid()) return Collections.emptyList();

    final PsiElement parent =
      PsiTreeUtil.getParentOfType(importRef, PyImportElement.class, PyFromImportStatement.class); //importRef.getParent();
    if (parent instanceof PyImportElement) {
      PyImportElement import_element = (PyImportElement)parent;
      return multiResolveImportElement(import_element, importRef.asQualifiedName());
    }
    else if (parent instanceof PyFromImportStatement) { // "from foo import"
      PyFromImportStatement from_import_statement = (PyFromImportStatement)parent;
      return resolveFromImportStatementSource(from_import_statement, importRef.asQualifiedName());
    }
    return Collections.emptyList();
  }

  @Nullable
  public static PsiElement resolveFromImportStatementSource(PyFromImportStatement from_import_statement) {
    final PyQualifiedName qName = from_import_statement.getImportSourceQName();
    if (qName == null) {
      return null;
    }
    final List<PsiElement> source = resolveFromImportStatementSource(from_import_statement, qName);
    return source.isEmpty() ? null : source.get(0);
  }

  @NotNull
  public static List<PsiElement> resolveFromImportStatementSource(PyFromImportStatement from_import_statement, PyQualifiedName qName) {
    boolean absolute_import_enabled = isAbsoluteImportEnabledFor(from_import_statement);
    PsiFile file = from_import_statement.getContainingFile();
    return resolveModule(qName, file, absolute_import_enabled, from_import_statement.getRelativeLevel());
  }

  /**
   * Resolves a module reference in a general case.
   *
   * @param qualifiedName      qualified name of the module reference to resolve
   * @param source_file        where that reference resides; serves as PSI foothold to determine module, project, etc.
   * @param import_is_absolute if false, try old python 2.x's "relative first, absolute next" approach.
   * @param relative_level     if > 0, step back from source_file and resolve from there (even if import_is_absolute is false!).
   * @return list of possible candidates
   */
  @NotNull
  public static List<PsiElement> resolveModule(@Nullable PyQualifiedName qualifiedName, PsiFile source_file,
                                               boolean import_is_absolute, int relative_level) {
    if (qualifiedName == null) return Collections.emptyList();
    String marker = StringUtil.join(qualifiedName.getComponents(), ".") + "#" + Integer.toString(relative_level);
    Set<String> being_imported = ourBeingImported.get();
    if (being_imported.contains(marker)) return Collections.emptyList(); // break endless loop in import
    try {
      being_imported.add(marker);
      if (relative_level > 0) {
        // "from ...module import"
        final PsiElement module = resolveModuleAt(stepBackFrom(source_file, relative_level), source_file, qualifiedName);
        return module != null ? Collections.singletonList(module) : Collections.<PsiElement>emptyList();
      }
      else { // "from module import"
        if (import_is_absolute) {
          return resolveModulesInRoots(qualifiedName, source_file);
        }
        else {
          final PsiDirectory dir = source_file.getOriginalFile().getContainingDirectory();
          PsiElement module = resolveModuleAt(dir, source_file, qualifiedName);
          if (module != null) {
            return Collections.singletonList(module);
          }
          List<PsiElement> found_in_roots = resolveModulesInRoots(qualifiedName, source_file);
          if (found_in_roots.size() > 0) return found_in_roots;

          return Collections.emptyList();
        }
      }
    }
    finally {
      being_imported.remove(marker);
    }
  }

  /**
   * Searches for a module at given directory, unwinding qualifiers and traversing directories as needed.
   *
   * @param directory     where to start from; top qualifier will be searched for here.
   * @param sourceFile    the file containing the import statement being resolved
   * @param qualifiedName the qualified name of the module to search
   * @return module's file, or null.
   */
  @Nullable
  private static PsiElement resolveModuleAt(PsiDirectory directory, PsiFile sourceFile, PyQualifiedName qualifiedName) {
    // prerequisites
    if (directory == null || !directory.isValid()) return null;
    if (sourceFile == null || !sourceFile.isValid()) return null;

    PsiElement seeker = directory;
    for (String name : qualifiedName.getComponents()) {
      if (name == null) {
        return null;
      }
      seeker = resolveChild(seeker, name, sourceFile, null, true, true);
    }
    return seeker;
  }

  @Nullable
  public static PsiElement resolveModuleInRoots(@NotNull PyQualifiedName moduleQualifiedName, @Nullable PsiElement foothold) {
    final List<PsiElement> candidates = resolveModulesInRoots(moduleQualifiedName, foothold);
    return candidates.isEmpty() ? null : candidates.get(0);
  }

  /**
   * Returns the list of directories/files under different project roots which match the specified qualified name.
   *
   * @param moduleQualifiedName the qualified name to find
   * @param foothold            the PSI element in the context of which the search is performed
   * @return the list of matching directories or files, or an empty list if nothing was found
   */
  @NotNull
  public static List<PsiElement> resolveModulesInRoots(@NotNull PyQualifiedName moduleQualifiedName, @Nullable PsiElement foothold) {
    if (foothold == null || !foothold.isValid()) return Collections.emptyList();
    PsiFile footholdFile = foothold.getContainingFile();
    if (footholdFile == null || !footholdFile.isValid()) return Collections.emptyList();

    PythonPathCache cache = getPathCache(foothold, footholdFile);
    if (cache != null) {
      final List<PsiElement> cachedResults = cache.get(moduleQualifiedName);
      if (cachedResults != null) {
        return cachedResults;
      }
    }

    List<PsiElement> results =
      visitRoots(moduleQualifiedName, foothold.getManager(), ModuleUtil.findModuleForPsiElement(foothold), foothold, true);

    if (cache != null) {
      cache.put(moduleQualifiedName, results);
    }
    return results;
  }

  private static List<PsiElement> visitRoots(@NotNull PyQualifiedName moduleQualifiedName,
                                             @NotNull PsiManager manager,
                                             @Nullable Module module,
                                             @Nullable PsiElement foothold,
                                             boolean checkForPackage) {
    // resolve the name considering every source root as a package dir, as if it's a deployed package. django console does so.
    PsiFile footholdFile = foothold != null ? foothold.getContainingFile() : null;
    boolean has_djando_facet = false;
    if (module != null) {
      has_djando_facet = FacetManager.getInstance(module).getFacetByType(DjangoFacetType.ID) != null;
    }
    ResolveInRootVisitor visitor;
    if (has_djando_facet) {
      visitor = new ResolveInRootAsTopPackageVisitor(moduleQualifiedName, manager, footholdFile, checkForPackage);
    }
    else {
      visitor = new ResolveInRootVisitor(moduleQualifiedName, manager, footholdFile, checkForPackage);
    }
    if (module != null) {
      visitRoots(module, visitor);
      return visitor.resultsAsList();
    }
    else if (foothold != null) {
      visitRoots(foothold, visitor);
      return visitor.resultsAsList();
    }
    else {
      throw new IllegalStateException();
    }
  }

  @Nullable
  private static PythonPathCache getPathCache(PsiElement foothold, PsiFile footholdFile) {
    PythonPathCache cache = null;
    final Module module = ModuleUtil.findModuleForPsiElement(foothold);
    if (module != null) {
      cache = PythonModulePathCache.getInstance(module);
    }
    else {
      final Sdk sdk = PyBuiltinCache.findSdkForFile(footholdFile);
      if (sdk != null) {
        cache = PythonSdkPathCache.getInstance(foothold.getProject(), sdk);
      }
    }
    return cache;
  }

  @NotNull
  public static List<PsiElement> resolveModulesInRoots(@NotNull Module module, @NotNull PyQualifiedName moduleQualifiedName,
                                                       boolean checkForPackage) {
    PythonPathCache cache = PythonModulePathCache.getInstance(module);
    final List<PsiElement> cachedResults = cache.get(moduleQualifiedName);
    if (cachedResults != null) {
      return cachedResults;
    }
    List<PsiElement> results = visitRoots(moduleQualifiedName, PsiManager.getInstance(module.getProject()), module, null, checkForPackage);
    cache.put(moduleQualifiedName, results);
    return results;
  }

  @NotNull
  public static List<PsiElement> resolveModulesInRootProvider(@NotNull RootProvider rootProvider,
                                                              @NotNull Module module,
                                                              @NotNull PyQualifiedName moduleQualifiedName) {
    ResolveInRootVisitor visitor = new ResolveInRootVisitor(moduleQualifiedName, PsiManager.getInstance(module.getProject()), null,
                                                            true);
    if (!visitModuleContentEntries(module, visitor)) {
      for (VirtualFile file : rootProvider.getFiles(OrderRootType.CLASSES)) {
        visitor.visitRoot(file);
      }
    }
    return visitor.resultsAsList();
  }

  public static void visitRoots(@NotNull final PsiElement elt, @NotNull final RootVisitor visitor) {
    // real search
    final Module module = ModuleUtil.findModuleForPsiElement(elt);
    if (module != null) {
      visitRoots(module, visitor);
    }
    else {
      visitSdkRoots(elt, visitor);
    }
  }

  public static void visitRoots(@NotNull Module module, RootVisitor visitor) {
    // TODO: implement a proper module-like approach in PyCharm for "project's dirs on pythonpath", minding proper search order
    // Module-based approach works only in the IDEA plugin.
    if (visitModuleContentEntries(module, visitor)) return;
    // else look in SDK roots
    visitModuleSdkRoots(visitor, module);
  }

  /**
   * Visits module content, sdk roots and libraries
   */
  public static void visitRoots(@NotNull Module module, @NotNull Sdk sdk, RootVisitor visitor) {
    if (visitModuleContentEntries(module, visitor)) return;
    // else look in SDK roots
    if (visitSdkRoots(visitor, sdk)) return;

    //look in libraries
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    rootManager.orderEntries().process(new LibraryRootVisitingPolicy(visitor), null);
  }

  private static void visitSdkRoots(PsiElement elt, RootVisitor visitor) {
    // no module, another way to look in SDK roots
    final PsiFile elt_psifile = elt.getContainingFile();
    if (elt_psifile != null) {  // formality
      final VirtualFile elt_vfile = elt_psifile.getOriginalFile().getVirtualFile();
      if (elt_vfile != null) { // reality
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(elt.getProject()).getFileIndex();
        final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(elt_vfile);
        if (orderEntries.size() > 0) {
          for (OrderEntry entry : orderEntries) {
            if (!visitOrderEntryRoots(visitor, entry)) break;
          }
        }
        else {
          // out-of-project file - use roots of SDK assigned to project
          final Sdk sdk = PyBuiltinCache.findSdkForFile(elt_psifile);
          if (sdk != null) {
            visitSdkRoots(visitor, sdk);
          }
        }
      }
    }
  }

  private static boolean visitSdkRoots(@NotNull RootVisitor visitor, @NotNull Sdk sdk) {
    final VirtualFile[] roots = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    for (VirtualFile root : roots) {
      if (!visitor.visitRoot(root)) {
        return true;
      }
    }
    return false;
  }


  private static boolean visitModuleContentEntries(Module module, RootVisitor visitor) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    // look in module sources
    boolean sourceEntriesMissing = true;
    Set<VirtualFile> contentRoots = Sets.newHashSet();
    for (ContentEntry entry : rootManager.getContentEntries()) {
      VirtualFile rootFile = entry.getFile();

      if (rootFile != null && !visitor.visitRoot(rootFile)) return true;
      contentRoots.add(rootFile);
      for (VirtualFile folder : entry.getSourceFolderFiles()) {
        sourceEntriesMissing = false;
        if (!visitor.visitRoot(folder)) return true;
      }
    }
    if (sourceEntriesMissing) {
      // fallback for a case without any source entries: use project root
      VirtualFile projectRoot = module.getProject().getBaseDir();
      if (projectRoot != null && !contentRoots.contains(projectRoot) && !visitor.visitRoot(projectRoot)) return true;
    }
    return false;
  }

  private static void visitModuleSdkRoots(@NotNull RootVisitor visitor, @NotNull Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    rootManager.orderEntries().process(new SdkRootVisitingPolicy(visitor), null);
  }

  private static boolean visitOrderEntryRoots(RootVisitor visitor, OrderEntry entry) {
    Set<VirtualFile> allRoots = new LinkedHashSet<VirtualFile>();
    Collections.addAll(allRoots, entry.getFiles(OrderRootType.SOURCES));
    Collections.addAll(allRoots, entry.getFiles(OrderRootType.CLASSES));
    for (VirtualFile root : allRoots) {
      if (!visitor.visitRoot(root)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static PsiElement resolveInRoots(@NotNull final PsiElement context, final String name) {
    return resolveInRoots(context, PyQualifiedName.fromDottedString(name));
  }

  /**
   * Looks for a name among element's module's roots; if there's no module, then among project's roots.
   *
   * @param context PSI element that defines the module and/or the project.
   * @param refName module name to be found among roots.
   * @return a PsiFile, a child of a root.
   */
  @Nullable
  public static PsiElement resolveInRoots(@NotNull final PsiElement context, final PyQualifiedName qualifiedName) {
    // NOTE: a quick and dirty temporary fix for "current dir" root path, which is assumed to be present first (but may be not).
    if (qualifiedName.getComponentCount() == 1) {
      PsiElement res = resolveInCurrentDir(context, qualifiedName.getLastComponent());
      if (res != null) {
        return res;
      }
    }
    return resolveModuleInRoots(qualifiedName, context);
  }

  @Nullable
  public static PsiElement resolveInCurrentDir(@NotNull final PsiElement elt, final String refName) {
    PsiFile pfile = elt.getContainingFile();
    VirtualFile vfile = pfile.getVirtualFile();
    if (vfile == null) { // we're probably within a copy, e.g. for completion; get the real thing
      pfile = pfile.getOriginalFile();
    }
    if (pfile != null) {
      PsiDirectory pdir = pfile.getContainingDirectory();
      if (pdir != null) {
        PsiElement child_elt = resolveChild(pdir, refName, pfile, null, true, true);
        if (child_elt != null) return child_elt;
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveForeignImport(final PyElement importElement,
                                                 final PyQualifiedName importText,
                                                 final PyQualifiedName importFrom) {
    for (PyImportResolver resolver : Extensions.getExtensions(PyImportResolver.EP_NAME)) {
      PsiElement result = resolver.resolveImportReference(importElement, importText, importFrom);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  static class ResolveInRootVisitor implements RootVisitor {
    final PsiFile myFootholdFile;
    final boolean myCheckForPackage;
    final @NotNull PyQualifiedName myQualifiedName;
    final @NotNull PsiManager myPsiManager;
    final Set<PsiElement> results = Sets.newLinkedHashSet();

    public ResolveInRootVisitor(@NotNull PyQualifiedName qName,
                                @NotNull PsiManager psiManager,
                                @Nullable PsiFile foothold_file,
                                boolean checkForPackage) {
      myQualifiedName = qName;
      myPsiManager = psiManager;
      myFootholdFile = foothold_file;
      myCheckForPackage = checkForPackage;
    }

    public boolean visitRoot(final VirtualFile root) {
      if (!root.isValid()) {
        return true;
      }
      PsiElement module = resolveInRoot(root, myQualifiedName, myPsiManager, myFootholdFile, myCheckForPackage);
      if (module != null) {
        results.add(module);
      }

      return true;
    }

    @NotNull
    public List<PsiElement> resultsAsList() {
      return Lists.newArrayList(results);
    }

    @Nullable
    protected PsiElement resolveInRoot(VirtualFile root,
                                       PyQualifiedName qualifiedName,
                                       PsiManager psiManager,
                                       @Nullable PsiFile foothold_file,
                                       boolean checkForPackage) {
      PsiElement module = root.isDirectory() ? psiManager.findDirectory(root) : psiManager.findFile(root);
      if (module == null) return null;
      for (String component : qualifiedName.getComponents()) {
        if (component == null) {
          module = null;
          break;
        }
        module = resolveChild(module, component, foothold_file, root, false, checkForPackage); // only files, we want a module
      }
      return module;
    }
  }

  /**
   * Visits roots and detects if qName is a name of top package coincinding with a root:
   * that is, tha package is not one of root's children, but the root itself.
   */
  private static class ResolveInRootAsTopPackageVisitor extends ResolveInRootVisitor {
    public ResolveInRootAsTopPackageVisitor(@NotNull PyQualifiedName qName,
                                            @NotNull PsiManager psiManager,
                                            @Nullable PsiFile foothold_file,
                                            boolean checkForPackage) {
      super(qName, psiManager, foothold_file, checkForPackage);
    }

    @Override
    public boolean visitRoot(VirtualFile root) {
      if (!root.isValid()) {
        return true;
      }
      PsiElement module = resolveInRoot(root, myQualifiedName, myPsiManager, myFootholdFile, myCheckForPackage);
      if (module != null) {
        results.add(module);
      }

      if (myQualifiedName.matchesPrefix(PyQualifiedName.fromDottedString(root.getName()))) {
        module = resolveInRoot(root.getParent(), myQualifiedName, myPsiManager, myFootholdFile, myCheckForPackage);
        if (module != null) {
          results.add(module);
        }
      }

      return true;
    }
  }

  /**
   * Tries to find referencedName under the parent element. Used to resolve any names that look imported.
   * Parent might happen to be a PyFile(__init__.py), then it is treated <i>both</i> as a file and as ist base dir.
   * For details of this ugly magic, see {@link com.jetbrains.python.psi.impl.PyReferenceExpressionImpl#resolve()}.
   *
   * @param parent          element under which to look for referenced name; if null, null is returned.
   * @param referencedName  which name to look for.
   * @param containingFile  where we're in.
   * @param root            the root from which we started descending the directory tree (if any)
   * @param fileOnly        if true, considers only a PsiFile child as a valid result; non-file hits are ignored.
   * @param checkForPackage if true, directories are returned only if they contain __init__.py
   * @return the element the referencedName resolves to, or null.
   * @todo: Honor module's __all__ value.
   * @todo: Honor package's __path__ value (hard).
   */
  @Nullable
  public static PsiElement resolveChild(@Nullable final PsiElement parent, @NotNull final String referencedName,
                                        @Nullable final PsiFile containingFile, @Nullable VirtualFile root,
                                        boolean fileOnly, boolean checkForPackage) {
    PsiDirectory dir = null;
    PsiElement ret = null;
    PsiElement possible_ret = null;
    if (parent instanceof PyFileImpl) {
      if (PyNames.INIT_DOT_PY.equals(((PyFile)parent).getName())) {
        // gobject does weird things like '_gobject = sys.modules['gobject._gobject'], so it's preferable to look at
        // files before looking at names exported from __init__.py
        dir = ((PyFile)parent).getContainingDirectory();
        possible_ret = resolveInDirectory(referencedName, containingFile, dir, root, fileOnly, checkForPackage);
      }

      // OTOH, quite often a module named foo exports a class or function named foo, which is used as a fallback
      // by a module one level higher (e.g. curses.set_key). Prefer it to submodule if possible.
      ret = ((PyFileImpl)parent).getElementNamed(referencedName, false);
      if (ret != null && !PyUtil.instanceOf(ret, PsiFile.class, PsiDirectory.class) &&
          PsiTreeUtil.getStubOrPsiParentOfType(ret, PyExceptPart.class) == null) {
        return ret;
      }

      if (possible_ret != null) return possible_ret;
    }
    else if (parent instanceof PsiDirectory) {
      dir = (PsiDirectory)parent;
    }
    else if (parent instanceof PsiDirectoryContainer) {
      final PsiDirectoryContainer container = (PsiDirectoryContainer)parent;
      for (PsiDirectory childDir : container.getDirectories()) {
        final PsiElement result = resolveInDirectory(referencedName, containingFile, childDir, root, fileOnly, checkForPackage);
        //if (fileOnly && ! (result instanceof PsiFile) && ! (result instanceof PsiDirectory)) return null;
        if (result != null) return result;
      }
    }
    if (dir != null) {
      final PsiElement result = resolveInDirectory(referencedName, containingFile, dir, root, fileOnly, checkForPackage);
      //if (fileOnly && ! (result instanceof PsiFile) && ! (result instanceof PsiDirectory)) return null;
      if (result != null) {
        return result;
      }
    }
    return ret;
  }

  @Nullable
  private static PsiElement resolveInDirectory(final String referencedName, @Nullable final PsiFile containingFile,
                                               final PsiDirectory dir, @Nullable VirtualFile root, boolean isFileOnly,
                                               boolean checkForPackage) {
    if (referencedName == null) return null;

    final PsiDirectory subdir = dir.findSubdirectory(referencedName);
    if (subdir != null && (!checkForPackage || subdir.findFile(PyNames.INIT_DOT_PY) != null)) {
      return subdir;
    }

    final PsiElement module = findPyFileInDir(dir, referencedName);
    if (module != null) return module;

    if (isInSdk(dir)) {
      PsiDirectory skeletonDir = findSkeletonDir(dir, root);
      if (skeletonDir != null) {
        final PsiFile skeletonFile = findPyFileInDir(skeletonDir, referencedName);
        if (skeletonFile != null) {
          return skeletonFile;
        }
      }
    }

    if (!isFileOnly) {
      // not a subdir, not a file; could be a name in parent/__init__.py
      final PsiFile initPy = dir.findFile(PyNames.INIT_DOT_PY);
      if (initPy == containingFile) return null; // don't dive into the file we're in
      if (initPy instanceof PyFile) {
        return ((PyFile)initPy).getElementNamed(referencedName);
      }
    }
    return null;
  }

  @Nullable
  private static PsiFile findPyFileInDir(PsiDirectory dir, String referencedName) {
    final PsiFile file = dir.findFile(referencedName + PyNames.DOT_PY);
    // findFile() does case-insensitive search, and we need exactly matching case (see PY-381)
    if (file != null && FileUtil.getNameWithoutExtension(file.getName()).equals(referencedName)) {
      return file;
    }
    return null;
  }

  private static boolean isInSdk(PsiDirectory dir) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(dir.getProject()).getFileIndex();
    final List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(dir.getVirtualFile());
    for (OrderEntry entry : entries) {
      if (entry instanceof JdkOrderEntry) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiDirectory findSkeletonDir(PsiDirectory dir, @Nullable VirtualFile root) {
    String relativeName = null;
    if (root != null) {
      relativeName = VfsUtil.getRelativePath(dir.getVirtualFile(), root, '/');
    }
    else {
      PyQualifiedName relativeQName = findShortestImportableQName(dir);
      if (relativeQName != null) {
        relativeName = relativeQName.join("/");
      }
    }
    VirtualFile skeletonsRoot = findSkeletonsRoot(dir);
    if (skeletonsRoot != null && relativeName != null) {
      VirtualFile skeletonsVFile =
        relativeName.length() == 0 ? skeletonsRoot : skeletonsRoot.findFileByRelativePath(relativeName.replace(".", "/"));
      if (skeletonsVFile != null) {
        return dir.getManager().findDirectory(skeletonsVFile);
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile findSkeletonsRoot(PsiFileSystemItem fsItem) {
    Sdk sdk = PyBuiltinCache.findSdkForFile(fsItem);
    if (sdk != null) {
      return PythonSdkType.findSkeletonsDir(sdk);
    }
    return null;
  }

  /**
   * Tries to find roots that contain given vfile, and among them the root that contains at the smallest depth.
   */
  private static class PathChoosingVisitor implements RootVisitor {

    private final VirtualFile myVFile;
    private List<String> myResult;

    private PathChoosingVisitor(VirtualFile file) {
      if (!file.isDirectory() && file.getName().equals(PyNames.INIT_DOT_PY)) {
        myVFile = file.getParent();
      }
      else {
        myVFile = file;
      }
    }

    public boolean visitRoot(VirtualFile root) {
      final String relativePath = VfsUtil.getRelativePath(myVFile, root, '/');
      if (relativePath != null) {
        List<String> result = StringUtil.split(relativePath, "/");
        if (myResult == null || result.size() < myResult.size()) {
          if (result.size() > 0) {
            result.set(result.size() - 1, FileUtil.getNameWithoutExtension(result.get(result.size() - 1)));
          }
          for (String component : result) {
            if (!PyNames.isIdentifier(component)) {
              return true;
            }
          }
          myResult = result;
        }
      }
      return myResult == null || myResult.size() > 0;
    }

    @Nullable
    public PyQualifiedName getResult() {
      return myResult != null ? PyQualifiedName.fromComponents(myResult) : null;
    }
  }

  /**
   * Looks for a way to import given file.
   *
   * @param foothold an element in the file to import to (maybe the file itself); used to determine module, roots, etc.
   * @param vfile    file which importable name we want to find.
   * @return a possibly qualified name under which the file may be imported, or null. If there's more than one way (overlapping roots),
   *         the name with fewest qualifiers is selected.
   */
  @Nullable
  public static String findShortestImportableName(PsiElement foothold, @NotNull VirtualFile vfile) {
    final PyQualifiedName qName = findShortestImportableQName(foothold, vfile);
    return qName == null ? null : qName.toString();
  }

  @Nullable
  public static PyQualifiedName findShortestImportableQName(@Nullable PsiFileSystemItem fsItem) {
    VirtualFile vFile = fsItem != null ? fsItem.getVirtualFile() : null;
    return vFile != null ? findShortestImportableQName(fsItem, vFile) : null;
  }

  @Nullable
  public static PyQualifiedName findShortestImportableQName(@NotNull PsiElement foothold, @NotNull VirtualFile vfile) {
    PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
    visitRoots(foothold, visitor);
    return visitor.getResult();
  }

  @Nullable
  public static String findShortestImportableName(Module module, @NotNull VirtualFile vfile) {
    PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
    visitRoots(module, visitor);
    final PyQualifiedName result = visitor.getResult();
    return result == null ? null : result.toString();
  }

  /**
   * Returns the name through which the specified symbol should be imported. This can be different from the qualified name of the
   * symbol (the place where a symbol is defined). For example, Python 2.7 unittest defines TestCase in unittest.case module
   * but it should be imported directly from unittest.
   *
   * @param symbol   the symbol to be imported
   * @param foothold the location where the import statement would be added
   * @return the qualified name, or null if it wasn't possible to calculate one
   */
  @Nullable
  public static PyQualifiedName findCanonicalImportPath(@NotNull PsiElement symbol, @Nullable PsiElement foothold) {
    PsiFileSystemItem srcfile = symbol instanceof PsiFileSystemItem ? (PsiFileSystemItem)symbol : symbol.getContainingFile();
    if (srcfile == null) {
      return null;
    }
    VirtualFile virtualFile = srcfile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (srcfile instanceof PsiFile && symbol instanceof PsiNamedElement && !(symbol instanceof PsiFileSystemItem)) {
      PsiElement toplevel = symbol;
      if (symbol instanceof PyFunction) {
        final PyClass containingClass = ((PyFunction)symbol).getContainingClass();
        if (containingClass != null) {
          toplevel = containingClass;
        }
      }
      PsiDirectory dir = ((PsiFile)srcfile).getContainingDirectory();
      while (dir != null) {
        PsiFile initPy = dir.findFile(PyNames.INIT_DOT_PY);
        if (initPy == null) {
          break;
        }
        if (initPy instanceof PyFile && toplevel.equals(((PyFile)initPy).findExportedName(((PsiNamedElement)toplevel).getName()))) {
          virtualFile = dir.getVirtualFile();
        }
        dir = dir.getParentDirectory();
      }
    }
    final PyQualifiedName qname = findShortestImportableQName(foothold != null ? foothold : symbol, virtualFile);
    if (qname != null) {
      final PyQualifiedName restored = restoreStdlibCanonicalPath(qname);
      if (restored != null) {
        return restored;
      }
    }
    return qname;
  }

  @Nullable
  private static PyQualifiedName restoreStdlibCanonicalPath(PyQualifiedName qname) {
    if (qname.getComponentCount() > 0) {
      final List<String> components = qname.getComponents();
      final String head = components.get(0);
      if (head.equals("_abcoll")) {
        components.set(0, "collections");
        return  PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_functools")) {
        components.set(0, "functools");
        return PyQualifiedName.fromComponents(components);
      }
    }
    return null;
  }

  public static class SdkRootVisitingPolicy extends RootPolicy<PsiElement> {
    private final RootVisitor myVisitor;

    public SdkRootVisitingPolicy(RootVisitor visitor) {
      myVisitor = visitor;
    }

    @Nullable
    public PsiElement visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final PsiElement value) {
      if (value != null) return value;  // for chaining in processOrder()
      visitOrderEntryRoots(myVisitor, jdkOrderEntry);
      return null;
    }

    @Nullable
    @Override
    public PsiElement visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, PsiElement value) {
      if (value != null) return value;  // for chaining in processOrder()
      visitOrderEntryRoots(myVisitor, libraryOrderEntry);
      return null;
    }
  }

  public static class LibraryRootVisitingPolicy extends RootPolicy<PsiElement> {
    private final RootVisitor myVisitor;

    public LibraryRootVisitingPolicy(RootVisitor visitor) {
      myVisitor = visitor;
    }

    @Nullable
    public PsiElement visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final PsiElement value) {
      return null;
    }

    @Nullable
    @Override
    public PsiElement visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, PsiElement value) {
      if (value != null) return value;  // for chaining in processOrder()
      visitOrderEntryRoots(myVisitor, libraryOrderEntry);
      return null;
    }
  }

  /**
   * When a name is imported from a module, tries to find the definition of that name inside the module,
   * as opposed to looking for submodules.
   *
   * @param where an element related to the name, presumably inside import
   * @param name  the name to find
   * @return found element, or null.
   */
  @Nullable
  public static PsiElement findImportedNameInsideModule(@NotNull PyImportElement where, String name) {
    PyStatement stmt = where.getContainingImportStatement();
    if (stmt instanceof PyFromImportStatement) {
      final PyFromImportStatement from_import = (PyFromImportStatement)stmt;
      if (from_import.getImportSourceQName() != null) { // have qname -> have source stub and importing a name, not a module
        final PyReferenceExpression source = from_import.getImportSource();
        if (source != null) {
          PsiElement resolved = source.getReference().resolve();
          if (resolved instanceof PyFile) {
            return ((PyFile)resolved).findExportedName(name);
          }
        }
      }
    }
    return null;
  }

  public static enum PointInImport {
    /**
     * The reference is not inside an import statement.
     */
    NONE,

    /**
     * The reference is inside import and refers to a module
     */
    AS_MODULE,

    /**
     * The reference is inside import and refers to a name imported from a module
     */
    AS_NAME
  }

  /**
   * @param element what we test (identifier, reference, import element, etc)
   * @return the how the element relates to an enclosing import statement, if any
   */
  public static PointInImport getPointInImport(@NotNull PsiElement element) {
    PsiElement parent = PsiTreeUtil.getNonStrictParentOfType(
      element,
      PyImportElement.class, PyFromImportStatement.class
    );
    if (parent instanceof PyFromImportStatement) {
      return PointInImport.AS_MODULE; // from foo ...
    }
    if (parent instanceof PyImportElement) {
      PsiElement statement = parent.getParent();
      if (statement instanceof PyImportStatement) {
        return PointInImport.AS_MODULE; // import foo,...
      }
      else if (statement instanceof PyFromImportStatement) {
        return PointInImport.AS_NAME;
      }
    }
    return PointInImport.NONE;
  }
}
