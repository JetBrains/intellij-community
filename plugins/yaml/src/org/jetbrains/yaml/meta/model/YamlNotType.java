// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class YamlNotType extends YamlMetaType {

  private @NotNull YamlMetaType myDelegate;

  private static final Field ourAnyField = new Field("<any-key>", YamlAnything.getInstance())
    .withAnyName()
    .withRelationSpecificType(Field.Relation.SEQUENCE_ITEM, YamlAnything.getInstance())
    .withRelationSpecificType(Field.Relation.SCALAR_VALUE, YamlAnything.getInstance())
    .withEmptyValueAllowed(true);


  public YamlNotType(@NonNls @NotNull String typeName, @NotNull YamlMetaType delegate) {
    super(typeName);
    myDelegate = delegate;
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

  @Override
  public void validateValue(@NotNull YAMLValue value, @NotNull ProblemsHolder problemsHolder) {
    final ProblemsHolder tempHolder = clone(problemsHolder);

    myDelegate.validateDeep(value, tempHolder);
    if (!tempHolder.hasResults())
      problemsHolder.registerProblem(value, "The value isn't valid against NOT schema: " + myDelegate.getDisplayName());
  }

  private static ProblemsHolder clone(@NotNull ProblemsHolder original) {
    return new ProblemsHolder(original.getManager(), original.getFile(), original.isOnTheFly());
  }
}
