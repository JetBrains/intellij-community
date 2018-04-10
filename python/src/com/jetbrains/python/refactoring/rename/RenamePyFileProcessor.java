// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenamePsiFileProcessor;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class RenamePyFileProcessor extends RenamePsiFileProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PyFile;
  }

  @Override
  public PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor) {
    final PyFile file = (PyFile) element;
    if (file.getName().equals(PyNames.INIT_DOT_PY) && editor != null) {
      return file.getParent();
    }
    return element;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(@NotNull PsiElement element) {
    final List<PsiReference> results = new ArrayList<>();
    for (PsiReference reference : super.findReferences(element)) {
      if (isNotAliasedInImportElement(reference)) {
        results.add(reference);
      }
    }
    return results;
  }

  @Override
  public void findCollisions(@NotNull PsiElement element,
                             @NotNull final String newName,
                             @NotNull Map<? extends PsiElement, String> allRenames,
                             @NotNull List<UsageInfo> result) {
    final String newFileName = FileUtil.getNameWithoutExtension(newName);
    if (!PyNames.isIdentifier(newFileName)) {
      final List<UsageInfo> usages = new ArrayList<>(result);
      for (UsageInfo usageInfo : usages) {
        final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(usageInfo.getElement(), PyImportStatementBase.class);
        if (importStatement != null) {
          result.add(new UnresolvableCollisionUsageInfo(importStatement, element) {
            @Override
            public String getDescription() {
              return "The name '" + newFileName + "' is not a valid Python identifier. Cannot update import statement in '" +
                     importStatement.getContainingFile().getName() + "'";
            }
          });
        }
      }
    }
  }

  private static boolean isNotAliasedInImportElement(@NotNull PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      for (ResolveResult result : results) {
        final PsiElement resolved = result.getElement();
        if (resolved instanceof PyImportElement && ((PyImportElement)resolved).getAsName() != null) {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  @Override
  public String getHelpID(PsiElement element) {
    return "procedures.refactoring.renameRefactorings";
  }
}
