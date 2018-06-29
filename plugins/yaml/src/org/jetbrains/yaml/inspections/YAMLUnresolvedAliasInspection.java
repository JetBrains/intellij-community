// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

public class YAMLUnresolvedAliasInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new YamlPsiElementVisitor() {
      @Override
      public void visitAlias(@NotNull YAMLAlias alias) {
        PsiReference reference = alias.getReference();
        if (reference != null && reference.resolve() == null) {
          holder.registerProblem(
            reference,
            YAMLBundle.message("inspections.unresolved.alias.message", alias.getAliasName()),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          );
        }
      }
    };
  }
}
