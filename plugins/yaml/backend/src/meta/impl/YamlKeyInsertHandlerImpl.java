// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInsight.completion.InsertionContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.meta.model.Field;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.meta.model.YamlMetaType.ForcedCompletionPath;
import org.jetbrains.yaml.meta.model.YamlMetaType.YamlInsertionMarkup;

@ApiStatus.Internal
public class YamlKeyInsertHandlerImpl extends YamlKeyInsertHandler {
  private final Field myToBeInserted;

  public YamlKeyInsertHandlerImpl(boolean needsSequenceItemMark, @NotNull Field toBeInserted) {
    super(needsSequenceItemMark);
    myToBeInserted = toBeInserted;
  }

  @Override
  protected @NotNull YamlInsertionMarkup computeInsertionMarkup(@NotNull InsertionContext context,
                                                                @NotNull ForcedCompletionPath forcedCompletionPath) {
    YamlInsertionMarkup markup = new YamlInsertionMarkup(context);
    Field.Relation relation = myToBeInserted.getDefaultRelation();
    YamlMetaType defaultType = myToBeInserted.getType(relation);
    defaultType.buildInsertionSuffixMarkup(markup, relation, forcedCompletionPath.start());
    return markup;
  }

  @Override
  protected @NotNull String getReplacement() {
    return myToBeInserted.getName();
  }
}
