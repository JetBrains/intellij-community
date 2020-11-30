// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.codeInspection;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesInspectionBase;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class WrongPropertyKeyValueDelimiterInspection extends PropertiesInspectionBase implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!(holder.getFile() instanceof PropertiesFileImpl)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final PropertiesCodeStyleSettings codeStyleSettings = PropertiesCodeStyleSettings.getInstance(holder.getProject());
    final char codeStyleKeyValueDelimiter = codeStyleSettings.getDelimiter();
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PropertyImpl) {
          final char delimiter = ((PropertyImpl)element).getKeyValueDelimiter();
          if (delimiter != codeStyleKeyValueDelimiter) {
            holder.registerProblem(element, PropertiesBundle.message("wrong.property.key.value.delimiter.inspection.display.name"), new ReplaceKeyValueDelimiterQuickFix(element));
          }
        }
      }
    };
  }

  private static final class ReplaceKeyValueDelimiterQuickFix extends LocalQuickFixOnPsiElement implements HighPriorityAction {
    ReplaceKeyValueDelimiterQuickFix(@NotNull PsiElement element) {
      super(element);
    }

    @NotNull
    @Override
    public String getText() {
      return getFamilyName();
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement element, @NotNull PsiElement endElement) {
      ((PropertyImpl) element).replaceKeyValueDelimiterWithDefault();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return PropertiesBundle.message("replace.key.value.delimiter.quick.fix.family.name");
    }
  }
}
