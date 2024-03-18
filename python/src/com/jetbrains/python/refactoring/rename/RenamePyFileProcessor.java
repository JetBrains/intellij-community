// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenamePsiFileProcessor;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public final class RenamePyFileProcessor extends RenamePsiFileProcessor {
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
  public Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                 @NotNull SearchScope searchScope,
                                                 boolean searchInCommentsAndStrings) {
    final List<PsiReference> results = new ArrayList<>();
    for (PsiReference reference : super.findReferences(element, searchScope, searchInCommentsAndStrings)) {
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
    final String newFileName = FileUtilRt.getNameWithoutExtension(newName);
    if (!PyNames.isIdentifier(newFileName)) {
      final List<UsageInfo> usages = new ArrayList<>(result);
      for (UsageInfo usageInfo : usages) {
        final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(usageInfo.getElement(), PyImportStatementBase.class);
        if (importStatement != null) {
          result.add(new UnresolvableCollisionUsageInfo(importStatement, element) {
            @Override
            public String getDescription() {
              return PyBundle
                .message("refactoring.rename.not.valid.identifier", newFileName, importStatement.getContainingFile().getName());
            }
          });
        }
      }
    }
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {

    final PsiFile file = (PsiFile)element;
    if (file instanceof PyiFile) {
      final PsiElement originalElement = PyiUtil.getOriginalElement((PyiFile)file);
      if (originalElement != null) {
        allRenames.put(originalElement, newName.substring(0, newName.length() - 1));
      }
    }
    else if (file instanceof PyFile) {
      final PsiElement stubElement = PyiUtil.getPythonStub((PyFile)file);
      if (stubElement != null) {
        allRenames.put(stubElement, newName + "i");
      }
    }
    super.prepareRenaming(element, newName, allRenames);
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
