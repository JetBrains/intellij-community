/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.inspections.unresolvedReference.PyPackageAliasesProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

public final class PythonImportUtils {
  private PythonImportUtils() {

  }

  @Nullable
  public static AutoImportQuickFix proposeImportFix(final PyElement node, PsiReference reference) {
    final String text = reference.getElement().getText();
    final String refText = reference.getRangeInElement().substring(text); // text of the part we're working with

    // don't propose meaningless auto imports if no interpreter is configured
    final Module module = ModuleUtilCore.findModuleForPsiElement(node);
    if (module != null && PythonSdkType.findPythonSdk(module) == null) {
      return null;
    }

    // don't show auto-import fix if we're trying to reference a variable which is defined below in the same scope
    ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(node, ScopeOwner.class);
    if (scopeOwner != null && ControlFlowCache.getScope(scopeOwner).containsDeclaration(refText)) {
      return null;
    }

    AutoImportQuickFix fix = addCandidates(node, reference, refText, null);
    if (fix != null) return fix;
    final String packageName = PyPackageAliasesProvider.commonImportAliases.get(refText);
    if (packageName != null) {
      fix = addCandidates(node, reference, packageName, refText);
      if (fix != null) return fix;
    }
    return null;
  }

  @Nullable
  private static AutoImportQuickFix addCandidates(PyElement node, PsiReference reference, String refText, @Nullable String asName) {
    final boolean qualify = !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
    AutoImportQuickFix fix = new AutoImportQuickFix(node, reference.getClass(), refText, qualify);
    Set<String> seenCandidateNames = new HashSet<>(); // true import names

    PsiFile existingImportFile = addCandidatesFromExistingImports(node, refText, fix, seenCandidateNames);
    if (fix.getCandidates().isEmpty()|| fix.hasProjectImports() || Registry.is("python.import.always.ask")) {
      // maybe some unimported file has it, too
      ProgressManager.checkCanceled(); // before expensive index searches
      addSymbolImportCandidates(node, refText, asName, fix, seenCandidateNames, existingImportFile);
    }

    for(PyImportCandidateProvider provider: Extensions.getExtensions(PyImportCandidateProvider.EP_NAME)) {
      provider.addImportCandidates(reference, refText, fix);
    }
    if (!fix.getCandidates().isEmpty()) {
      fix.sortCandidates();
      return fix;
    }
    return null;
  }

  /**
   * maybe the name is importable via some existing 'import foo' statement, and only needs a qualifier.
   * collect all such statements and analyze.
   * NOTE: It only makes sense to look at imports in file scope - there is no guarantee that an import in a local scope will
   * be visible from the scope where the auto-import was invoked
   */
  @Nullable
  private static PsiFile addCandidatesFromExistingImports(PyElement node, String refText, AutoImportQuickFix fix,
                                                          Set<String> seenCandidateNames) {
    PsiFile existingImportFile = null; // if there's a matching existing import, this is the file it imports
    PsiFile file = node.getContainingFile();
    if (file instanceof PyFile) {
      PyFile pyFile = (PyFile)file;
      for (PyImportElement importElement : pyFile.getImportTargets()) {
        existingImportFile = addImportViaElement(refText, fix, seenCandidateNames, existingImportFile, importElement, importElement.resolve());
      }
      for (PyFromImportStatement fromImportStatement : pyFile.getFromImports()) {
        if (!fromImportStatement.isStarImport() && fromImportStatement.getImportElements().length > 0) {
          PsiElement source = fromImportStatement.resolveImportSource();
          existingImportFile = addImportViaElement(refText, fix, seenCandidateNames, existingImportFile, fromImportStatement.getImportElements()[0], source);
        }
      }
    }
    return existingImportFile;
  }

  private static PsiFile addImportViaElement(String refText,
                                             AutoImportQuickFix fix,
                                             Set<String> seenCandidateNames,
                                             PsiFile existingImportFile,
                                             PyImportElement importElement,
                                             PsiElement source) {
    PyFile sourceFile = as(PyUtil.turnDirIntoInit(source), PyFile.class);
    if (sourceFile instanceof PyFileImpl) {

      PsiElement res = sourceFile.findExportedName(refText);
      final String name = res instanceof PyQualifiedNameOwner ? ((PyQualifiedNameOwner)res).getQualifiedName() : null;
      if (name != null && seenCandidateNames.contains(name)) {
        return existingImportFile;
      }
      // allow importing from this source if it either declares the name itself or represents a higher-level package that reexports the name
      if (res != null && !(res instanceof PyFile) && !(res instanceof PyImportElement) && res.getContainingFile() != null &&
          PsiTreeUtil.isAncestor(source, res.getContainingFile(), false)) {
        existingImportFile = sourceFile;
        fix.addImport(res, sourceFile, importElement);
        if (name != null) {
          seenCandidateNames.add(name);
        }
      }
    }
    return existingImportFile;
  }

  private static void addSymbolImportCandidates(PyElement node, String refText, @Nullable String asName, AutoImportQuickFix fix,
                                                Set<String> seenCandidateNames, PsiFile existingImportFile) {
    Project project = node.getProject();
    List<PsiElement> symbols = new ArrayList<>();
    symbols.addAll(PyClassNameIndex.find(refText, project, true));
    GlobalSearchScope scope = PyProjectScopeBuilder.excludeSdkTestsScope(node);
    if (!isQualifier(node)) {
      symbols.addAll(PyFunctionNameIndex.find(refText, project, scope));
    }
    symbols.addAll(PyVariableNameIndex.find(refText, project, scope));
    if (isPossibleModuleReference(node)) {
      symbols.addAll(findImportableModules(node.getContainingFile(), refText, project, scope));
    }
    if (!symbols.isEmpty()) {
      for (PsiElement symbol : symbols) {
        if (isIndexableTopLevel(symbol)) { // we only want top-level symbols
          PsiFileSystemItem srcfile = symbol instanceof PsiFileSystemItem ? ((PsiFileSystemItem)symbol).getParent() : symbol.getContainingFile();
          if (srcfile != null && isAcceptableForImport(node, existingImportFile, srcfile)) {
            QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(symbol, node);
            if (importPath == null) {
              continue;
            }
            if (symbol instanceof PsiFileSystemItem) {
              importPath = importPath.removeTail(1);
            }
            final String symbolImportQName = importPath.append(refText).toString();
            if (!seenCandidateNames.contains(symbolImportQName)) {
              // a new, valid hit
              fix.addImport(symbol, srcfile, importPath, asName);
              seenCandidateNames.add(symbolImportQName);
            }
          }
        }
      }
    }
  }

  private static boolean isAcceptableForImport(PyElement node, PsiFile existingImportFile, PsiFileSystemItem srcfile) {
    return srcfile != existingImportFile && srcfile != node.getContainingFile() &&
        (ImportFromExistingAction.isRoot(srcfile) || PyNames.isIdentifier(FileUtil.getNameWithoutExtension(srcfile.getName()))) &&
         !isShadowedModule(srcfile);
  }

  private static boolean isShadowedModule(PsiFileSystemItem file) {
    if (file.isDirectory() || file.getName().equals(PyNames.INIT_DOT_PY)) {
      return false;
    }
    String name = FileUtil.getNameWithoutExtension(file.getName());
    final PsiDirectory directory = ((PsiFile)file).getContainingDirectory();
    if (directory == null) {
      return false;
    }
    PsiDirectory packageDir = directory.findSubdirectory(name);
    return packageDir != null && packageDir.findFile(PyNames.INIT_DOT_PY) != null;
  }

  private static boolean isQualifier(PyElement node) {
    return node.getParent() instanceof PyReferenceExpression && node == ((PyReferenceExpression)node.getParent()).getQualifier();
  }

  private static boolean isPossibleModuleReference(PyElement node) {
    if (node.getParent() instanceof PyCallExpression && node == ((PyCallExpression) node.getParent()).getCallee()) {
      return false;
    }
    if (node.getParent() instanceof PyArgumentList) {
      final PyArgumentList argumentList = (PyArgumentList)node.getParent();
      if (argumentList.getParent() instanceof PyClass) {
        final PyClass pyClass = (PyClass)argumentList.getParent();
        if (pyClass.getSuperClassExpressionList() == argumentList) {
          return false;
        }
      }
    }
    return true;
  }

  private static Collection<PsiElement> findImportableModules(PsiFile targetFile, String refText, Project project,
                                                              GlobalSearchScope scope) {
    List<PsiElement> result = new ArrayList<>();
    // Add packages
    FilenameIndex.processFilesByName(refText, true, item -> {
      ProgressManager.checkCanceled();
      final PsiDirectory candidatePackageDir = as(item, PsiDirectory.class);
      if (candidatePackageDir != null && candidatePackageDir.findFile(PyNames.INIT_DOT_PY) != null) {
        result.add(candidatePackageDir);
      }
      return true;
    }, scope, project, null);
    // Add modules
    FilenameIndex.processFilesByName(refText + ".py", false, true, item -> {
      ProgressManager.checkCanceled();
      if (isImportableModule(targetFile, item)) {
        result.add(item);
      }
      return true;
    }, scope, project, null);

    return result;
  }

  public static boolean isImportableModule(PsiFile targetFile, @NotNull PsiFileSystemItem file) {
    PsiDirectory parent = (PsiDirectory)file.getParent();
    return parent != null && file != targetFile &&
           (parent.findFile(PyNames.INIT_DOT_PY) != null ||
            ImportFromExistingAction.isRoot(parent) ||
            parent == targetFile.getParent());
  }

  private static boolean isIndexableTopLevel(PsiElement symbol) {
    if (symbol instanceof PsiFileSystemItem) {
      return true;
    }
    if (symbol instanceof PyClass || symbol instanceof PyFunction) {
      return PyUtil.isTopLevel(symbol);
    }
    // only top-level target expressions are included in VariableNameIndex
    return symbol instanceof PyTargetExpression;
  }

  public static boolean isImportable(PsiElement ref_element) {
    PyStatement parentStatement = PsiTreeUtil.getParentOfType(ref_element, PyStatement.class);
    if (parentStatement instanceof PyGlobalStatement || parentStatement instanceof PyNonlocalStatement ||
      parentStatement instanceof PyImportStatementBase) {
      return false;
    }
    return PsiTreeUtil.getParentOfType(ref_element, PyStringLiteralExpression.class, false, PyStatement.class) == null;
  }
}
