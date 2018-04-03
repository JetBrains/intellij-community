/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.meta.model.Field;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.meta.model.YamlMetaType.ForcedCompletionPath;
import org.jetbrains.yaml.meta.model.YamlMetaType.YamlInsertionMarkup;

@ApiStatus.Experimental
public class YamlKeyInsertHandlerImpl extends YamlKeyInsertHandler {
  private final Field myToBeInserted;

  public YamlKeyInsertHandlerImpl(boolean needsSequenceItemMark, @NotNull Field toBeInserted) {
    super(needsSequenceItemMark);
    myToBeInserted = toBeInserted;
  }

  @NotNull
  @Override
  protected YamlInsertionMarkup computeInsertionMarkup(@NotNull ForcedCompletionPath forcedCompletionPath) {
    YamlInsertionMarkup markup = new YamlInsertionMarkup();
    Field.Relation relation = myToBeInserted.getDefaultRelation();
    YamlMetaType defaultType = myToBeInserted.getType(relation);
    defaultType.buildInsertionSuffixMarkup(markup, relation, forcedCompletionPath.start());
    return markup;
  }

  @NotNull
  @Override
  protected String getReplacement() {
    return myToBeInserted.getName();
  }
}
