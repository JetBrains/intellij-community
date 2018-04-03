/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import java.util.Collections;
import java.util.List;

@ApiStatus.Experimental
public abstract class YamlScalarType extends YamlMetaType {

  protected YamlScalarType(@NotNull String typeName) {
    super(typeName);
  }

  @Nullable
  @Override
  public Field findFeatureByName(@NotNull String name) {
    return null;
  }

  @Override
  public void validateKeyValue(@NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder problemsHolder) {
    YAMLValue value = keyValue.getValue();
    if (value == null) {
      return;
    }
    if (value instanceof YAMLScalar) {
      validateScalarValue((YAMLScalar)value, problemsHolder);
      return;
    }
    if (value instanceof YAMLSequence) {
      for (YAMLSequenceItem nextItem : ((YAMLSequence)value).getItems()) {
        YAMLValue nextValue = nextItem.getValue();
        if (nextValue instanceof YAMLScalar) {
          validateScalarValue((YAMLScalar)nextValue, problemsHolder);
        }
      }
    }
  }

  protected void validateScalarValue(@NotNull YAMLScalar scalarValue, @NotNull ProblemsHolder holder) {
    //
  }

  @NotNull
  public List<? extends LookupElement> getValueLookups(@NotNull YAMLScalar context) {
    return Collections.emptyList();
  }

  @Override
  public void buildInsertionSuffixMarkup(@NotNull YamlInsertionMarkup markup,
                                         @NotNull Field.Relation relation,
                                         @NotNull ForcedCompletionPath.Iteration iteration) {
    switch (relation) {
      case OBJECT_CONTENTS:
        // weird, but lets ignore and breakthrough to defaults
      case SCALAR_VALUE: {
        markup.append(": ");
        if (iteration.isEndOfPathReached()) {
          markup.appendCaret();
        }
        break;
      }
      case SEQUENCE_ITEM: {
        markup.append(":");
        markup.increaseTabs(2);
        try {
          markup.newLineAndTabs(true);
          if (iteration.isEndOfPathReached()) {
            markup.appendCaret();
          }
        }
        finally {
          markup.decreaseTabs(2);
        }
        break;
      }
      default:
        throw new IllegalStateException("Unknown relation: " + relation);
    }
  }
}
