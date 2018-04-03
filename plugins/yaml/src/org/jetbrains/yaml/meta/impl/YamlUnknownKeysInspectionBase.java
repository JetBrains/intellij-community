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
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLValue;

@ApiStatus.Experimental
public abstract class YamlUnknownKeysInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new StructureChecker(holder, metaTypeProvider);
  }

  private static class StructureChecker extends SimpleYamlPsiVisitor {
    private YamlMetaTypeProvider myMetaTypeProvider;
    private final ProblemsHolder myProblemsHolder;

    public StructureChecker(@NotNull ProblemsHolder problemsHolder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
      myProblemsHolder = problemsHolder;
      myMetaTypeProvider = metaTypeProvider;
    }

    @Override
    protected void visitYAMLKeyValue(@NotNull YAMLKeyValue keyValue) {
      if (keyValue.getKey() == null) {
        return;
      }

      YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getKeyValueMetaType(keyValue);
      if (meta == null) {
        //only mark the first element as unknown, not its children
        YAMLValue parent = keyValue.getParentMapping();
        if (parent != null && myMetaTypeProvider.getValueMetaType(parent) == null) {
          return;
        }

        String msg = YAMLBundle.message("YamlUnknownKeysInspectionBase.unknown.key", keyValue.getKeyText());
        myProblemsHolder.registerProblem(keyValue.getKey(), msg, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }
}
