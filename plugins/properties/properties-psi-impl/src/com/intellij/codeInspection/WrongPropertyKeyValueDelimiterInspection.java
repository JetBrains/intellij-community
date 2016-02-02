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
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertySuppressableInspectionBase;
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
public class WrongPropertyKeyValueDelimiterInspection extends PropertySuppressableInspectionBase implements CleanupLocalInspectionTool {

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
      public void visitElement(PsiElement element) {
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
    public ReplaceKeyValueDelimiterQuickFix(@NotNull PsiElement element) {
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
      return "Replace property key/value delimiter according code style";
    }
  }
}
