/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.command.CommandExecutor;
import com.jetbrains.commandInterface.commandLine.psi.*;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Inspection that ensures command line options and arguments are correct.
 * It works only if list of available commands is provided to command file
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("commandLine.inspection.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new MyVisitor(holder);
  }

  private static final class MyVisitor extends CommandLineVisitor {
    @NotNull
    private final ProblemsHolder myHolder;

    private MyVisitor(@NotNull final ProblemsHolder holder) {
      myHolder = holder;
    }

    @Nullable
    private static CommandLineFile getFile(@NotNull final PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, CommandLineFile.class);
    }

    @Nullable
    private static ValidationResult getValidationResult(@NotNull final PsiElement element) {
      final CommandLineFile file = getFile(element);
      if (file == null) {
        return null;
      }
      return file.getValidationResult();
    }

    @Override
    public void visitCommand(@NotNull final CommandLineCommand o) {
      super.visitCommand(o);
      final CommandLineFile file = getFile(o);
      if (file == null) {
        return;
      }
      final List<Command> commands = file.getCommands();
      if (commands == null) {
        return;
      }
      for (final Command command : commands) {
        if (o.getText().equals(command.getName())) {
          return;
        }
      }
      myHolder.registerProblem(o, PyBundle.message("commandLine.inspection.badCommand"));
    }

    @Override
    public void visitOption(@NotNull final CommandLineOption o) {
      super.visitOption(o);
      final ValidationResult validationResult = getValidationResult(o);
      if (validationResult != null && validationResult.isBadValue(o)) {
        myHolder.registerProblem(o, PyBundle.message("commandLine.inspection.badOption"));
      }
    }


    @Override
    public void visitArgument(@NotNull final CommandLineArgument o) {
      super.visitArgument(o);
      final ValidationResult validationResult = getValidationResult(o);
      if (validationResult != null) {
        if (validationResult.isBadValue(o)) {
          myHolder.registerProblem(o, PyBundle.message("commandLine.inspection.badArgument"));
        }
        else if (validationResult.isExcessArgument(o)) {
          myHolder.registerProblem(o, PyBundle.message("commandLine.inspection.excessArgument"));
        }
      }
    }
  }
}
