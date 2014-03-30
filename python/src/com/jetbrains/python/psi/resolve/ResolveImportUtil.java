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
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
  public static PsiElement resolveImportElement(PyImportElement importElement, @NotNull final QualifiedName qName) {
    List<RatedResolveResult> targets;
    final PyStatement importStatement = importElement.getContainingImportStatement();
    if (importStatement instanceof PyFromImportStatement) {
      targets = resolveNameInFromImport((PyFromImportStatement)importStatement, qName);
    }
    else { // "import foo"
      targets = resolveNameInImportStatement(importElement, qName);
    }
    final List<RatedResolveResult> resultList = RatedResolveResult.sorted(targets);
    return resultList.size() > 0 ? resultList.get(0).getElement() : null;
  }

  public static List<RatedResolveResult> resolveNameInImportStatement(PyImportElement importElement, @NotNull QualifiedName qName) {
    final PsiFile file = importElement.getContainingFile().getOriginalFile();
    boolean absoluteImportEnabled = isAbsoluteImportEnabledFor(importElement);
    final List<PsiElement> modules = resolveModule(qName, file, absoluteImportEnabled, 0);
    return rateResults(modules);
  }

  public static List<RatedResolveResult> resolveNameInFromImport(PyFromImportStatement importStatement, @NotNull QualifiedName qName) {
    PsiFile file = importStatement.getContainingFile().getOriginalFile();
    String name = qName.getComponents().get(0);

    final List<PsiElement> candidates = importStatement.resolveImportSourceCandidates();
    List<PsiElement> resultList = new ArrayList<PsiElement>();
    for (PsiElement candidate : candidates) {
      if (!candidate.isValid()) {
        throw new PsiInvalidElementAccessException(candidate, "Got an invalid candidate from resolveImportSourceCandidates(): " + candidate.getClass());
      }
      if (candidate instanceof PsiDirectory) {
        candidate = PyUtil.getPackageElement((PsiDirectory)candidate, importStatement);
      }
      PsiElement result = resolveChild(candidate, name, file, false, true);
      if (result != null) {
        if (!result.isValid()) {
          throw new PsiInvalidElementAccessException(result, "Got an invalid candidate from resolveChild(): " + result.getClass());
        }
        resultList.add(result);
      }
    }
    if (!resultList.isEmpty()) {
      return rateResults(resultList);
    }
    return Collections.emptyList();
  }

  @NotNull
  public static List<PsiElement> resolveFromImportStatementSource(PyFromImportStatement from_import_statement, QualifiedName qName) {
    boolean absoluteImportEnabled = isAbsoluteImportEnabledFor(from_import_statement);
    PsiFile file = from_import_statement.getContainingFile();
    return resolveModule(qName, file, absoluteImportEnabled, from_import_statement.getRelativeLevel());
  }

  /**
   * Resolves a module reference in a general case.
   *
   *
   * @param qualifiedName     qualified name of the module reference to resolve
   * @param sourceFile        where that reference resides; serves as PSI foothold to determine module, project, etc.
   * @param importIsAbsolute  if false, try old python 2.x's "relative first, absolute next" approach.
   * @param relativeLevel     if > 0, step back from sourceFile and resolve from there (even if importIsAbsolute is false!).
   * @return list of possible candidates
   */
  @NotNull
  public static List<PsiElement> resolveModule(@Nullable QualifiedName qualifiedName, PsiFile sourceFile,
                                               boolean importIsAbsolute, int relativeLevel) {
    if (qualifiedName == null || sourceFile == null) {
      return Collections.emptyList();
    }
    final String marker = StringUtil.join(qualifiedName.getComponents(), ".") + "#" + Integer.toString(relativeLevel);
    final Set<String> beingImported = ourBeingImported.get();
    if (beingImported.contains(marker)) {
      return Collections.emptyList(); // break endless loop in import
    }
    try {
      beingImported.add(marker);
      final QualifiedNameResolver visitor = new QualifiedNameResolverImpl(qualifiedName).fromElement(sourceFile);
      if (relativeLevel > 0) {
        // "from ...module import"
        visitor.withRelative(relativeLevel).withoutRoots();
      }
      else {
        // "from module import"
        if (!importIsAbsolute) {
          visitor.withRelative(0);
        }
      }
      List<PsiElement> results = visitor.resultsAsList();
      if (results.isEmpty() && relativeLevel == 0 && !importIsAbsolute) {
        results = resolveRelativeImportAsAbsolute(sourceFile, qualifiedName);
      }
      return results;
    }
    finally {
      beingImported.remove(marker);
    }
  }

  /**
   * Try to resolve relative import as absolute in roots, not in its parent directory.
   *
   * This may be useful for resolving to child skeleton modules located in other directories.
   *
   * @param foothold        foothold file.
   * @param qualifiedName   relative import name.
   * @return                list of resolved elements.
   */
  @NotNull
  private static List<PsiElement> resolveRelativeImportAsAbsolute(@NotNull PsiFile foothold,
                                                                  @NotNull QualifiedName qualifiedName) {
    final PsiDirectory containingDirectory = foothold.getContainingDirectory();
    if (containingDirectory != null) {
      final QualifiedName containingPath = QualifiedNameFinder.findCanonicalImportPath(containingDirectory, null);
      if (containingPath != null && containingPath.getComponentCount() > 0) {
        final QualifiedName absolutePath = containingPath.append(qualifiedName.toString());
        final QualifiedNameResolver absoluteVisitor = new QualifiedNameResolverImpl(absolutePath).fromElement(foothold);
        return absoluteVisitor.resultsAsList();
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static PsiElement resolveModuleInRoots(@NotNull QualifiedName moduleQualifiedName, @Nullable PsiElement foothold) {
    if (foothold == null) return null;
    QualifiedNameResolver visitor = new QualifiedNameResolverImpl(moduleQualifiedName).fromElement(foothold);
    return visitor.firstResult();
  }

  @Nullable
  static PythonPathCache getPathCache(PsiElement foothold) {
    PythonPathCache cache = null;
    final Module module = ModuleUtilCore.findModuleForPsiElement(foothold);
    if (module != null) {
      cache = PythonModulePathCache.getInstance(module);
    }
    else {
      final Sdk sdk = PyBuiltinCache.findSdkForFile(foothold.getContainingFile());
      if (sdk != null) {
        cache = PythonSdkPathCache.getInstance(foothold.getProject(), sdk);
      }
    }
    return cache;
  }

  /**
   * Tries to find referencedName under the parent element. Used to resolve any names that look imported.
   * Parent might happen to be a PyFile(__init__.py), then it is treated <i>both</i> as a file and as ist base dir.
   *
   * @param parent          element under which to look for referenced name; if null, null is returned.
   * @param referencedName  which name to look for.
   * @param containingFile  where we're in.
   * @param fileOnly        if true, considers only a PsiFile child as a valid result; non-file hits are ignored.
   * @param checkForPackage if true, directories are returned only if they contain __init__.py
   * @return the element the referencedName resolves to, or null.
   * @todo: Honor module's __all__ value.
   * @todo: Honor package's __path__ value (hard).
   */
  @Nullable
  public static PsiElement resolveChild(@Nullable final PsiElement parent, @NotNull final String referencedName,
                                        @Nullable final PsiFile containingFile, boolean fileOnly, boolean checkForPackage) {
    PsiDirectory dir = null;
    PsiElement ret = null;
    PsiElement possible_ret = null;
    final PyResolveContext resolveContext = PyResolveContext.defaultContext();
    if (parent instanceof PyFileImpl) {
      if (PyNames.INIT_DOT_PY.equals(((PyFile)parent).getName())) {
        // gobject does weird things like '_gobject = sys.modules['gobject._gobject'], so it's preferable to look at
        // files before looking at names exported from __init__.py
        dir = ((PyFile)parent).getContainingDirectory();
        possible_ret = resolveInDirectory(referencedName, containingFile, dir, fileOnly, checkForPackage);
      }

      // OTOH, quite often a module named foo exports a class or function named foo, which is used as a fallback
      // by a module one level higher (e.g. curses.set_key). Prefer it to submodule if possible.
      final PyModuleType moduleType = new PyModuleType((PyFile)parent);
      final List<? extends RatedResolveResult> results = moduleType.resolveMember(referencedName, null, AccessDirection.READ,
                                                                                  resolveContext);
      final PsiElement moduleMember = results != null && !results.isEmpty() ? results.get(0).getElement() : null;
      if (!fileOnly || PyUtil.instanceOf(moduleMember, PsiFile.class, PsiDirectory.class)) {
        ret = moduleMember;
      }
      if (ret != null && !PyUtil.instanceOf(ret, PsiFile.class, PsiDirectory.class) &&
          PsiTreeUtil.getStubOrPsiParentOfType(ret, PyExceptPart.class) == null) {
        return ret;
      }

      if (possible_ret != null) return possible_ret;
    }
    else if (parent instanceof PsiDirectory) {
      dir = (PsiDirectory)parent;
    }
    else if (parent != null) {
      PyType refType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(parent, resolveContext.getTypeEvalContext(), null);
      if (refType != null) {
        final List<? extends RatedResolveResult> result = refType.resolveMember(referencedName, null, AccessDirection.READ, resolveContext);
        if (result != null && !result.isEmpty()) {
          return result.get(0).getElement();
        }
      }
    }
    if (dir != null) {
      final PsiElement result = resolveInDirectory(referencedName, containingFile, dir, fileOnly, checkForPackage);
      //if (fileOnly && ! (result instanceof PsiFile) && ! (result instanceof PsiDirectory)) return null;
      if (result != null) {
        return result;
      }
      if (parent instanceof PsiFile) {
        final List<PsiElement> items = resolveRelativeImportAsAbsolute((PsiFile)parent,
                                                                       QualifiedName.fromComponents(referencedName));
        if (!items.isEmpty()) {
          return items.get(0);
        }
      }
    }
    return ret;
  }

  @Nullable
  private static PsiElement resolveInDirectory(final String referencedName, @Nullable final PsiFile containingFile,
                                               final PsiDirectory dir, boolean isFileOnly, boolean checkForPackage) {
    if (referencedName == null) return null;

    final PsiDirectory subdir = dir.findSubdirectory(referencedName);
    if (subdir != null && (!checkForPackage || PyUtil.isPackage(subdir, containingFile))) {
      return subdir;
    }

    final PsiFile module = findPyFileInDir(dir, referencedName);
    if (module != null) return module;

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

  public static ResolveResultList rateResults(List<? extends PsiElement> targets) {
    ResolveResultList ret = new ResolveResultList();
    for (PsiElement target : targets) {
      if (target instanceof PsiDirectory) {
        target = PyUtil.getPackageElement((PsiDirectory)target, null);
      }
      if (target != null) {   // Ignore non-package dirs, worthless
        int rate = RatedResolveResult.RATE_HIGH;
        if (target instanceof PyFile) {
          VirtualFile vFile = ((PyFile)target).getVirtualFile();
          if (vFile != null && vFile.getLength() > 0) {
            rate += 100;
          }
          for (PyResolveResultRater rater : Extensions.getExtensions(PyResolveResultRater.EP_NAME)) {
            rate += rater.getRate(target);
          }
        }
        ret.poke(target, rate);
      }
    }
    return ret;
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
