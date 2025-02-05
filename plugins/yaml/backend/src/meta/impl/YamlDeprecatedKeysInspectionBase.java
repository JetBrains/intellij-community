// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLKeyValue;

@ApiStatus.Internal
public abstract class YamlDeprecatedKeysInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  protected @NotNull PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new StructureChecker(holder, metaTypeProvider);
  }

  private static class StructureChecker extends SimpleYamlPsiVisitor {
    private final YamlMetaTypeProvider myMetaTypeProvider;
    private final ProblemsHolder myProblemsHolder;

    StructureChecker(@NotNull ProblemsHolder problemsHolder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
      myProblemsHolder = problemsHolder;
      myMetaTypeProvider = metaTypeProvider;
    }

    @Override
    protected void visitYAMLKeyValue(@NotNull YAMLKeyValue keyValue) {
      if (keyValue.getKey() == null) {
        return;
      }

      YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getKeyValueMetaType(keyValue);
      if (meta != null && meta.getField().isDeprecated()) {
        String msg = YAMLBundle.message("YamlDeprecatedKeysInspectionBase.deprecated.key", keyValue.getKeyText());
        myProblemsHolder.registerProblem(keyValue.getKey(), msg, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }
  }
}
