// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.inspections.unresolvedReference.PyCommonImportAliasesKt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.stubs.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

public class PyImportCollector {

  private final PyElement myNode;
  private final PsiReference myReference;
  private final String myRefText;
  private final AutoImportQuickFix fix;
  private final Set<String> seenCandidateNames;

  public PyImportCollector(PyElement node, PsiReference reference, String refText) {
    myNode = node;
    myReference = reference;
    myRefText = refText;

    boolean qualify = !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
    fix = new AutoImportQuickFix(node, reference.getClass(), refText, qualify);
    seenCandidateNames = new HashSet<>();
  }

  public AutoImportQuickFix addCandidates() {
    PsiFile existingImportFile = addCandidatesFromExistingImports();
    ProgressManager.checkCanceled(); // before expensive index searches
    addSymbolImportCandidates(existingImportFile);

    for (PyImportCandidateProvider provider : PyImportCandidateProvider.EP_NAME.getExtensionList()) {
      provider.addImportCandidates(myReference, myRefText, fix);
    }
    if (!fix.getCandidates().isEmpty()) {
      fix.sortCandidates();
      return fix;
    }
    return null;
  }

  private PsiFile addCandidatesFromExistingImports() {
    PsiFile existingImportFile = null; // if there's a matching existing import, this is the file it imports
    PsiFile file = myNode.getContainingFile();
    if (file instanceof PyFile pyFile) {
      for (PyImportElement importElement : pyFile.getImportTargets()) {
        existingImportFile = addImportViaElement(existingImportFile, importElement, importElement.resolve());
      }
      existingImportFile = addCandidatesViaFromImports(existingImportFile, pyFile);
    }
    return existingImportFile;
  }

  protected PsiFile addCandidatesViaFromImports(PsiFile existingImportFile, PyFile pyFile) {
    for (PyFromImportStatement fromImportStatement : pyFile.getFromImports()) {
      if (!fromImportStatement.isStarImport() && fromImportStatement.getImportElements().length > 0) {
        PsiElement source = fromImportStatement.resolveImportSource();
        existingImportFile = addImportViaElement(existingImportFile, fromImportStatement.getImportElements()[0], source);
      }
    }
    return existingImportFile;
  }

  private PsiFile addImportViaElement(PsiFile existingImportFile, PyImportElement importElement, PsiElement source) {
    PyFile sourceFile = as(PyUtil.turnDirIntoInit(source), PyFile.class);
    if (sourceFile instanceof PyFileImpl) {

      PsiElement variant = sourceFile.findExportedName(myRefText);
      final String name = variant instanceof PyQualifiedNameOwner ? ((PyQualifiedNameOwner)variant).getQualifiedName() : null;
      if (name != null && seenCandidateNames.contains(name)) {
        return existingImportFile;
      }
      PsiNamedElement definition = as(variant, PsiNamedElement.class);
      // allow importing from this source if it either declares the name itself or represents a higher-level package that reexports the name
      if (definition != null && !(definition instanceof PyFile || definition instanceof PyImportElement) &&
          definition.getContainingFile() != null && PsiTreeUtil.isAncestor(source, definition.getContainingFile(), false)) {
        existingImportFile = sourceFile;
        fix.addImport(definition, sourceFile, importElement);
        if (name != null) {
          seenCandidateNames.add(name);
        }
      }
    }
    return existingImportFile;
  }

  private void addSymbolImportCandidates(PsiFile existingImportFile) {
    Project project = myNode.getProject();
    GlobalSearchScope scope = PySearchUtilBase.defaultSuggestionScope(myNode);
    TypeEvalContext context = TypeEvalContext.codeAnalysis(project, myNode.getContainingFile());

    List<PsiNamedElement> symbols = new ArrayList<>(PyClassNameIndex.find(myRefText, project, scope));
    if (!isQualifier()) {
      symbols.addAll(PyFunctionNameIndex.find(myRefText, project, scope));
    }
    symbols.addAll(PyVariableNameIndex.find(myRefText, project, scope));
    if (PyTypingTypeProvider.isInsideTypeHint(myNode, context)) {
      symbols.addAll(PyTypeAliasNameIndex.find(myRefText, project, scope));
    }
    if (isPossibleModuleReference()) {
      symbols.addAll(findImportableModules(myRefText, false, scope));
      String packageQName = PyCommonImportAliasesKt.PY_COMMON_IMPORT_ALIASES.get(myRefText);
      if (packageQName != null) {
        symbols.addAll(findImportableModules(packageQName, true, scope));
      }
    }
    for (PsiNamedElement symbol : symbols) {
      if (isIndexableTopLevel(symbol)) { // we only want top-level symbols
        PsiFileSystemItem srcfile =
          symbol instanceof PsiFileSystemItem ? ((PsiFileSystemItem)symbol).getParent() : symbol.getContainingFile();
        if (srcfile != null && isAcceptableForImport(existingImportFile, srcfile)) {
          QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(symbol, myNode);
          if (importPath == null) {
            continue;
          }
          if (symbol instanceof PsiFileSystemItem) {
            importPath = importPath.removeTail(1);
          }
          String name = PyUtil.getElementNameWithoutExtension(symbol);
          final String symbolImportQName = importPath.append(name).toString();
          if (seenCandidateNames.add(symbolImportQName)) {
            String alias = name.equals(myRefText) ? null : myRefText;
            fix.addImport(symbol, srcfile, importPath, alias);
          }
        }
      }
    }
  }

  protected PyElement getNode() {
    return myNode;
  }

  private boolean isAcceptableForImport(PsiFile existingImportFile, PsiFileSystemItem srcfile) {
    return srcfile != existingImportFile && srcfile != myNode.getContainingFile() &&
           (PyUtil.isRoot(srcfile) || PyNames.isIdentifier(FileUtilRt.getNameWithoutExtension(srcfile.getName()))) &&
           !isShadowedModule(srcfile);
  }

  private static boolean isShadowedModule(PsiFileSystemItem file) {
    if (file.isDirectory() || file.getName().equals(PyNames.INIT_DOT_PY)) {
      return false;
    }
    String name = FileUtilRt.getNameWithoutExtension(file.getName());
    final PsiDirectory directory = ((PsiFile)file).getContainingDirectory();
    if (directory == null) {
      return false;
    }
    PsiDirectory packageDir = directory.findSubdirectory(name);
    return packageDir != null && packageDir.findFile(PyNames.INIT_DOT_PY) != null;
  }

  private boolean isQualifier() {
    return myNode.getParent() instanceof PyReferenceExpression && myNode == ((PyReferenceExpression)myNode.getParent()).getQualifier();
  }

  private boolean isPossibleModuleReference() {
    final PyCallExpression callExpression = as(myNode.getParent(), PyCallExpression.class);
    if (callExpression != null && myNode == callExpression.getCallee()) {
      final PyDecorator decorator = as(callExpression, PyDecorator.class);
      // getArgumentList() still returns empty (but not null) element in this case
      return decorator != null && !decorator.hasArgumentList();
    }
    if (myNode.getParent() instanceof PyArgumentList argumentList) {
      if (argumentList.getParent() instanceof PyClass pyClass) {
        if (pyClass.getSuperClassExpressionList() == argumentList) {
          return false;
        }
      }
    }
    return true;
  }

  private @NotNull Collection<PsiFileSystemItem> findImportableModules(@NotNull String name,
                                                                       boolean matchQualifiedName,
                                                                       @NotNull GlobalSearchScope scope) {
    List<PsiFileSystemItem> result = new ArrayList<>();
    QualifiedName qualifiedName = QualifiedName.fromDottedString(name);
    List<PyFile> matchingModules = matchQualifiedName ? PyModuleNameIndex.findByQualifiedName(qualifiedName, myNode.getProject(), scope)
                                                      : PyModuleNameIndex.findByShortName(name, myNode.getProject(), scope);
    for (PyFile module : matchingModules) {
      PsiFileSystemItem candidate = as(PyUtil.turnInitIntoDir(module), PsiFileSystemItem.class);
      if (candidate != null && PyUtil.isImportable(myNode.getContainingFile(), candidate)) {
        result.add(candidate);
      }
    }
    return result;
  }

  private static boolean isIndexableTopLevel(PsiElement symbol) {
    if (symbol instanceof PsiFileSystemItem) {
      return true;
    }
    if (symbol instanceof PyClass || symbol instanceof PyFunction) {
      return PyUtil.isTopLevel(symbol);
    }
    // only top-level target expressions and type aliases are included in VariableNameIndex and TypeAliasNameIndex, respectively
    return symbol instanceof PyTargetExpression || symbol instanceof PyTypeAliasStatement;
  }
}
