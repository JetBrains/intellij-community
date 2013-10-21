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
package com.jetbrains.python.refactoring.move;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.actions.CreatePackageAction;
import com.jetbrains.python.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author vlan
 */
public class PyMoveFileHandler extends MoveFileHandler {
  private static final Key<PsiNamedElement> REFERENCED_ELEMENT = Key.create("PY_REFERENCED_ELEMENT");

  @Override
  public boolean canProcessElement(PsiFile element) {
    return element.getFileType() == PythonFileType.INSTANCE;
  }

  @Override
  public void prepareMovedFile(PsiFile file, PsiDirectory moveDestination, Map<PsiElement, PsiElement> oldToNewMap) {
    if (file != null) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        PyUtil.deletePycFiles(virtualFile.getPath());
      }
      final Collection<VirtualFile> roots = PyUtil.getSourceRoots(file);
      PsiDirectory root = moveDestination;
      while (root != null && !roots.contains(root.getVirtualFile())) {
        root = root.getParentDirectory();
      }
      final boolean searchForReferences = RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE;
      if (moveDestination != root && root != null && searchForReferences) {
        CreatePackageAction.createInitPyInHierarchy(moveDestination, root);
      }
    }
    // TODO: Update relative imports
  }

  @Override
  public List<UsageInfo> findUsages(PsiFile file, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    if (file != null) {
      final List<UsageInfo> usages = PyRefactoringUtil.findUsages(file, false);
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
        final PsiNamedElement newElement = element.getCopyableUserData(REFERENCED_ELEMENT);
        element.putCopyableUserData(REFERENCED_ELEMENT, null);
        if (newElement != null) {
          final PsiFile file = element.getContainingFile();
          final PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class);
          // TODO: Retarget qualified expressions in docstrings
          if (importStmt != null) {
            updatedFiles.add(file);
            PyClassRefactoringUtil.updateImportOfElement(importStmt, newElement);
            if (importStmt instanceof PyFromImportStatement && PsiTreeUtil.getParentOfType(element, PyImportElement.class) != null) {
              continue;
            }
            final QualifiedName newElementName = QualifiedNameFinder.findCanonicalImportPath(newElement, element);
            replaceWithQualifiedExpression(element, newElementName);
          }
          else if (element instanceof PyReferenceExpression) {
            updatedFiles.add(file);
            if (((PyReferenceExpression)element).getQualifier() != null) {
              final QualifiedName newQualifiedName = QualifiedNameFinder.findCanonicalImportPath(newElement, element);
              replaceWithQualifiedExpression(element, newQualifiedName);
            } else {
              final QualifiedName newName = QualifiedName.fromComponents(PyClassRefactoringUtil.getOriginalName(newElement));
              replaceWithQualifiedExpression(element, newName);
              PyClassRefactoringUtil.insertImport(element, newElement, null);
            }
          }
        }
      }
    }
    if (!updatedFiles.isEmpty()) {
      final PyImportOptimizer optimizer = new PyImportOptimizer();
      for (PsiFile file : updatedFiles) {
        final boolean injectedFragment = InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
        if (!injectedFragment)
          optimizer.processFile(file).run();
      }
    }
  }

  private static void replaceWithQualifiedExpression(@NotNull PsiElement oldElement,
                                                     @Nullable QualifiedName newElementName) {
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
