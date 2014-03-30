/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    QualifiedName result = null;
    for (QualifiedName name : qNames) {
      if (result == null || name.getComponentCount() < result.getComponentCount()) {
        result = name;
      }
    }
    return result;
  }

  @Nullable
  public static String findShortestImportableName(Module module, @NotNull VirtualFile vfile) {
    final PythonPathCache cache = PythonModulePathCache.getInstance(module);
    final List<QualifiedName> names = cache.getNames(vfile);
    if (names != null) {
      return names.toString();
    }
    PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
    RootVisitorHost.visitRoots(module, false, visitor);
    final List<QualifiedName> results = visitor.getResults();
    cache.putNames(vfile, results);
    final QualifiedName qName = shortestQName(results);
    return qName == null ? null : qName.toString();
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
        if (initPy instanceof PyFile && toplevel.equals(((PyFile)initPy).getElementNamed(((PsiNamedElement)toplevel).getName()))) {
          virtualFile = dir.getVirtualFile();
        }
        dir = dir.getParentDirectory();
      }
    }
    final QualifiedName qname = findShortestImportableQName(foothold != null ? foothold : symbol, virtualFile);
    if (qname != null) {
      for (PyCanonicalPathProvider provider : Extensions.getExtensions(PyCanonicalPathProvider.EP_NAME)) {
        final QualifiedName restored = provider.getCanonicalPath(qname, foothold);
        if (restored != null) {
          return restored;
        }
      }
    }
    return qname;
  }

  /**
   * Tries to find roots that contain given vfile, and among them the root that contains at the smallest depth.
   * For equal depth source root is in preference to library.
   */
  private static class PathChoosingVisitor implements RootVisitor {
    @Nullable private final VirtualFile myVFile;
    @NotNull private final List<QualifiedName> myResults = new ArrayList<QualifiedName>();

    private PathChoosingVisitor(@NotNull VirtualFile file) {
      if (!file.isDirectory() && file.getName().equals(PyNames.INIT_DOT_PY)) {
        myVFile = file.getParent();
      }
      else {
        myVFile = file;
      }
    }

    public boolean visitRoot(VirtualFile root, Module module, Sdk sdk, boolean isModuleSource) {
      if (myVFile != null) {
        final String relativePath = VfsUtilCore.getRelativePath(myVFile, root, '/');
        if (relativePath != null && !relativePath.isEmpty()) {
          List<String> result = StringUtil.split(relativePath, "/");
          if (result.size() > 0) {
            result.set(result.size() - 1, FileUtil.getNameWithoutExtension(result.get(result.size() - 1)));
          }
          for (String component : result) {
            if (!PyNames.isIdentifier(component)) {
              return true;
            }
          }
          myResults.add(QualifiedName.fromComponents(result));
        }
      }
      return true;
    }

    @NotNull
    public List<QualifiedName> getResults() {
      return myResults;
    }
  }
}
