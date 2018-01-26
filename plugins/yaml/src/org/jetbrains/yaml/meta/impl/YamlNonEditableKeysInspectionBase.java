/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;

@ApiStatus.Experimental
public abstract class YamlNonEditableKeysInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new StructureChecker(holder, metaTypeProvider);
  }

  private static class StructureChecker extends SimpleYamlPsiVisitor {
    private YamlMetaTypeProvider myMetaTypeProvider;
    private final ProblemsHolder myProblemsHolder;
    private StripNonEditableKeysQuickFix myQuickFix;

    public StructureChecker(@NotNull ProblemsHolder problemsHolder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
      myProblemsHolder = problemsHolder;
      myMetaTypeProvider = metaTypeProvider;
      myQuickFix = new StripNonEditableKeysQuickFix(myMetaTypeProvider);
    }

    @Override
    protected void visitYAMLKeyValue(@NotNull YAMLKeyValue keyValue) {
      if (keyValue.getKey() == null) {
        return;
      }

      YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getKeyValueMetaType(keyValue);
      if (meta != null && !meta.getField().isEditable()) {
        String msg = YAMLBundle.message("YamlNonEditableKeysInspectionBase.noneditable.key", keyValue.getKeyText());
        myProblemsHolder.registerProblem(keyValue, msg, ProblemHighlightType.WEAK_WARNING,
                                         myQuickFix);
      }
    }

    private static class StripNonEditableKeysQuickFix implements LocalQuickFix {
      @NotNull
      private final YamlMetaTypeProvider myMetaTypeProvider;

      private StripNonEditableKeysQuickFix(@NotNull YamlMetaTypeProvider provider) {myMetaTypeProvider = provider;}

      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return YAMLBundle.message("YamlNonEditableKeyInspectionBase.strip.noneditable.keys.quickfix.name", new Object[]{});
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final ArrayList<YAMLKeyValue> keysToDelete = new ArrayList<>();

        descriptor.getPsiElement().getContainingFile().accept(new PsiRecursiveElementVisitor() {
          @Override
          public void visitElement(PsiElement element) {
            if (element instanceof YAMLKeyValue) {
              final YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getKeyValueMetaType((YAMLKeyValue)element);
              if (meta != null && !meta.getField().isEditable()) {
                keysToDelete.add((YAMLKeyValue)element);
                return;
              }
            }
            super.visitElement(element);
          }
        });

        for (YAMLKeyValue keyValue : keysToDelete) {
          keyValue.getParentMapping().deleteKeyValue(keyValue);
        }
      }
    }
  }
}
