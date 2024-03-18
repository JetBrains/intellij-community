// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyNamedParameter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Parameter renaming.
 */
public class RenameParameterQuickFix extends PsiUpdateModCommandQuickFix {
  private final String myNewName;

  public RenameParameterQuickFix(String newName) {
    myNewName = newName;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyNamedParameter pyNamedParameter) {
      updater.rename(pyNamedParameter, List.of(myNewName));
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.rename.parameter");
  }

  @Override
  @NotNull
  public String getName() {
    return PyPsiBundle.message("QFIX.rename.parameter", myNewName);
  }
}
