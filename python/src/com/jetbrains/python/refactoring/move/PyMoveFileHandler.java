package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author vlan
 */
public class PyMoveFileHandler extends MoveFileHandler {
  private static final Key<PsiNamedElement> REFERENCED_ELEMENT = Key.create("PY_REFERENCED_ELEMENT");

  @Override
  public boolean canProcessElement(PsiFile element) {
    return element instanceof PyFile;
  }

  @Override
  public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {
    // TODO: Update relative imports
  }

  @Override
  public List<UsageInfo> findUsages(PsiFile file, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    if (file != null) {
      final List<UsageInfo> usages = PyRefactoringUtil.findUsages(file);
      for (UsageInfo usage : usages) {
        final PsiElement element = usage.getElement();
        if (element != null) {
          element.putCopyableUserData(REFERENCED_ELEMENT, file);
        }
      }
      return usages;
    }
    return null;
  }

  @Override
  public void retargetUsages(List<UsageInfo> usages, Map<PsiElement, PsiElement> oldToNewMap) {
    final Set<PsiFile> updatedFiles = new HashSet<PsiFile>();
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element != null) {
        // TODO: Check if the element still exists/valid
        final PsiNamedElement newElement = element.getCopyableUserData(REFERENCED_ELEMENT);
        element.putCopyableUserData(REFERENCED_ELEMENT, null);
        if (newElement != null) {
          final PsiFile file = element.getContainingFile();
          final PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(usage.getElement(), PyImportStatementBase.class);
          // TODO: Retarget qualified expressions in docstrings
          if (importStmt != null) {
            PyClassRefactoringUtil.updateImportOfElement(importStmt, newElement);
            final PyQualifiedName newElementName = ResolveImportUtil.findCanonicalImportPath(newElement, element);
            replaceWithQualifiedExpression(element, newElementName);
            updatedFiles.add(file);
          }
          else if (element instanceof PyReferenceExpression) {
            final PyQualifiedName newElementName = PyQualifiedName.fromComponents(PyClassRefactoringUtil.getOriginalName(newElement));
            replaceWithQualifiedExpression(element, newElementName);
            PyClassRefactoringUtil.insertImport(element, newElement, null);
            updatedFiles.add(file);
          }
        }
      }
    }
    if (!updatedFiles.isEmpty()) {
      final PyImportOptimizer optimizer = new PyImportOptimizer();
      for (PsiFile file : updatedFiles) {
        optimizer.processFile(file).run();
      }
    }
  }

  private static void replaceWithQualifiedExpression(@NotNull PsiElement oldElement,
                                                     @Nullable PyQualifiedName newElementName) {
    if (newElementName != null && PyClassRefactoringUtil.isValidQualifiedName(newElementName)) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(oldElement.getProject());
      final PsiElement newElement = generator.createExpressionFromText(LanguageLevel.forElement(oldElement), newElementName.toString());
      if (newElement != null) {
        oldElement.replace(newElement);
      }
    }
  }

  @Override
  public void updateMovedFile(PsiFile file) throws IncorrectOperationException {
  }
}
