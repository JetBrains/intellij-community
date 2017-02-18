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
  public PsiElement substituteElementToRename(PsiElement element, @Nullable Editor editor) {
    final PyFile file = (PyFile) element;
    if (file.getName().equals(PyNames.INIT_DOT_PY) && editor != null) {
      return file.getParent();
    }
    return element;
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    final List<PsiReference> results = new ArrayList<>();
    for (PsiReference reference : super.findReferences(element)) {
      if (isNotAliasedInImportElement(reference)) {
        results.add(reference);
      }
    }
    return results;
  }

  @Override
  public void findCollisions(PsiElement element,
                             final String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
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
}
