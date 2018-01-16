/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.meta.model.Field;
import org.jetbrains.yaml.psi.*;

@ApiStatus.Experimental
public abstract class YamlUnknownValuesInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new ValuesChecker(holder, metaTypeProvider);
  }

  private static class ValuesChecker extends SimpleYamlPsiVisitor {
    private final YamlMetaTypeProvider myMetaTypeProvider;
    private final ProblemsHolder myProblemsHolder;

    public ValuesChecker(@NotNull ProblemsHolder problemsHolder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
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
        return;
      }

      YAMLValue value = keyValue.getValue();
      if (value == null) {
        validateEmptyValue(meta.getField(), keyValue);
        return;
      }

      if (meta.getField().isMany()) {
        requireMultiplicityMany(value);
      }
      else if (!meta.getField().hasRelationSpecificType(Field.Relation.SEQUENCE_ITEM)) {
        requireMultiplicityOne(value);
      }

      meta.getMetaType().validateKeyValue(keyValue, myProblemsHolder);
    }

    protected void requireMultiplicityOne(@NotNull YAMLValue value) {
      if (value instanceof YAMLSequence) {
        for (YAMLSequenceItem next : ((YAMLSequence)value).getItems()) {
          if (next.getValue() == null || !next.getKeysValues().isEmpty()) {
            //not our business ?
            continue;
          }
          myProblemsHolder.registerProblem(next.getValue(),
                                           YAMLBundle.message("YamlUnknownValuesInspectionBase.error.array.not.allowed", new Object[]{}));
        }
      }
    }

    protected void requireMultiplicityMany(@NotNull YAMLValue value) {
      if (value instanceof YAMLScalar) {
        myProblemsHolder.registerProblem(value,
                                         YAMLBundle.message("YamlUnknownValuesInspectionBase.error.array.is.required", new Object[]{}));
      }
    }

    protected void validateEmptyValue(@NotNull Field feature, @NotNull YAMLKeyValue withoutValue) {
      assert withoutValue.getKey() != null; //would not be able to find `this` as a type

      if (!feature.isEmptyValueAllowed() && !feature.isAnyValueAllowed()) {
        InspectionManager manager = myProblemsHolder.getManager();
        ProblemDescriptor eolError = manager.createProblemDescriptor(
          withoutValue.getKey(),
          YAMLBundle.message("YamlUnknownValuesInspectionBase.error.value.is.required", new Object[]{}),
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          myProblemsHolder.isOnTheFly(), true);
        myProblemsHolder.registerProblem(eolError);
      }
    }
  }
}
