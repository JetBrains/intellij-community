/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.meta.model.Field;
import org.jetbrains.yaml.psi.*;

@ApiStatus.Internal
public abstract class YamlUnknownValuesInspectionBase extends YamlMetaTypeInspectionBase {

  @Override
  @NotNull
  protected PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
    return new ValuesChecker(holder, metaTypeProvider);
  }

  protected static class ValuesChecker extends SimpleYamlPsiVisitor {
    private final YamlMetaTypeProvider myMetaTypeProvider;
    private final ProblemsHolder myProblemsHolder;

    public ValuesChecker(@NotNull ProblemsHolder problemsHolder, @NotNull YamlMetaTypeProvider metaTypeProvider) {
      myProblemsHolder = problemsHolder;
      myMetaTypeProvider = metaTypeProvider;
    }

    @Override
    protected void visitYAMLSequenceItem(@NotNull YAMLSequenceItem item) {
      YAMLValue value = item.getValue();
      if (value == null) {
        return;
      }
      if (value instanceof YAMLKeyValue || value instanceof YAMLMapping) {
        // will be handled separately
        return;
      }
      YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getValueMetaType(value);
      if (meta != null && meta.getField().hasRelationSpecificType(Field.Relation.SEQUENCE_ITEM)) {
        meta.getMetaType().validateValue(value, myProblemsHolder);
      }
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
      if (value == null || YamlMetaUtil.isNull(value)) {
        validateEmptyValue(meta.getField(), keyValue);
        return;
      }

      validateMultiplicity(meta, value);
      meta.getMetaType().validateKey(keyValue, myProblemsHolder);

      if (value instanceof YAMLMapping || value instanceof YAMLSequence) {
        // will be handled separately
        return;
      }
      meta.getMetaType().validateValue(value, myProblemsHolder);
    }

    @Override
    protected void visitYAMLMapping(@NotNull YAMLMapping mapping) {
      YamlMetaTypeProvider.MetaTypeProxy meta = myMetaTypeProvider.getValueMetaType(mapping);
      if (meta != null) {
        meta.getMetaType().validateValue(mapping, myProblemsHolder);
      }
    }

    protected void validateMultiplicity(@NotNull YamlMetaTypeProvider.MetaTypeProxy meta, @NotNull YAMLValue value) {
      if (meta.getField().isMany()) {
        requireMultiplicityMany(meta, value);
      }
      else {
        requireMultiplicityOne(meta, value);
      }
    }

    protected void requireMultiplicityOne(@NotNull YamlMetaTypeProvider.MetaTypeProxy meta, @NotNull YAMLValue value) {
      if (meta.getField().hasRelationSpecificType(Field.Relation.SEQUENCE_ITEM)) {
        return;
      }
      if (value instanceof YAMLSequence) {
        for (YAMLSequenceItem next : ((YAMLSequence)value).getItems()) {
          if (next.getValue() == null || !next.getKeysValues().isEmpty()) {
            //not our business ?
            continue;
          }
          myProblemsHolder.registerProblem(next.getValue(),
                                           YAMLBundle.message("YamlUnknownValuesInspectionBase.error.array.not.allowed"));
        }
      }
    }

    protected void requireMultiplicityMany(@NotNull YamlMetaTypeProvider.MetaTypeProxy meta, @NotNull YAMLValue value) {
      if (meta.getField().hasRelationSpecificType(Field.Relation.OBJECT_CONTENTS)) {
        return;
      }
      boolean actuallyOne = value instanceof YAMLScalar ||
                            (value instanceof YAMLMapping && !meta.getField().isAnyNameAllowed());

      if (actuallyOne) {
        myProblemsHolder.registerProblem(value,
                                         YAMLBundle.message("YamlUnknownValuesInspectionBase.error.array.is.required"));
      }
    }

    protected void validateEmptyValue(@NotNull Field feature, @NotNull YAMLKeyValue withoutValue) {
      assert withoutValue.getKey() != null; //would not be able to find `this` as a type

      if (!feature.isEmptyValueAllowed()) {
        InspectionManager manager = myProblemsHolder.getManager();
        YAMLValue value = withoutValue.getValue();

        ProblemDescriptor eolError = manager.createProblemDescriptor(
          value == null ? withoutValue.getKey() : value,
          YAMLBundle.message("YamlUnknownValuesInspectionBase.error.value.is.required", ArrayUtil.EMPTY_OBJECT_ARRAY),
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          myProblemsHolder.isOnTheFly(), value == null);
        myProblemsHolder.registerProblem(eolError);
      }
    }
  }
}
