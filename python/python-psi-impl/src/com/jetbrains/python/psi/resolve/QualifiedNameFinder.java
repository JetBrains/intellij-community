// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyStubPackages;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class QualifiedNameFinder {
  /**
   * Looks for a way to import given file.
   *
   * @param foothold an element in the file to import to (maybe the file itself); used to determine module, roots, etc.
   * @param vfile    file which importable name we want to find.
   * @return a possibly qualified name under which the file may be imported, or null. If there's more than one way (overlapping roots),
   *         the name with fewest qualifiers is selected.
   */
  @Nullable
  public static String findShortestImportableName(@NotNull PsiElement foothold, @NotNull VirtualFile vfile) {
    final QualifiedName qName = findShortestImportableQName(foothold, vfile);
    return qName == null ? null : qName.toString();
  }

  @Nullable
  public static QualifiedName findShortestImportableQName(@Nullable PsiFileSystemItem fsItem) {
    VirtualFile vFile = fsItem != null ? fsItem.getVirtualFile() : null;
    return vFile != null ? findShortestImportableQName(fsItem, vFile) : null;
  }

  @Nullable
  public static QualifiedName findShortestImportableQName(@NotNull PsiElement foothold, @NotNull VirtualFile vfile) {
    return shortestQName(findImportableQNames(foothold, vfile));
  }

  @NotNull
  public static List<QualifiedName> findImportableQNames(@NotNull PsiElement foothold, @NotNull VirtualFile vfile) {
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

  @Nullable
  private static QualifiedName shortestQName(@NotNull List<QualifiedName> qNames) {
    return qNames.stream().min(Comparator.comparingInt(QualifiedName::getComponentCount)).orElse(null);
  }

  @Nullable
  public static String findShortestImportableName(Module module, @NotNull VirtualFile vfile) {
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
  @Nullable
  public static QualifiedName findCanonicalImportPath(@NotNull PsiElement symbol, @Nullable PsiElement foothold) {
    return PyUtil.getNullableParameterizedCachedValue(symbol, Couple.of(symbol, foothold), QualifiedNameFinder::doFindCanonicalImportPath);
  }

  @Nullable
  private static QualifiedName doFindCanonicalImportPath(@NotNull Couple<PsiElement> param) {
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
        if (initPy instanceof PyFile) {
          //noinspection ConstantConditions
          final List<RatedResolveResult> resolved = ((PyFile)initPy).multiResolveName(((PsiNamedElement)toplevel).getName());
          final PsiElement finalTopLevel = toplevel;
          if (resolved.stream().anyMatch(r -> r.getElement() == finalTopLevel)) {
            virtualFile = dir.getVirtualFile();
          }
        }
        dir = dir.getParentDirectory();
      }
    }
    final QualifiedName qname = findShortestImportableQName(foothold != null ? foothold : symbol, virtualFile);
    if (qname != null) {
      final QualifiedName restored = canonizeQualifiedName(qname, foothold);
      if (restored != null) return restored;
    }
    return qname;
  }

  @Nullable
  public static QualifiedName canonizeQualifiedName(QualifiedName qname, PsiElement foothold) {
    for (PyCanonicalPathProvider provider : PyCanonicalPathProvider.EP_NAME.getExtensionList()) {
      final QualifiedName restored = provider.getCanonicalPath(qname, foothold);
      if (restored != null) {
        return restored;
      }
    }
    return null;
  }

  @Nullable
  public static String getQualifiedName(@NotNull PyElement element) {
    return CachedValuesManager.getCachedValue(element, () ->
      new Result<>(getQualifiedNameInner(element), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private static String getQualifiedNameInner(@NotNull final PyElement element) {
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
      else if (owner instanceof PyFile) {
        if (builtinCache.isBuiltin(element)) {
          return name;
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
    @NotNull private final VirtualFile myVFile;
    @NotNull private final Set<QualifiedName> myResults = new LinkedHashSet<>();

    private PathChoosingVisitor(@NotNull VirtualFile file) {
      myVFile = file;
    }

    @Override
    public boolean visitRoot(@NotNull VirtualFile root, @Nullable Module module, @Nullable Sdk sdk, boolean isModuleSource) {
      final String relativePath = VfsUtilCore.getRelativePath(myVFile, root, '/');
      final QualifiedName result = PyStubPackages.convertStubToRuntimePackageName(pathToQualifiedName(relativePath));
      if (result.getComponentCount() != 0) {
        for (String component : result.getComponents()) {
          if (!PyNames.isIdentifier(component)) {
            return true;
          }
        }
        myResults.add(result);
      }
      return true;
    }

    @NotNull
    private QualifiedName pathToQualifiedName(@Nullable String relativePath) {
      if (StringUtil.isEmpty(relativePath)) return QualifiedName.fromComponents();

      final List<String> result = new ArrayList<>(StringUtil.split(relativePath, "/"));
      if (!result.isEmpty()) {
        final int lastIndex = result.size() - 1;
        final String nameWithoutExtension = FileUtilRt.getNameWithoutExtension(result.get(lastIndex));

        if (myVFile.isDirectory() || !nameWithoutExtension.equals(PyNames.INIT)) {
          result.set(lastIndex, nameWithoutExtension);
        }
        else {
          result.remove(lastIndex);
        }

        for (String component : result) {
          if (component.contains(".")) {
            return QualifiedName.fromComponents();
          }
        }

        return QualifiedName.fromComponents(result);
      }

      return QualifiedName.fromComponents();
    }

    @NotNull
    public List<QualifiedName> getResults() {
      return new ArrayList<>(myResults);
    }
  }
}
