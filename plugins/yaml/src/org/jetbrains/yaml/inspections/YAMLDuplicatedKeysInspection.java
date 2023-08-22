// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.Collection;
import java.util.Map;

public class YAMLDuplicatedKeysInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new YamlPsiElementVisitor() {
      @Override
      public void visitMapping(@NotNull YAMLMapping mapping) {

        MultiMap<String, YAMLKeyValue> occurrences = new MultiMap<>();

        for (YAMLKeyValue keyValue : mapping.getKeyValues()) {
          final String keyName = keyValue.getKeyText().trim();
          // http://yaml.org/type/merge.html
          if (keyName.equals("<<")) {
            continue;
          }
          if (!keyName.isEmpty()) {
            occurrences.putValue(keyName, keyValue);
          }
        }

        for (Map.Entry<String, Collection<YAMLKeyValue>> entry : occurrences.entrySet()) {
          if (entry.getValue().size() > 1) {
            entry.getValue().forEach((duplicatedKey) -> {
              assert duplicatedKey.getKey() != null;
              assert duplicatedKey.getParentMapping() != null : "This key is gotten from mapping";

              holder.registerProblem(duplicatedKey.getKey(),
                                     YAMLBundle.message("YAMLDuplicatedKeysInspection.duplicated.key", entry.getKey()),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveDuplicatedKeyQuickFix());
            });
          }
        }
      }
    };
  }

  private static class RemoveDuplicatedKeyQuickFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return YAMLBundle.message("YAMLDuplicatedKeysInspection.remove.key.quickfix.name");
    }

    @Override
    public boolean availableInBatchMode() {
      //IDEA-185914: quick fix is disabled in batch mode cause of ambiguity which item should stay
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      YAMLKeyValue keyVal = (YAMLKeyValue)descriptor.getPsiElement().getParent();
      if (keyVal == null || keyVal.getParentMapping() == null) {
        return;
      }

      keyVal.getParentMapping().deleteKeyValue(keyVal);
    }
  }
}
