// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor;

import java.util.ArrayList;
import java.util.Objects;

@ApiStatus.Internal
public abstract class YamlNonEditableKeysInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new StructureChecker(holder, metaTypeProvider);
  }

  private static class StructureChecker extends SimpleYamlPsiVisitor {
    private final YamlMetaTypeProvider myMetaTypeProvider;
    private final ProblemsHolder myProblemsHolder;
    private final StripNonEditableKeysQuickFix myQuickFix;

    StructureChecker(@NotNull ProblemsHolder problemsHolder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
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
        myProblemsHolder.registerProblem(keyValue.getKey(), msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                         myQuickFix);
      }
    }

    private static final class StripNonEditableKeysQuickFix implements LocalQuickFix {
      @NotNull
      private final YamlMetaTypeProvider myMetaTypeProvider;

      private StripNonEditableKeysQuickFix(@NotNull YamlMetaTypeProvider provider) {myMetaTypeProvider = provider;}

      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return YAMLBundle.message("YamlNonEditableKeyInspectionBase.strip.noneditable.keys.quickfix.name");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final ArrayList<YAMLKeyValue> keysToDelete = new ArrayList<>();

        descriptor.getPsiElement().getContainingFile().accept(new YamlRecursivePsiElementVisitor() {
          @Override
          public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
            final YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getKeyValueMetaType(keyValue);
            if (meta != null && !meta.getField().isEditable()) {
              if (keyValue.getParentMapping() != null) {
                keysToDelete.add(keyValue);
              }
              else {
                Logger.getInstance(YamlNonEditableKeysInspectionBase.class)
                  .warn("Wanted to remove KV, but it does not have a parent mapping");
              }
              return;
            }
            super.visitKeyValue(keyValue);
          }
        });

        for (YAMLKeyValue keyValue : keysToDelete) {
          Objects.requireNonNull(keyValue.getParentMapping()).deleteKeyValue(keyValue);
        }
      }
    }
  }
}
