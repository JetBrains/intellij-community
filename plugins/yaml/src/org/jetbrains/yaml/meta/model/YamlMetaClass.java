// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.meta.impl.YamlMetaUtil;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.*;
import java.util.stream.Collectors;

@ApiStatus.Internal
@SuppressWarnings("UnusedReturnValue")
public class YamlMetaClass extends YamlMetaType {
  private final List<Field> myFeatures = new LinkedList<>();
  private final List<Field> myFeaturesRO = Collections.unmodifiableList(myFeatures);

  public YamlMetaClass(@NonNls @NotNull String typeName) {
    super(typeName);
  }

  @Override
  @Nullable
  public Field findFeatureByName(@NotNull String name) {
    if (getFeatures().isEmpty()) {
      return null;
    }

    Optional<Field> byExactName = getFeatures().stream()
      .filter(f -> !f.isByPattern() && name.equals(f.getName()))
      .findAny();

    return byExactName.orElse(
      ContainerUtil.find(getFeatures(), f -> f.isByPattern() && f.acceptsFieldName(name))
    );
  }

  @NotNull
  @Override
  public List<String> computeMissingFields(@NotNull Set<String> existingFields) {
    return myFeatures.stream()
      .filter(Field::isRequired)
      .map(Field::getName)
      .filter(name -> !existingFields.contains(name))
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  public List<Field> computeKeyCompletions(@Nullable YAMLMapping existingMapping) {
    return ContainerUtil.filter(myFeatures, Field::isEditable);
  }

  @NotNull
  public List<Field> getFeatures() {
    return myFeaturesRO;
  }

  @NotNull
  protected final Field addStringFeature(@NotNull String name) {
    return addFeature(new Field(name, YamlStringType.getInstance()));
  }

  @NotNull
  protected Field addBooleanFeature(@NotNull String name) {
    return addScalarFeature(name, YamlBooleanType.getSharedInstance());
  }

  @NotNull
  protected final Field addScalarFeature(@NotNull YamlScalarType type) {
    return addScalarFeature(type.getTypeName(), type);
  }

  @NotNull
  protected final Field addScalarFeature(@NotNull String name, @NotNull YamlScalarType type) {
    return addFeature(new Field(name, type));
  }

  @NotNull
  protected final Field addObjectFeature(@NotNull YamlMetaClass metaClass) {
    return addFeature(new Field(metaClass.getTypeName(), metaClass));
  }

  protected <T extends Field> T addFeature(@NotNull T child) {
    myFeatures.add(child);
    return child;
  }

  @Override
  public void buildInsertionSuffixMarkup(@NotNull YamlInsertionMarkup markup,
                                         @NotNull Field.Relation relation,
                                         @NotNull ForcedCompletionPath.Iteration iteration) {
    switch (relation) {
      case SCALAR_VALUE:
        throw new IllegalArgumentException("Default relation " + relation + " requested for complex type: " + this);

      case OBJECT_CONTENTS:
        doBuildInsertionSuffixMarkup(markup, false, iteration);
        break;

      case SEQUENCE_ITEM:
        doBuildInsertionSuffixMarkup(markup, true, iteration);
        break;

      default:
        throw new IllegalArgumentException("Unknown relation: " + relation);
    }
  }

  @Override
  public void validateValue(@NotNull YAMLValue value, @NotNull ProblemsHolder problemsHolder) {
    super.validateValue(value, problemsHolder);
    if (value instanceof YAMLScalar && !YamlMetaUtil.isNull(value)) {
      problemsHolder.registerProblem(value,
                                     YAMLBundle.message("YamlMetaClass.error.scalar.value", ArrayUtil.EMPTY_OBJECT_ARRAY));
    }
  }

  private void doBuildInsertionSuffixMarkup(@NotNull YamlInsertionMarkup markup,
                                            boolean manyNotOne,
                                            @NotNull ForcedCompletionPath.Iteration iteration) {
    markup.append(":");
    markup.doTabbedBlock(manyNotOne ? 2 : 1, () -> {
      markup.newLineAndTabs(manyNotOne);

      List<Field> allRequired =
        ContainerUtil.filter(myFeatures, field -> field.isRequired() || iteration.isNextOnPath(field));
      if (allRequired.isEmpty() && iteration.isEndOfPathReached()) {
        markup.appendCaret();
      }
      else {
        for (Iterator<Field> iterator = allRequired.iterator(); iterator.hasNext(); ) {
          Field field = iterator.next();
          buildCompleteKeyMarkup(markup, field, iteration.nextIterationFor(field));
          if (iterator.hasNext()) {
            markup.newLineAndTabs();
          }
        }
      }
    });
  }
}
