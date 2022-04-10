/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.meta.model.YamlArrayType;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.meta.model.YamlScalarType;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLValue;

@ApiStatus.Internal
public abstract class YamlUnknownKeysInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
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

      if ("<<".equals(keyValue.getKey().getText())) {
        // validation of merge types is not supported, but at least there should be no red code
        return;
      }

      YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getKeyValueMetaType(keyValue);
      if (meta == null) {
        YAMLValue parent = keyValue.getParentMapping();
        if (parent != null) {
          final YamlMetaTypeProvider.MetaTypeProxy typeProxy = myMetaTypeProvider.getValueMetaType(parent);

          if (typeProxy == null) {
            return;
          }

          // Only mark the first element as unknown, not its children
          //
          // Also if it's a mapping instead of expected scalar type,
          // don't report key-specific errors as they're redundant ("scalar value expected" error is already reported)
          YamlMetaType parentMetaType = typeProxy.getMetaType();
          if(parentMetaType instanceof YamlScalarType || parentMetaType instanceof YamlArrayType) {
            return;
          }
        }

        String msg = YAMLBundle.message("YamlUnknownKeysInspectionBase.unknown.key", keyValue.getKeyText());
        myProblemsHolder.registerProblem(keyValue.getKey(), msg, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }
}
