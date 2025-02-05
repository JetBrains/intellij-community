// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLCompoundValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLValue;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public abstract class YamlScalarType extends YamlMetaType {

  /**
   * @deprecated initialise the {@code displayName} explicitly via {@link #YamlScalarType(String, String)}
   */
  @Deprecated(forRemoval = true)
  protected YamlScalarType(@NonNls @NotNull String typeName) {
    super(typeName);
  }

  protected YamlScalarType(@NonNls @NotNull String typeName, @NonNls @NotNull String displayName) {
    super(typeName, displayName);
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
  public @NotNull Icon getIcon() {
    return PlatformIcons.PROPERTY_ICON;
  }

  @Override
  public void validateValue(@NotNull YAMLValue value, @NotNull ProblemsHolder problemsHolder) {
    if (value instanceof YAMLScalar) {
      validateScalarValue((YAMLScalar)value, problemsHolder);
    }
    else if (value instanceof YAMLCompoundValue) {
      problemsHolder.registerProblem(value, YAMLBundle.message("YamlScalarType.error.scalar.value"), ProblemHighlightType.ERROR);
    }
  }

  protected void validateScalarValue(@NotNull YAMLScalar scalarValue, @NotNull ProblemsHolder holder) {
    //
  }

  @Override
  public void buildInsertionSuffixMarkup(@NotNull YamlInsertionMarkup markup,
                                         @NotNull Field.Relation relation,
                                         @NotNull ForcedCompletionPath.Iteration iteration) {
    switch (relation) {
      case OBJECT_CONTENTS /* weird, but let's ignore and breakthrough to defaults */, SCALAR_VALUE -> {
        markup.append(": ");
        if (iteration.isEndOfPathReached()) {
          markup.appendCaret();
        }
      }
      case SEQUENCE_ITEM -> {
        markup.append(":");
        markup.doTabbedBlockForSequenceItem(() -> {
          if (iteration.isEndOfPathReached()) {
            markup.appendCaret();
          }
        });
      }
      default -> throw new IllegalStateException("Unknown relation: " + relation); //NON-NLS
    }
  }
}
