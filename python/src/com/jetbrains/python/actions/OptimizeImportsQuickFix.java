package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.PyImportOptimizer;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class OptimizeImportsQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return "Optimize imports";
  }

  @NotNull
  public String getFamilyName() {
    return "Optimize imports";
  }

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiFile file = descriptor.getPsiElement().getContainingFile();
    ImportOptimizer optimizer = new PyImportOptimizer();
    final Runnable runnable = optimizer.processFile(file);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, runnable, getFamilyName(), this);
      }
    });
  }
}
