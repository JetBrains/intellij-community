// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class YamlAnyOfType extends YamlComposedTypeBase {

  public static YamlMetaType anyOf(YamlMetaType... types) {
    return anyOf(null, types);
  }

  public static YamlMetaType anyOf(@Nullable String complexTypeDisplayNameHint, YamlMetaType... types) {
    if (types.length == 0) {
      throw new IllegalArgumentException();
    }
    if (types.length == 1) {
      return types[0];
    }
    String name = "AnyOf[" + Stream.of(types).map(YamlMetaType::getDisplayName).collect(Collectors.joining()) + "]";
    return new YamlAnyOfType(name, complexTypeDisplayNameHint != null ? complexTypeDisplayNameHint : name, flattenTypes(types));
  }

  @Override
  protected YamlMetaType composeTypes(YamlMetaType... types) {
    return anyOf(types);
  }

  protected YamlAnyOfType(@NotNull String typeName, List<YamlMetaType> types) {
    this(typeName, typeName, types);
  }

  protected YamlAnyOfType(@NotNull String typeName, @NotNull String displayName, List<YamlMetaType> types) {
    super(typeName, displayName, types);
  }

  @Override
  public void validateKey(@NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder problemsHolder) {
    List<ProblemsHolder> allProblems = allProblemsOrEmpty(problemsHolder, listNonScalarSubTypes(),
                                                          (nextType, nextHolder) -> nextType.validateKey(keyValue, nextHolder));

    allProblems.stream()
      .flatMap(h -> h.getResults().stream())
      .forEach(problemsHolder::registerProblem);
  }

  @Override
  public void validateValue(@NotNull YAMLValue value, @NotNull ProblemsHolder problemsHolder) {
    List<YamlMetaType> types;
    if (value instanceof YAMLScalar) {
      types = listScalarSubTypes();
      if (types.isEmpty()) { // value kind does not match, let some scalar component to report it
        types = Collections.singletonList(listNonScalarSubTypes().get(0));
      }
    }
    else {
      types = listNonScalarSubTypes();
      if (types.isEmpty()) {
        // only scalar components, let one of them report it
        types = Collections.singletonList(listScalarSubTypes().get(0));
      }
    }
    List<ProblemsHolder> allProblems = allProblemsOrEmpty(problemsHolder, types,
                                                          (nextType, nextHolder) -> nextType.validateValue(value, nextHolder));

    allProblems.stream()
      .flatMap(h -> h.getResults().stream())
      .forEach(problemsHolder::registerProblem);
  }

  @Override
  public @NotNull List<? extends LookupElement> getValueLookups(@NotNull YAMLScalar insertedScalar, @Nullable CompletionContext completionContext) {
    return streamSubTypes()
      .flatMap(type -> type.getValueLookups(insertedScalar, completionContext).stream())
      .collect(Collectors.toList());
  }

  private static List<ProblemsHolder> allProblemsOrEmpty(@NotNull ProblemsHolder problemsHolder, @NotNull List<YamlMetaType> types,
                                                         @NotNull BiConsumer<YamlMetaType, ProblemsHolder> oneValidation) {
    List<ProblemsHolder> problems = new SmartList<>();
    for (YamlMetaType nextType : types) {
      ProblemsHolder nextHolder = makeCopy(problemsHolder);
      oneValidation.accept(nextType, nextHolder);
      if (!nextHolder.hasResults()) {
        return Collections.emptyList();
      }
      problems.add(nextHolder);
    }
    return problems;
  }
}
