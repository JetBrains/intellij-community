package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.util.Condition;
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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.impl.PyImportStatementNavigator.getImportStatementByElement;

/**
 * @author Mikhail Golubev
 */
public class PyMoveSymbolProcessor {
  private final PsiNamedElement myMovedElement;
  private final PyFile myDestinationFile;
  private final List<UsageInfo> myUsages;
  private final PsiElement[] myAllMovedElements;
  private final List<PsiFile> myOptimizeImportTargets = new ArrayList<>();
  private final Set<ScopeOwner> myScopeOwnersWithGlobal = new HashSet<>();

  public PyMoveSymbolProcessor(@NotNull final PsiNamedElement element,
                               @NotNull PyFile destination,
                               @NotNull Collection<UsageInfo> usages,
                               @NotNull PsiElement[] otherElements) {
    myMovedElement = element;
    myDestinationFile = destination;
    myAllMovedElements = otherElements;
    myUsages = ContainerUtil.sorted(usages, (u1, u2) -> PsiUtilCore.compareElementsByPosition(u1.getElement(), u2.getElement()));
  }

  public final void moveElement() {
    final PsiElement oldElementBody = PyMoveModuleMembersHelper.expandNamedElementBody(myMovedElement);
    final PsiFile sourceFile = myMovedElement.getContainingFile();
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
      PyClassRefactoringUtil.restoreNamedReferences(newElementBody, myMovedElement, myAllMovedElements);
      deleteElement();
      optimizeImports(sourceFile);
    }
  }

  private void deleteElement() {
    final PsiElement elementBody = PyMoveModuleMembersHelper.expandNamedElementBody(myMovedElement);
    assert elementBody != null;
    elementBody.delete();
  }

  private void optimizeImports(@Nullable PsiFile originalFile) {
    for (PsiFile usageFile : myOptimizeImportTargets) {
      PyClassRefactoringUtil.optimizeImports(usageFile);
    }
    if (originalFile != null) {
      PyClassRefactoringUtil.optimizeImports(originalFile);
    }
  }

  @NotNull
  private PsiElement addElementToFile(@NotNull PsiElement element) {
    final PsiElement firstUsage = findFirstTopLevelWithUsageAtDestination();
    if (firstUsage != null) {
      return myDestinationFile.addBefore(element, firstUsage);
    }
    else {
      return myDestinationFile.add(element);
    }
  }

  @Nullable
  private PsiElement findFirstTopLevelWithUsageAtDestination() {
    final List<PsiElement> topLevelAtDestination = ContainerUtil.mapNotNull(myUsages, usage -> {
      final PsiElement element = usage.getElement();
      if (element != null && ScopeUtil.getScopeOwner(element) == myDestinationFile && getImportStatementByElement(element) == null) {
        return findTopLevelParent(element);
      }
      return null;
    });
    if (topLevelAtDestination.isEmpty()) {
      return null;
    }
    return Collections.min(topLevelAtDestination, (e1, e2) -> PsiUtilCore.compareElementsByPosition(e1, e2));
  }

  @Nullable
  private PsiElement findTopLevelParent(@NotNull PsiElement element) {
    return PsiTreeUtil.findFirstParent(element, element1 -> element1.getParent() == myDestinationFile);
  }

  private void updateSingleUsage(@NotNull PsiElement usage, @NotNull PsiNamedElement newElement) {
    final PsiFile usageFile = usage.getContainingFile();
    if (belongsToSomeMovedElement(usage)) {
      return;
    }
    if (usage instanceof PyQualifiedExpression) {
      final PyQualifiedExpression qualifiedExpr = (PyQualifiedExpression)usage;
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
          PyClassRefactoringUtil.insertImport(usage, newElement);
          myOptimizeImportTargets.add(usageFile);
        }
      }
    }
    else if (usage instanceof PyStringLiteralExpression) {
      for (PsiReference ref : usage.getReferences()) {
        if (ref instanceof PyDunderAllReference) {
          usage.delete();
        }
        else {
          if (ref.isReferenceTo(myMovedElement)) {
            ref.bindToElement(newElement);
          }
        }
      }
    }
  }

  private boolean belongsToSomeMovedElement(@NotNull final PsiElement element) {
    return ContainerUtil.exists(myAllMovedElements, movedElement -> {
      final PsiElement movedElementBody = PyMoveModuleMembersHelper.expandNamedElementBody((PsiNamedElement)movedElement);
      return PsiTreeUtil.isAncestor(movedElementBody, element, false);
    });
  }


  /**
   * <pre><code>
   *   print(foo.bar)
   * </code></pre>
   * is transformed to
   * <pre><code>
   *   from new import bar
   *   print(bar)
   * </code></pre>
   */
  private static void insertImportFromAndReplaceReference(@NotNull PsiNamedElement targetElement,
                                                          @NotNull PyQualifiedExpression expression) {
    PyClassRefactoringUtil.insertImport(expression, targetElement, null, true);
    final PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
    final PyExpression generated = generator.createExpressionFromText(LanguageLevel.forElement(expression), expression.getReferencedName());
    expression.replace(generated);
  }

  /**
   * <pre><code>
   *   print(foo.bar)
   * </code></pre>
   * is transformed to
   * <pre><code>
   *   import new
   *   print(new.bar)
   * </code></pre>
   */
  private static void insertQualifiedImportAndReplaceReference(@NotNull PsiNamedElement targetElement,
                                                               @NotNull PyQualifiedExpression expression) {
    final PsiFile file = targetElement.getContainingFile();
    final QualifiedName qualifier = QualifiedNameFinder.findCanonicalImportPath(file, expression);
    PyClassRefactoringUtil.insertImport(expression, file, null, false);
    final PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
    final PyExpression generated = generator.createExpressionFromText(LanguageLevel.forElement(expression),
                                                                      qualifier + "." + expression.getReferencedName());
    expression.replace(generated);
  }

  private static boolean resolvesToLocalStarImport(@NotNull PsiElement usage) {
    // Don't use PyUtil#multiResolveTopPriority here since it filters out low priority ImportedResolveResults
    final List<PsiElement> resolvedElements = new ArrayList<>();
    if (usage instanceof PyReferenceOwner) {
      final PsiPolyVariantReference reference = ((PyReferenceOwner)usage).getReference(PyResolveContext.defaultContext());
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
