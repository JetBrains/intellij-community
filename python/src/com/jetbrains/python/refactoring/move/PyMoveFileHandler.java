/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.model.BranchableUsageInfo;
import com.intellij.model.ModelBranch;
import com.intellij.model.ModelBranchUtil;
import com.intellij.openapi.util.Comparing;
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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.actions.CreatePackageAction;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PyMoveFileHandler extends MoveFileHandler {
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
    }
  }

  @Override
  public void updateMovedFile(PsiFile file) throws IncorrectOperationException {
    if (file instanceof PyFile) {
      updateRelativeImportsInModule((PyFile)file);
    }
  }

  private static void updateRelativeImportsInModule(@NotNull PyFile module) {
    final String originalLocation = getOriginalLocation(module);
    if (originalLocation == null) {
      return;
    }
    //module.putUserData(ORIGINAL_FILE_LOCATION, null);
    for (PyFromImportStatement statement : module.getFromImports()) {
      if (!canBeRelative(statement)) {
        continue;
      }
      final int relativeLevel = Math.max(statement.getRelativeLevel(), 1);
      final PsiFileSystemItem sourceElement = resolveRelativeImportFromModuleLocation(module,
                                                                                      originalLocation,
                                                                                      statement.getImportSource(),
                                                                                      relativeLevel);
      if (sourceElement == null) {
        continue;
      }
      final QualifiedName newName = QualifiedNameFinder.findShortestImportableQName(sourceElement);
      replaceRelativeImportSourceWithQualifiedExpression(statement, newName);
    }

    for (PyImportElement importElement : module.getImportTargets()) {
      final PyReferenceExpression referenceExpr = importElement.getImportReferenceExpression();
      if (!canBeRelative(importElement) || referenceExpr == null) {
        continue;
      }
      final PsiFileSystemItem resolved = resolveRelativeImportFromModuleLocation(module, originalLocation, referenceExpr, 1);
      if (resolved == null) {
        continue;
      }
      final QualifiedName newName = QualifiedNameFinder.findShortestImportableQName(resolved);
      replaceWithQualifiedExpression(referenceExpr, newName);
      final QualifiedName oldQualifiedName = referenceExpr.asQualifiedName();
      if (!Comparing.equal(oldQualifiedName, newName)) {
        final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(importElement);
        if (scopeOwner == null) {
          continue;
        }
        scopeOwner.accept(new PyRecursiveElementVisitor() {
          @Override
          public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
            if (Comparing.equal(node.asQualifiedName(), oldQualifiedName)) {
              replaceWithQualifiedExpression(node, newName);
            }
            else {
              super.visitPyReferenceExpression(node);
            }
          }
        });
      }
    }
  }

  private static boolean canBeRelative(@NotNull PyFromImportStatement statement) {
    return LanguageLevel.forElement(statement).isPython2() || statement.getRelativeLevel() > 0;
  }


  private static boolean canBeRelative(@NotNull PyImportElement statement) {
    return LanguageLevel.forElement(statement).isPython2();
  }

  /**
   * @param referenceExpr is null if we resolve import of type "from .. import bar", and "foo" for import of type "from foo import bar"
   */
  @Nullable
  private static PsiFileSystemItem resolveRelativeImportFromModuleLocation(@NotNull PyFile context,
                                                                           @NotNull String moduleLocation,
                                                                           @Nullable PyReferenceExpression referenceExpr,
                                                                           int relativeLevel) {
    String relativeImportBasePath = VirtualFileManager.extractPath(moduleLocation);
    for (int level = 0; level < relativeLevel; level++) {
      relativeImportBasePath = PathUtil.getParentPath(relativeImportBasePath);
    }
    if (!relativeImportBasePath.isEmpty()) {
      final String protocol = VirtualFileManager.extractProtocol(moduleLocation);
      assert protocol != null : "Original location: " + moduleLocation;
      final String relativeImportBaseUrl = VirtualFileManager.constructUrl(protocol, relativeImportBasePath);
      ModelBranch branch = ModelBranch.getPsiBranch(context);
      VirtualFile relativeImportBaseDir = branch != null
                                          ? branch.findFileByUrl(relativeImportBaseUrl)
                                          : VirtualFileManager.getInstance().findFileByUrl(relativeImportBaseUrl);
      VirtualFile sourceFile = relativeImportBaseDir;
      if (relativeImportBaseDir != null && relativeImportBaseDir.isDirectory() && referenceExpr != null) {
        final QualifiedName qualifiedName = referenceExpr.asQualifiedName();
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
        final PsiFileSystemItem sourceElement;
        if (sourceFile.isDirectory()) {
          sourceElement = context.getManager().findDirectory(sourceFile);
        }
        else {
          sourceElement = context.getManager().findFile(sourceFile);
        }
        return sourceElement;
      }
    }
    return null;
  }

  private static boolean probablyNamespacePackage(@NotNull PsiFile anchor, @NotNull PsiDirectory destination, @NotNull PsiDirectory root) {
    if (LanguageLevel.forElement(anchor).isPython2()) {
      return false;
    }
    while (destination != null && destination != root) {
      if (destination.findFile(PyNames.INIT_DOT_PY) != null) {
        return false;
      }
      destination = destination.getParent();
    }
    return true;
  }

  @Override
  public List<UsageInfo> findUsages(PsiFile file, PsiDirectory newParent, boolean searchInComments, boolean searchInNonJavaFiles) {
    if (file != null) {
      file.putUserData(ORIGINAL_FILE_LOCATION, file.getVirtualFile().getUrl());
      final List<UsageInfo> usages = PyPsiIndexUtil.findUsages(file, false);
      return ContainerUtil.map(usages, usage -> {
        final PsiElement element = usage.getElement();
        if (element != null) {
          return new PyUsageInfo(element, file);
        }
        return usage;
      });
    }
    return null;
  }

  static final class PyUsageInfo extends UsageInfo implements BranchableUsageInfo {
    private final @NotNull PsiFile myFile;

    PyUsageInfo(@NotNull PsiElement element, @NotNull PsiFile file) {
      super(element);
      myFile = file;
    }

    @Override
    public @NotNull UsageInfo obtainBranchCopy(@NotNull ModelBranch branch) {
      return new PyUsageInfo(branch.obtainPsiCopy(Objects.requireNonNull(getElement())),
                             branch.obtainPsiCopy(myFile));
    }
  }

  @Override
  public void retargetUsages(List<UsageInfo> usages, Map<PsiElement, PsiElement> oldToNewMap) {
    final Set<PsiFile> updatedFiles = new HashSet<>();
    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement != null && usage instanceof PyUsageInfo) {
        final PsiNamedElement movedElement = ((PyUsageInfo)usage).myFile;
        final PsiFile usageFile = usageElement.getContainingFile();

        final PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(usageElement, PyImportStatementBase.class);
        // TODO: Retarget qualified expressions in docstrings
        if (importStmt != null) {

          if (getOriginalLocation(usageFile) != null) {
            // Leave relative imports as they are after #updateRelativeImportsInModule
            final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(usageFile.getProject(), usageFile);
            final PyResolveContext resolveContext = PyResolveContext.implicitContext(typeEvalContext);
            if (ContainerUtil.getFirstItem(PyUtil.multiResolveTopPriority(usageElement, resolveContext)) == movedElement) {
              continue;
            }
          }

          updatedFiles.add(usageFile);
          final boolean usageInsideImportElement = PsiTreeUtil.getParentOfType(usageElement, PyImportElement.class) != null;
          if (usageInsideImportElement) {
            // Handles imported element in "from import" statement (from some.package import module)
            // or simple unqualified import of the module (import module).
            if (PyClassRefactoringUtil.updateUnqualifiedImportOfElement(importStmt, movedElement)) {
              continue;
            }
          }
          final QualifiedName newElementName = QualifiedNameFinder.findCanonicalImportPath(movedElement, usageElement);
          if (importStmt instanceof PyFromImportStatement) {
            if (!usageInsideImportElement) {
              replaceRelativeImportSourceWithQualifiedExpression((PyFromImportStatement)importStmt, newElementName);
            }
          }
          else {
            replaceWithQualifiedExpression(usageElement, newElementName);
          }
        }
        else if (usageElement instanceof PyReferenceExpression) {
          updatedFiles.add(usageFile);
          if (((PyReferenceExpression)usageElement).isQualified()) {
            final QualifiedName newQualifiedName = QualifiedNameFinder.findCanonicalImportPath(movedElement, usageElement);
            replaceWithQualifiedExpression(usageElement, newQualifiedName);
          }
          else {
            final QualifiedName newName = QualifiedName.fromComponents(PyPsiRefactoringUtil.getOriginalName(movedElement));
            replaceWithQualifiedExpression(usageElement, newName);
          }
        }
      }
    }
    if (!updatedFiles.isEmpty()) {
      final PyImportOptimizer optimizer = PyImportOptimizer.onlyRemoveUnused();
      for (PsiFile file : updatedFiles) {
        final boolean injectedFragment = InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
        if (!injectedFragment) {
          optimizer.processFile(file).run();
        }
      }
    }
  }

  @Nullable
  private static String getOriginalLocation(PsiFile file) {
    file = ModelBranchUtil.findOriginalPsi(file);
    return file == null ? null : file.getUserData(ORIGINAL_FILE_LOCATION);
  }

  @NotNull
  private static PsiElement replaceWithQualifiedExpression(@NotNull PsiElement oldElement, @Nullable QualifiedName newElementName) {
    if (newElementName != null && PyPsiRefactoringUtil.isValidQualifiedName(newElementName)) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(oldElement.getProject());
      final PsiElement newElement = generator.createExpressionFromText(LanguageLevel.forElement(oldElement), newElementName.toString());
      return oldElement.replace(newElement);
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
   * @see #replaceWithQualifiedExpression(PsiElement, QualifiedName)
   */
  @NotNull
  private static PsiElement replaceRelativeImportSourceWithQualifiedExpression(@NotNull PyFromImportStatement importStatement,
                                                                               @Nullable QualifiedName qualifiedName) {
    final Couple<PsiElement> range = getRelativeImportSourceRange(importStatement);
    if (range != null && qualifiedName != null) {
      if (range.getFirst() != range.getSecond()) {
        importStatement.deleteChildRange(range.getFirst().getNextSibling(), range.getSecond());
      }
      replaceWithQualifiedExpression(range.getFirst(), qualifiedName);
    }
    return importStatement;
  }

  @Nullable
  private static Couple<PsiElement> getRelativeImportSourceRange(@NotNull PyFromImportStatement statement) {
    final PsiElement fromKeyword = statement.getFirstChild();
    assert fromKeyword.getNode().getElementType() == PyTokenTypes.FROM_KEYWORD;
    final PsiElement elementAfterFrom = PsiTreeUtil.skipWhitespacesForward(fromKeyword);
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
        next = PsiTreeUtil.skipWhitespacesForward(next);
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
}
