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
package com.jetbrains.python.refactoring.move;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.actions.CreatePackageAction;
import com.jetbrains.python.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.psi.*;
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
  private static final Key<String> ORIGINAL_FILE_LOCATION = Key.create("PY_ORIGINAL_FILE_LOCATION");

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
      if (moveDestination != root && root != null && searchForReferences && !probablyNamespacePackage(file, moveDestination, root)) {
        CreatePackageAction.createInitPyInHierarchy(moveDestination, root);
      }
      if (file instanceof PyFile) {
        updateRelativeImportsInModule((PyFile)file);
      }
    }
  }

  private static void updateRelativeImportsInModule(@NotNull PyFile module) {
    final String originalLocation = module.getUserData(ORIGINAL_FILE_LOCATION);
    if (originalLocation == null) {
      return;
    }
    module.putUserData(ORIGINAL_FILE_LOCATION, null);
    for (PyFromImportStatement statement : module.getFromImports()) {
      if (statement.getRelativeLevel() == 0) {
        continue;
      }
      final PsiFileSystemItem sourceElement = resolveRelativeImportSourceFromModuleLocation(originalLocation, statement);
      if (sourceElement == null) {
        continue;
      }
      final QualifiedName newName = QualifiedNameFinder.findShortestImportableQName(sourceElement);
      replaceRelativeImportSourceWithQualifiedExpression(statement, newName);
    }
  }

  @Nullable
  private static PsiFileSystemItem resolveRelativeImportSourceFromModuleLocation(@NotNull String moduleLocation,
                                                                                 @NotNull PyFromImportStatement statement) {
    String relativeImportBasePath = VirtualFileManager.extractPath(moduleLocation);
    for (int level = 0; level < statement.getRelativeLevel(); level++) {
      relativeImportBasePath = PathUtil.getParentPath(relativeImportBasePath);
    }
    if (!relativeImportBasePath.isEmpty()) {
      final String protocol = VirtualFileManager.extractProtocol(moduleLocation);
      assert protocol != null : "Original location: " + moduleLocation;
      final String relativeImportBaseUrl = VirtualFileManager.constructUrl(protocol, relativeImportBasePath);
      final VirtualFile relativeImportBaseDir = VirtualFileManager.getInstance().findFileByUrl(relativeImportBaseUrl);
      VirtualFile sourceFile = relativeImportBaseDir;
      if (relativeImportBaseDir != null && relativeImportBaseDir.exists() && statement.getImportSource() != null) {
        final QualifiedName qualifiedName = statement.getImportSource().asQualifiedName();
        if (qualifiedName == null) {
          return null;
        }
        final String relativePath = qualifiedName.join("/");
        sourceFile = relativeImportBaseDir.findFileByRelativePath(relativePath);
        if (sourceFile == null) {
          sourceFile = relativeImportBaseDir.findFileByRelativePath(relativePath + PyNames.DOT_PY);
        }
      }
      if (sourceFile != null) {
        final PsiManager psiManager = statement.getManager();
        final PsiFileSystemItem sourceElement;
        if (sourceFile.isDirectory()) {
          sourceElement = psiManager.findDirectory(sourceFile);
        }
        else {
          sourceElement = psiManager.findFile(sourceFile);
        }
        return sourceElement;
      }
    }
    return null;
  }

  private static boolean probablyNamespacePackage(@NotNull PsiFile anchor, @NotNull PsiDirectory destination, @NotNull PsiDirectory root) {
    if (!LanguageLevel.forElement(anchor).isAtLeast(LanguageLevel.PYTHON33)) {
      return false;
    }
    while (destination != null && destination != root) {
      if (destination.findFile(PyNames.INIT_DOT_PY) != null) {
        return false;
      }
      //noinspection ConstantConditions
      destination = destination.getParent();
    }
    return true;
  }

  @Override
  public List<UsageInfo> findUsages(PsiFile file, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    if (file != null) {
      file.putUserData(ORIGINAL_FILE_LOCATION, file.getVirtualFile().getUrl());
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
            if (importStmt instanceof PyFromImportStatement) {
              replaceRelativeImportSourceWithQualifiedExpression((PyFromImportStatement)importStmt, newElementName);
            }
            else {
              replaceWithQualifiedExpression(element, newElementName);
            }
          }
          else if (element instanceof PyReferenceExpression) {
            updatedFiles.add(file);
            if (((PyReferenceExpression)element).isQualified()) {
              final QualifiedName newQualifiedName = QualifiedNameFinder.findCanonicalImportPath(newElement, element);
              replaceWithQualifiedExpression(element, newQualifiedName);
            }
            else {
              final QualifiedName newName = QualifiedName.fromComponents(PyClassRefactoringUtil.getOriginalName(newElement));
              final PsiElement replaced = replaceWithQualifiedExpression(element, newName);
              PyClassRefactoringUtil.insertImport(replaced, newElement, null);
            }
          }
        }
      }
    }
    if (!updatedFiles.isEmpty()) {
      final PyImportOptimizer optimizer = new PyImportOptimizer();
      for (PsiFile file : updatedFiles) {
        final boolean injectedFragment = InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
        if (!injectedFragment) {
          optimizer.processFile(file).run();
        }
      }
    }
  }

  @NotNull
  private static PsiElement replaceWithQualifiedExpression(@NotNull PsiElement oldElement, @Nullable QualifiedName newElementName) {
    if (newElementName != null && PyClassRefactoringUtil.isValidQualifiedName(newElementName)) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(oldElement.getProject());
      final PsiElement newElement = generator.createExpressionFromText(LanguageLevel.forElement(oldElement), newElementName.toString());
      if (newElement != null) {
        return oldElement.replace(newElement);
      }
    }
    return oldElement;
  }

  /**
   * Replace import source with leading dots (if any) with reference expression created from given qualified name.
   * Basically it does the same thing as {@link #replaceWithQualifiedExpression}, but also removes leading dots.
   *
   * @param importStatement import statement to update
   * @param qualifiedName   qualified name of new import source
   * @return updated import statement
   * @see #replaceWithQualifiedExpression(com.intellij.psi.PsiElement, com.intellij.psi.util.QualifiedName)
   */
  @NotNull
  private static PsiElement replaceRelativeImportSourceWithQualifiedExpression(@NotNull PyFromImportStatement importStatement,
                                                                               @Nullable QualifiedName qualifiedName) {
    final Couple<PsiElement> range = getRelativeImportSourceRange(importStatement);
    if (range != null && qualifiedName != null) {
      if (range.getFirst() == range.getSecond()) {
        replaceWithQualifiedExpression(range.getFirst(), qualifiedName);
      }
      else {
        importStatement.deleteChildRange(range.getFirst().getNextSibling(), range.getSecond());
        replaceWithQualifiedExpression(range.getFirst(), qualifiedName);
      }
    }
    return importStatement;
  }

  @Nullable
  private static Couple<PsiElement> getRelativeImportSourceRange(@NotNull PyFromImportStatement statement) {
    final PsiElement fromKeyword = statement.getFirstChild();
    assert fromKeyword.getNode().getElementType() == PyTokenTypes.FROM_KEYWORD;
    final PsiElement elementAfterFrom = PsiTreeUtil.skipSiblingsForward(fromKeyword, PsiWhiteSpace.class);
    if (elementAfterFrom == null) {
      return null;
    }
    else if (elementAfterFrom instanceof PyReferenceExpression) {
      return Couple.of(elementAfterFrom, elementAfterFrom);
    }
    else if (elementAfterFrom.getNode().getElementType() == PyTokenTypes.DOT) {
      PsiElement lastDot;
      PsiElement next = elementAfterFrom;
      do {
        lastDot = next;
        next = PsiTreeUtil.skipSiblingsForward(next, PsiWhiteSpace.class);
      }
      while (next != null && next.getNode().getElementType() == PyTokenTypes.DOT);
      if (next instanceof PyReferenceExpression) {
        return Couple.of(elementAfterFrom, next);
      }
      else {
        return Couple.of(elementAfterFrom, lastDot);
      }
    }
    return null;
  }

  @Override
  public void updateMovedFile(PsiFile file) throws IncorrectOperationException {
  }
}
