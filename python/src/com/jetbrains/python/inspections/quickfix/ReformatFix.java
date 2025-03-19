// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ReformatFix implements ModCommandAction {
  @Override
  public @NotNull String getFamilyName() {
    return PyBundle.message("QFIX.reformat.file");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.psiUpdate(context.file(), (file, updater) -> {
      CodeStyleManager.getInstance(context.project()).reformat(file);
    });
  }
}
