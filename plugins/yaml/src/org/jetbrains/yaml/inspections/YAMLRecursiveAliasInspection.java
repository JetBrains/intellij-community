// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLAnchor;
import org.jetbrains.yaml.psi.YAMLValue;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;
import org.jetbrains.yaml.resolve.YAMLAliasReference;

public class YAMLRecursiveAliasInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new YamlPsiElementVisitor() {
      @Override
      public void visitAlias(@NotNull YAMLAlias alias) {
        YAMLAliasReference reference = alias.getReference();
        YAMLAnchor anchor = reference == null ? null: reference.resolve();
        YAMLValue value = anchor == null ? null : anchor.getMarkedValue();
        if (value == null) {
          return;
        }

        if (PsiTreeUtil.isAncestor(value, alias.getParent(), false)) {
          holder.registerProblem(
            reference,
            YAMLBundle.message("inspections.recursive.alias.message"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          );
        }
      }
    };
  }
}
