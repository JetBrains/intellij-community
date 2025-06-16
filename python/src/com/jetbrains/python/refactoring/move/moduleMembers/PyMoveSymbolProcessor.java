// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.move.moduleMembers;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.QualifiedName;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyDunderAllReference;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.formatter.PyTrailingBlankLinesPostFormatProcessor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.move.PyMoveRefactoringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.python.psi.impl.PyImportStatementNavigator.getImportStatementByElement;

/**
 * @author Mikhail Golubev
 */
public class PyMoveSymbolProcessor {
  private final PsiNamedElement myMovedElement;
  private final PyFile myDestinationFile;
  private final List<UsageInfo> myUsages;
  private final List<? extends SmartPsiElementPointer<PsiNamedElement>> myAllMovedElements;
  private final List<PsiFile> myFilesWithStarUsages = new ArrayList<>();
  private final Set<ScopeOwner> myScopeOwnersWithGlobal = new HashSet<>();

  public PyMoveSymbolProcessor(final @NotNull PsiNamedElement element,
                               @NotNull PyFile destination,
                               @NotNull Collection<UsageInfo> usages,
                               @NotNull List<? extends SmartPsiElementPointer<PsiNamedElement>> otherElements) {
    myMovedElement = element;
    myDestinationFile = destination;
    myAllMovedElements = otherElements;
    myUsages = ContainerUtil.sorted(usages, (u1, u2) -> PsiUtilCore.compareElementsByPosition(u1.getElement(), u2.getElement()));
  }

  public final @NotNull PyMoveSymbolResult moveElement() {
    final PsiElement oldElementBody = PyMoveModuleMembersHelper.expandNamedElementBody(myMovedElement);
    if (oldElementBody != null) {
      PyClassRefactoringUtil.rememberNamedReferences(oldElementBody);
      final PsiElement newElementBody = addElementToFile(oldElementBody);
      final PsiNamedElement newElement = PyMoveModuleMembersHelper.extractNamedElement(newElementBody);
      assert newElement != null;
      for (UsageInfo usage : myUsages) {
        final PsiElement usageElement = usage.getElement();
        if (usageElement != null) {
          updateSingleUsage(usageElement, newElement);
        }
      }
      final PsiElement[] unwrappedElements =
        ContainerUtil.mapNotNull(myAllMovedElements, SmartPsiElementPointer::getElement).toArray(PsiElement.EMPTY_ARRAY);
      PyClassRefactoringUtil.restoreNamedReferences(newElementBody, myMovedElement, unwrappedElements);
      final PyTrailingBlankLinesPostFormatProcessor postFormatProcessor = new PyTrailingBlankLinesPostFormatProcessor();
      if (PsiTreeUtil.nextVisibleLeaf(newElementBody) == null) {
        PyUtil.updateDocumentUnblockedAndCommitted(myDestinationFile, document -> {
          PsiDocumentManager.getInstance(newElementBody.getProject()).commitDocument(document);
          postFormatProcessor.processElement(newElementBody, CodeStyle.getSettings(myDestinationFile));
        });
      }
      deleteElement();
    }
    return new PyMoveSymbolResult(myFilesWithStarUsages);
  }

  private void deleteElement() {
    final PsiElement elementBody = PyMoveModuleMembersHelper.expandNamedElementBody(myMovedElement);
    assert elementBody != null;
    elementBody.delete();
  }

  private @NotNull PsiElement addElementToFile(@NotNull PsiElement element) {
    final PsiElement anchor = PyMoveRefactoringUtil.findLowestPossibleTopLevelInsertionPosition(myUsages, myDestinationFile);
    return myDestinationFile.addBefore(element, anchor);
  }

  private void updateSingleUsage(@NotNull PsiElement usage, @NotNull PsiNamedElement newElement) {
    PsiFile psiFile = usage.getContainingFile();
    final PsiFile usageFile = psiFile;
    if (belongsToSomeMovedElement(usage)) {
      return;
    }
    if (usage instanceof PyQualifiedExpression qualifiedExpr) {
      if (myMovedElement instanceof PyClass && PyNames.INIT.equals(qualifiedExpr.getName())) {
        return;
      }
      else if (qualifiedExpr.isQualified()) {
        insertQualifiedImportAndReplaceReference(newElement, qualifiedExpr);
      }
      else if (usageFile == myMovedElement.getContainingFile()) {
        if (usage.getParent() instanceof PyGlobalStatement) {
          myScopeOwnersWithGlobal.add(ScopeUtil.getScopeOwner(usage));
          if (((PyGlobalStatement)usage.getParent()).getGlobals().length == 1) {
            usage.getParent().delete();
          }
          else {
            usage.delete();
          }
        }
        else if (myScopeOwnersWithGlobal.contains(ScopeUtil.getScopeOwner(usage))) {
          insertQualifiedImportAndReplaceReference(newElement, qualifiedExpr);
        }
        else {
          insertImportFromAndReplaceReference(newElement, qualifiedExpr);
        }
      }
      else {
        final PyImportStatementBase importStmt = getImportStatementByElement(usage);
        if (importStmt != null) {
          PyClassRefactoringUtil.updateUnqualifiedImportOfElement(importStmt, newElement);
        }
        else if (resolvesToLocalStarImport(usage)) {
          PyPsiRefactoringUtil.insertImport(usage, newElement);
          myFilesWithStarUsages.add(usageFile);
        }
      }
    }
    else if (usage instanceof PyStringLiteralExpression) {
      for (PsiReference ref : usage.getReferences()) {
        if (ref instanceof PyDunderAllReference) {
          PsiFile newFile = newElement.getContainingFile();
          if (myMovedElement.getContainingFile().getName().equals(PyNames.INIT_DOT_PY)) {
            // Update import statement in `__init__.py` if symbol is moved from there to another module
            VirtualFile targetFile = newFile.getVirtualFile();
            VirtualFile currentFile = psiFile.getVirtualFile();
            if (targetFile != null && currentFile != null) {
              VirtualFile currentDir = currentFile.getParent();
              if (currentDir != null && !VfsUtilCore.isAncestor(currentDir, targetFile, true)) {
                usage.delete();
              }
              PyPsiRefactoringUtil.insertImport(usage, newElement);
            }
          }
          else if (ref.getElement().getContainingFile().getName().equals(PyNames.INIT_DOT_PY)) {
            // Remove old import statement in `__init__.py` if symbol is moved from some ancestor module to another one
            VirtualFile targetFile = newFile.getVirtualFile();
            VirtualFile currentFile = psiFile.getVirtualFile();
            if (targetFile != null && currentFile != null) {
              VirtualFile currentDir = currentFile.getParent();
              if (currentDir != null && !VfsUtilCore.isAncestor(currentDir, targetFile, true)) {
                usage.delete();
              }
            }
          }
          else {
            usage.delete();
          }
        }
        else {
          if (ref.isReferenceTo(myMovedElement)) {
            ref.bindToElement(newElement);
          }
        }
      }
    }
  }

  private boolean belongsToSomeMovedElement(final @NotNull PsiElement element) {
    return StreamEx.of(myAllMovedElements).
      map(SmartPsiElementPointer::getElement)
      .nonNull()
      .map(PyMoveModuleMembersHelper::expandNamedElementBody)
      .anyMatch(moved -> PsiTreeUtil.isAncestor(moved, element, false));
  }


  /**
   * <pre>{@code
   *   print(foo.bar)
   * }</pre>
   * is transformed to
   * <pre>{@code
   *   from new import bar
   *   print(bar)
   * }</pre>
   */
  private static void insertImportFromAndReplaceReference(@NotNull PsiNamedElement targetElement,
                                                          @NotNull PyQualifiedExpression expression) {
    PyPsiRefactoringUtil.insertImport(expression, targetElement, null, true);
    final PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
    final PyExpression generated = generator.createExpressionFromText(LanguageLevel.forElement(expression), expression.getReferencedName());
    expression.replace(generated);
  }

  /**
   * <pre>{@code
   *   print(foo.bar)
   * }</pre>
   * is transformed to
   * <pre>{@code
   *   import new
   *   print(new.bar)
   * }</pre>
   */
  private static void insertQualifiedImportAndReplaceReference(@NotNull PsiNamedElement targetElement,
                                                               @NotNull PyQualifiedExpression expression) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
    final PsiFile srcFile = targetElement.getContainingFile();
    final LanguageLevel languageLevel = LanguageLevel.forElement(expression);
    if (srcFile != expression.getContainingFile()) {
      final QualifiedName qualifier = QualifiedNameFinder.findCanonicalImportPath(srcFile, expression);
      PyPsiRefactoringUtil.insertImport(expression, srcFile, null, false);
      final String newQualifiedReference = qualifier + "." + expression.getReferencedName();
      expression.replace(generator.createExpressionFromText(languageLevel, newQualifiedReference));
    }
    else {
      expression.replace(generator.createExpressionFromText(languageLevel, expression.getReferencedName()));
    }
  }

  private static boolean resolvesToLocalStarImport(@NotNull PsiElement usage) {
    // Don't use PyUtil#multiResolveTopPriority here since it filters out low priority ImportedResolveResults
    final List<PsiElement> resolvedElements = new ArrayList<>();
    if (usage instanceof PyReferenceOwner) {
      final var context = TypeEvalContext.codeInsightFallback(usage.getProject());
      final PsiPolyVariantReference reference = ((PyReferenceOwner)usage).getReference(PyResolveContext.implicitContext(context));
      for (ResolveResult result : reference.multiResolve(false)) {
        resolvedElements.add(result.getElement());
      }
    }
    else {
      final PsiReference ref = usage.getReference();
      if (ref != null) {
        resolvedElements.add(ref.resolve());
      }
    }
    final PsiFile containingFile = usage.getContainingFile();
    if (containingFile != null) {
      for (PsiElement resolved : resolvedElements) {
        if (resolved instanceof PyStarImportElement && resolved.getContainingFile() == containingFile) {
          return true;
        }
      }
    }
    return false;
  }
}
