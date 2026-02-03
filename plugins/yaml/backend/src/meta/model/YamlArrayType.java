// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class YamlArrayType extends YamlMetaType {

  private final @NotNull YamlMetaType myElementType;

  public YamlArrayType(@NotNull YamlMetaType elementType) {
    super(elementType.getTypeName() + "[]");
    myElementType = elementType;
  }

  @Override
  public void validateValue(@NotNull YAMLValue value, @NotNull ProblemsHolder problemsHolder) {
    if(!(value instanceof YAMLSequence))
      problemsHolder.registerProblem(value, YAMLBundle.message("YamlUnknownValuesInspectionBase.error.array.is.required"));
  }

  public @NotNull YamlMetaType getElementType() {
    return myElementType;
  }

  @Override
  public @Nullable Field findFeatureByName(@NotNull String name) {
    return null;
  }

  @Override
  public @NotNull List<String> computeMissingFields(@NotNull Set<String> existingFields) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<Field> computeKeyCompletions(@Nullable YAMLMapping existingMapping) {
    return Collections.emptyList();
  }

  @Override
  public void buildInsertionSuffixMarkup(@NotNull YamlInsertionMarkup markup,
                                         Field.@NotNull Relation relation,
                                         ForcedCompletionPath.@NotNull Iteration iteration) {

  }
}
