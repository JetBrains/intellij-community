// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyStubPackages;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;


public final class QualifiedNameFinder {
  /**
   * Looks for a way to import given file.
   *
   * @param foothold an element in the file to import to (maybe the file itself); used to determine module, roots, etc.
   * @param vfile    file which importable name we want to find.
   * @return a possibly qualified name under which the file may be imported, or null. If there's more than one way (overlapping roots),
   *         the name with fewest qualifiers is selected.
   */
  public static @Nullable String findShortestImportableName(@NotNull PsiElement foothold, @NotNull VirtualFile vfile) {
    final QualifiedName qName = findShortestImportableQName(foothold, vfile);
    return qName == null ? null : qName.toString();
  }

  public static @Nullable QualifiedName findCachedShortestImportableName(@NotNull PsiElement foothold, @NotNull VirtualFile virtualFile) {
    final PythonPathCache cache = ResolveImportUtil.getPathCache(foothold);
    if (cache != null) {
      final List<QualifiedName> names = cache.getNames(virtualFile);
      if (names != null) {
        return shortestQName(names);
      }
    }
    return null;
  }

  public static @Nullable QualifiedName findShortestImportableQName(@Nullable PsiFileSystemItem fsItem) {
    VirtualFile vFile = fsItem != null ? fsItem.getVirtualFile() : null;
    return vFile != null ? findShortestImportableQName(fsItem, vFile) : null;
  }

  public static @Nullable QualifiedName findShortestImportableQName(@NotNull PsiElement foothold, @NotNull VirtualFile vfile) {
    return shortestQName(findImportableQNames(foothold, vfile));
  }

  public static @NotNull List<QualifiedName> findImportableQNames(@NotNull PsiElement foothold, @NotNull VirtualFile vfile) {
    final PythonPathCache cache = ResolveImportUtil.getPathCache(foothold);
    final List<QualifiedName> names = cache != null ? cache.getNames(vfile) : null;
    if (names != null) {
      return names;
    }
    PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
    RootVisitorHost.visitRoots(foothold, visitor);
    final List<QualifiedName> results = visitor.getResults();
    if (cache != null) {
      cache.putNames(vfile, results);
    }
    return results;
  }

  private static @Nullable QualifiedName shortestQName(@NotNull List<QualifiedName> qNames) {
    return qNames.stream().min(Comparator.comparingInt(QualifiedName::getComponentCount)).orElse(null);
  }

  public static @Nullable String findShortestImportableName(Module module, @NotNull VirtualFile vfile) {
    final PythonPathCache cache = PythonModulePathCache.getInstance(module);
    List<QualifiedName> names = cache.getNames(vfile);
    if (names == null) {
      final PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
      RootVisitorHost.visitRoots(module, false, visitor);
      names = visitor.getResults();
      cache.putNames(vfile, names);
    }
    return Objects.toString(shortestQName(names), null);
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
  public static @Nullable QualifiedName findCanonicalImportPath(@NotNull PsiElement symbol, @Nullable PsiElement foothold) {
    return PyUtil.getNullableParameterizedCachedValue(symbol, Couple.of(symbol, foothold), QualifiedNameFinder::doFindCanonicalImportPath);
  }

  private static @Nullable QualifiedName doFindCanonicalImportPath(@NotNull Couple<PsiElement> param) {
    final PsiElement symbol = param.getFirst();
    final PsiElement foothold = param.getSecond();

    final PsiFileSystemItem srcfile = PyPsiUtils.getFileSystemItem(symbol);
    if (srcfile == null) {
      return null;
    }
    VirtualFile virtualFile = srcfile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (srcfile instanceof PsiFile && symbol instanceof PsiNamedElement namedSymbol && !(symbol instanceof PsiFileSystemItem)) {
      PsiNamedElement toplevel = namedSymbol;
      if (symbol instanceof PyFunction) {
        final PyClass containingClass = ((PyFunction)symbol).getContainingClass();
        if (containingClass != null) {
          toplevel = containingClass;
        }
      }
      PsiFileSystemItem item = srcfile;
      PsiDirectory dir = ((PsiFile)srcfile).getContainingDirectory();
      while (dir != null) {
        PyFile initPy = as(PyUtil.turnDirIntoInit(dir), PyFile.class);
        if (initPy != null && initPy.getImportTargets().isEmpty() && initPy.getFromImports().isEmpty()) {
          initPy = jumpFromBinarySkeletonsToRealInitPy(initPy);
        }

        if (initPy != null && reexports(initPy, toplevel)) {
          virtualFile = dir.getVirtualFile();
        }
        else {
          var namesake = findReexportingPublicNamesake(item, dir, toplevel);
          if (namesake != null) {
            virtualFile = namesake;
          }
        }
        if (initPy == null) {
          break;
        }
        item = dir;
        dir = dir.getParentDirectory();
      }
    }
    final QualifiedName qname = findShortestImportableQName(foothold != null ? foothold : symbol, virtualFile);
    if (qname != null) {
      final QualifiedName restored = canonizeQualifiedName(symbol, qname, foothold);
      if (restored != null) return restored;
    }
    return qname;
  }

  /**
   * Returns the public namesake of {@code item} if {@code item} is private and the namesake re-exports {@code toplevel}.
   * <p>
   * The namesake is the module, stub, or package in {@code directory} with the name of {@code item} without the leading underscores,
   * for example {@code mod} for {@code _mod}.
   */
  private static @Nullable VirtualFile findReexportingPublicNamesake(@NotNull PsiFileSystemItem item,
                                                                    @NotNull PsiDirectory directory,
                                                                    @NotNull PsiNamedElement toplevel) {
    var name = item instanceof PsiDirectory ? item.getName() : FileUtilRt.getNameWithoutExtension(item.getName());
    if (PyUtil.getInitialUnderscores(name) == 0 || PyNames.INIT.equals(name)) return null;
    var namesake = StringUtil.trimLeading(name, '_');
    if (!PyNames.isIdentifier(namesake)) return null;

    var psiManager = directory.getManager();
    for (String childName : List.of(namesake, namesake + PyNames.DOT_PYI, namesake + PyNames.DOT_PY)) {
      var child = directory.getVirtualFile().findChild(childName);
      if (child == null) continue;
      PsiFileSystemItem childItem = child.isDirectory() ? psiManager.findDirectory(child) : psiManager.findFile(child);
      var module = as(PyUtil.turnDirIntoInit(childItem), PyFile.class);
      if (module != null && reexports(module, toplevel)) return child;
    }
    return null;
  }

  /**
   * Checks that {@code module} makes {@code toplevel} available under its own name.
   * <p>
   * A plain import counts only in a package {@code __init__} file. A module must alias the name
   * ({@code from m import X as X}) or list it in {@code __all__}. A star import counts in a stub file only.
   */
  private static boolean reexports(@NotNull PyFile module, @NotNull PsiNamedElement toplevel) {
    final String name = toplevel.getName();
    if (name == null) return false;
    final boolean plainImportCounts = PyNames.INIT.equals(FileUtilRt.getNameWithoutExtension(module.getName()));
    for (RatedResolveResult result : module.multiResolveName(name)) {
      if (result.getElement() != toplevel) continue;
      if (plainImportCounts) return true;
      final PyImportedNameDefiner definer = result instanceof ImportedResolveResult imported ? imported.getDefiner() : null;
      if (definer instanceof PyStarImportElement && module instanceof PyiFile) return true;
      if (definer instanceof PyImportElement importElement && importElement.getAsName() != null) return true;
      final List<String> dunderAll = module.getDunderAll();
      if (dunderAll != null && dunderAll.contains(name)) return true;
    }
    return false;
  }

  private static @NotNull PyFile jumpFromBinarySkeletonsToRealInitPy(@NotNull PyFile initPy) {
    if (PythonSdkUtil.isElementInSkeletons(initPy)) {
      QualifiedName packageName = findShortestImportableQName(initPy);
      if (packageName != null) {
        List<PsiElement> namesakeResults = PyResolveImportUtil.resolveQualifiedName(packageName, PyResolveImportUtil.fromFoothold(initPy));
        PsiElement nonSkeletonResult = ContainerUtil.find(namesakeResults, e -> !PythonSdkUtil.isElementInSkeletons(e));
        PsiDirectory libPackage = as(nonSkeletonResult, PsiDirectory.class);
        if (libPackage != null) {
          PyFile libInitPy = as(PyUtil.turnDirIntoInit(libPackage), PyFile.class);
          if (libInitPy != null && libInitPy != initPy) {
            return libInitPy;
          }
        }
      }
    }
    return initPy;
  }

  public static @Nullable QualifiedName canonizeQualifiedName(PsiElement symbol, QualifiedName qname, PsiElement foothold) {
    for (PyCanonicalPathProvider provider : PyCanonicalPathProvider.EP_NAME.getExtensionList()) {
      final QualifiedName restored = provider.getCanonicalPath(symbol, qname, foothold);
      if (restored != null) {
        return restored;
      }
    }
    return null;
  }

  public static @Nullable String getQualifiedName(@NotNull PyElement element) {
    return CachedValuesManager.getCachedValue(element, () ->
      new Result<>(getQualifiedNameInner(element), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static @Nullable String getQualifiedNameInner(final @NotNull PyElement element) {
    final String name = element.getName();
    if (name != null) {
      final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
      if (owner instanceof PyClass) {
        final String classQName = ((PyClass)owner).getQualifiedName();
        if (classQName != null) {
          return classQName + "." + name;
        }
      }
      if (owner instanceof PyTypeAliasStatement typeAliasStatement) {
        final String typeQName = typeAliasStatement.getQualifiedName();
        if (typeQName != null) {
          return typeQName + "." + name;
        }
      }
      else if (owner instanceof PyFile) {
        if (builtinCache.isBuiltin(element)) {
          // Builtins are always qualified with "builtins" — including in Python 2, whose runtime module is "__builtin__" —
          // so that consumers have a single canonical qualifier to match against (see PyNames.FQN.unqualifyBuiltinName).
          // Computed deterministically instead of via findShortestImportableName, which may resolve the builtins file
          // inconsistently under resolve-cache pressure.
          return PyBuiltinCache.BUILTIN_MODULE_3K + "." + name;
        }
        else {
          final VirtualFile virtualFile = ((PyFile)owner).getVirtualFile();
          if (virtualFile != null) {
            final String fileQName = findShortestImportableName(element, virtualFile);
            if (fileQName != null) {
              return fileQName + "." + name;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Tries to find roots that contain given vfile.
   */
  private static final class PathChoosingVisitor implements RootVisitor {
    private final @NotNull VirtualFile myVFile;
    private final @NotNull Set<QualifiedName> myResults = new LinkedHashSet<>();

    private PathChoosingVisitor(@NotNull VirtualFile file) {
      myVFile = file;
    }

    @Override
    public boolean visitRoot(@NotNull VirtualFile root, @Nullable Module module, @Nullable Sdk sdk, boolean isModuleSource) {
      QualifiedName qName = computeQualifiedNameInRoot(root, myVFile);
      if (qName != null && ContainerUtil.all(qName.getComponents(), PyNames::isIdentifier)) {
        myResults.add(qName);
      }
      return true;
    }

    public @NotNull List<QualifiedName> getResults() {
      return new ArrayList<>(myResults);
    }
  }

  @ApiStatus.Internal
  public abstract static class QualifiedNameBasedScope extends GlobalSearchScope {
    private final ProjectFileIndex myProjectFileIndex;

    public QualifiedNameBasedScope(@NotNull Project project) {
      super(project);
      myProjectFileIndex = ProjectFileIndex.getInstance(project);
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }

    @Override
    public final boolean contains(@NotNull VirtualFile file) {
      VirtualFile closestRoot = findClosestRoot(file);
      if (closestRoot == null) return true;
      QualifiedName qName = computeQualifiedNameInRoot(closestRoot, file);
      if (qName == null) return true;
      return containsQualifiedNameInRoot(closestRoot, qName);
    }

    protected abstract boolean containsQualifiedNameInRoot(@NotNull VirtualFile root, @NotNull QualifiedName qName);

    private @Nullable VirtualFile findClosestRoot(@NotNull VirtualFile vFile) {
      VirtualFile sourceRoot = myProjectFileIndex.getSourceRootForFile(vFile);
      if (sourceRoot != null) return sourceRoot;
      VirtualFile contentRoot = myProjectFileIndex.getContentRootForFile(vFile);
      if (contentRoot != null) return contentRoot;
      VirtualFile libraryRoot = myProjectFileIndex.getClassRootForFile(vFile);
      if (libraryRoot != null) return libraryRoot;
      return null;
    }
  }

  private static @Nullable QualifiedName computeQualifiedNameInRoot(@NotNull VirtualFile root, @NotNull VirtualFile file) {
    String relativePath = VfsUtilCore.getRelativePath(file, root, VfsUtilCore.VFS_SEPARATOR_CHAR);
    if (StringUtil.isEmpty(relativePath)) {
      return null;
    }

    List<String> components = new ArrayList<>(StringUtil.split(relativePath, VfsUtilCore.VFS_SEPARATOR));
    if (components.isEmpty()) {
      return null;
    }

    int lastIndex = components.size() - 1;
    String nameWithoutExtension = FileUtilRt.getNameWithoutExtension(components.get(lastIndex));
    if (!file.isDirectory() && nameWithoutExtension.equals(PyNames.INIT)) {
      components.remove(lastIndex);
    }
    else {
      components.set(lastIndex, nameWithoutExtension);
    }

    if (components.isEmpty() || ContainerUtil.exists(components, part -> part.contains("."))) {
      return null;
    }
    // A qualified name might still contain "-stubs" non-identifier part as its first component.
    return PyStubPackages.convertStubToRuntimePackageName(QualifiedName.fromComponents(components));
  }
}
