// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.QualifiedName;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Mikhail Golubev
 */
public final class PyMoveRefactoringUtil {
  private PyMoveRefactoringUtil() {
  }

  /**
   * Checks that given file has importable name.
   *
   * @param anchor arbitrary PSI element to determine project, module, etc.
   * @param file   virtual file of the module to check
   * @see QualifiedNameFinder#findShortestImportableName(PsiElement, VirtualFile)
   */
  public static void checkValidImportableFile(@NotNull PsiElement anchor, @NotNull VirtualFile file) {
    final QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(anchor, file);
    if (!PyPsiRefactoringUtil.isValidQualifiedName(qName)) {
      throw new IncorrectOperationException(PyBundle.message("refactoring.move.error.cannot.use.module.name.$0", file.getName()));
    }
  }

  /**
   * Returns name of the symbol suitable for displaying inside dialogs.
   * Functions will have parentheses after their name, names of another named
   * elements are returned as is.
   * <p>
   * If it's not possible to get the qualified name of the element, plain
   * {@link PsiNamedElement#getName()} will be called instead.
   * <p>
   * If {@link PsiNamedElement#getName()} returns {@code null} or emtpy string,
   * empty string is returned.
   *
   * @param element named PSI element
   * @return element name as described
   */
  @NotNull
  public static String getPresentableName(@NotNull PsiNamedElement element) {
    String name = null;
    if (element instanceof PyQualifiedNameOwner) {
      // Will return null for a local function
      name = ((PyQualifiedNameOwner)element).getQualifiedName();
    }
    if (StringUtil.isEmpty(name)) {
      name = element.getName();
    }
    if (StringUtil.isNotEmpty(name)) {
      return element instanceof PyFunction ? name + "()" : name;
    }
    return "";
  }

  /**
   * Returns anchor PSI element for {@link PsiElement#addBefore(PsiElement, PsiElement)}.
   * <p>
   * If there are any usages at file's level returns the top-level parent element for the first of them,
   * otherwise return {@code null} which means that the element can be safely inserted at the end of the file.
   *
   * @param usages      usages of the original element
   * @param destination file where original/generated element is to be moved
   * @return anchor element as described
   */
  @Nullable
  public static PsiElement findLowestPossibleTopLevelInsertionPosition(@NotNull List<UsageInfo> usages, @NotNull PsiFile destination) {
    return findFirstTopLevelUsageInFile(usages, destination)
      .map(element -> PyPsiUtils.getParentRightBefore(element, element.getContainingFile()))
      .orElse(null);
  }

  @NotNull
  private static Optional<PsiElement> findFirstTopLevelUsageInFile(@NotNull List<UsageInfo> usages, @NotNull PsiFile destination) {
    return usages.stream()
      .map(UsageInfo::getElement)
      .filter(Objects::nonNull)
      .filter(element -> ScopeUtil.getScopeOwner(element) == destination)
      .filter(element -> PyImportStatementNavigator.getImportStatementByElement(element) == null)
      .min(PsiUtilCore::compareElementsByPosition);
  }
}
