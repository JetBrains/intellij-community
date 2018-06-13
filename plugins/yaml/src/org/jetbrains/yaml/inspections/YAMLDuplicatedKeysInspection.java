// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
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
              holder.registerProblem(duplicatedKey.getKey(),
                                     YAMLBundle.message("YAMLDuplicatedKeysInspection.duplicated.key", entry.getKey()),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveDuplicatedKeyQuickFix(duplicatedKey));
            });
          }
        }
      }
    };
  }

  private static class RemoveDuplicatedKeyQuickFix implements LocalQuickFix {
    private final SmartPsiElementPointer<YAMLKeyValue> myKeyValueHolder;

    public RemoveDuplicatedKeyQuickFix(@NotNull final YAMLKeyValue keyValue) {
      myKeyValueHolder = SmartPointerManager.getInstance(keyValue.getProject()).createSmartPsiElementPointer(keyValue);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return YAMLBundle.message("YAMLDuplicatedKeysInspection.remove.key.quickfix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      YAMLKeyValue keyVal = myKeyValueHolder.getElement();
      if (keyVal == null) {
        return;
      }

      keyVal.getParentMapping().deleteKeyValue(keyVal);
    }
  }
}
