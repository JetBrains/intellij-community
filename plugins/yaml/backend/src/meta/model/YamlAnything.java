// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Like {@link YamlUnstructuredClass} but allows scalar values
 */
@ApiStatus.Internal
public class YamlAnything extends YamlMetaType {
  private static final YamlAnything ourInstance = new YamlAnything();

  private static final Field ourAnyField = new Field("<any-key>", ourInstance)
    .withAnyName()
    .withRelationSpecificType(Field.Relation.SEQUENCE_ITEM, ourInstance)
    .withRelationSpecificType(Field.Relation.SCALAR_VALUE, ourInstance)
    .withEmptyValueAllowed(true);

  public YamlAnything() {
    super("yaml:anything");
  }

  @Override
  public @Nullable Field findFeatureByName(@NotNull String name) {
    return ourAnyField;
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
    markup.append(": ");
    if (iteration.isEndOfPathReached()) {
      markup.appendCaret();
    }
  }

  public static YamlMetaType getInstance() {
      return ourInstance;
  }
}
