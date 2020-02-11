// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
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

public class YamlOneOfType extends YamlComposedTypeBase {

  public static YamlMetaType oneOf(YamlMetaType... types) {
    if (types.length == 0) {
      throw new IllegalArgumentException();
    }
    if (types.length == 1) {
      return types[0];
    }
    String name = "OneOf[" + Stream.of(types).map(YamlMetaType::getDisplayName).collect(Collectors.joining(",")) + "]";
    return new YamlOneOfType(name, flattenTypes(types));
  }

  protected YamlOneOfType(@NotNull String typeName, List<YamlMetaType> types) {
    super(typeName, types);
  }

  @Override
  public void validateKey(@NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder problemsHolder) {
    // todo ?
  }

  @Override
  public void validateValue(@NotNull YAMLValue value, @NotNull ProblemsHolder problemsHolder) {
    List<ProblemsHolder> allProblems = collectProblems(value, problemsHolder, myTypes,
                                                       (nextType, nextHolder) -> nextType.validateDeep(value, nextHolder));

    allProblems.stream()
      .flatMap(h -> h.getResults().stream())
      .forEach(problemsHolder::registerProblem);
  }

  @NotNull
  @Override
  public List<? extends LookupElement> getValueLookups(@NotNull YAMLScalar insertedScalar, @Nullable CompletionContext completionContext) {
    return streamSubTypes()
      .flatMap(type -> type.getValueLookups(insertedScalar, completionContext).stream())
      .collect(Collectors.toList());
  }

  @NotNull
  private static List<ProblemsHolder> collectProblems(@NotNull PsiElement value,
                                                      @NotNull ProblemsHolder problemsHolder, @NotNull List<YamlMetaType> types,
                                                      @NotNull BiConsumer<YamlMetaType, ProblemsHolder> oneValidation) {
    int timesValidated = 0;

    List<ProblemsHolder> problems = new SmartList<>();
    for (YamlMetaType nextType : types) {
      ProblemsHolder nextHolder = makeCopy(problemsHolder);
      oneValidation.accept(nextType, nextHolder);
      if (!nextHolder.hasResults()) {
        timesValidated++;
      }
      problems.add(nextHolder);
    }

    if(timesValidated == 1)
      return Collections.emptyList();

    if(timesValidated > 1) {
      ProblemsHolder oneMoreHolder = makeCopy(problemsHolder);
      oneMoreHolder.registerProblem(value, "The value must be valid against exactly one schema");
      problems.add(oneMoreHolder);
    }

    return problems;
  }
}
