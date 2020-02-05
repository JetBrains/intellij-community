// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class YamlComposedTypeBase extends YamlMetaType {
  protected final List<YamlMetaType> myTypes;

  protected static List<YamlMetaType> flattenTypes(YamlMetaType... types) {
    return new SmartList<>(types);
  }

  protected abstract YamlMetaType composeTypes(YamlMetaType... types);

  protected YamlComposedTypeBase(@NotNull String typeName, List<YamlMetaType> types) {
    super(typeName);
    assert types.size() > 1 : "Nothing to compose: " + types;
    myTypes = copyList(types);
  }

  @Nullable
  @Override
  public Field findFeatureByName(@NotNull String name) {
    return mergeFields(name, myTypes.stream()
      .map(type -> type.findFeatureByName(name))
      .filter(Objects::nonNull)
      .collect(Collectors.toList())
    );
  }

  @NotNull
  @Override
  public List<String> computeMissingFields(@NotNull Set<String> existingFields) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (YamlMetaType next : myTypes) {
      if (next instanceof YamlScalarType) {
        continue;
      }
      List<String> nextMissing = next.computeMissingFields(existingFields);
      if (nextMissing.isEmpty()) {
        return Collections.emptyList();
      }
      result.addAll(nextMissing);
    }
    return new LinkedList<>(result);
  }

  @NotNull
  @Override
  public List<Field> computeKeyCompletions(@Nullable YAMLMapping existingMapping) {
    Set<String> processedNames = new HashSet<>();
    LinkedHashSet<Field> result = new LinkedHashSet<>();
    for (YamlMetaType nextSubType : myTypes) {
      if (nextSubType instanceof YamlScalarType) {
        continue;
      }
      List<Field> subTypeCompletions = nextSubType.computeKeyCompletions(existingMapping);
      for (Field nextField : subTypeCompletions) {
        String nextFieldName = nextField.getName();
        if (!processedNames.contains(nextFieldName)) {
          Field mergedField = findFeatureByName(nextFieldName);
          processedNames.add(nextFieldName);
          result.add(mergedField);
        }
      }
    }
    return new LinkedList<>(result);
  }

  private boolean hasScalarSubtypes() {
    for (YamlMetaType type : myTypes) {
      if(type instanceof YamlScalarType)
        return true;

      if(type instanceof YamlComposedTypeBase && ((YamlComposedTypeBase)type).hasScalarSubtypes())
        return true;
    }

    return false;
  }

  @Override
  public void buildInsertionSuffixMarkup(@NotNull YamlInsertionMarkup markup,
                                         @NotNull Field.Relation relation,
                                         @NotNull ForcedCompletionPath.Iteration iteration) {

    if (relation == Field.Relation.SCALAR_VALUE ||
        (relation == Field.Relation.OBJECT_CONTENTS && hasScalarSubtypes())) {
      markup.append(": ");
    }
    else {
      markup.append(":");
      markup.increaseTabs(1);
      markup.newLineAndTabs(relation == Field.Relation.SEQUENCE_ITEM);
    }
    markup.appendCaret();
  }

  protected final Stream<YamlMetaType> streamSubTypes() {
    return myTypes.stream();
  }

  private static <T> List<T> copyList(@NotNull List<T> list) {
    return list.isEmpty() ? Collections.emptyList() : new ArrayList<>(list);
  }

  @Nullable
  private Field mergeFields(@NotNull String theName, @NotNull List<Field> fields) {
    switch (fields.size()) {
      case 0:
        return null;
      case 1:
        return fields.get(0);
    }

    Map<Boolean, List<YamlMetaType>> typesGroupedByMultiplicity = splitByMultiplicity(fields);

    // we will assume that "positive" field qualities are merged by ANY while "negative" qualities are merged by ALL
    boolean required = fields.stream().allMatch(f -> f.isRequired());
    boolean deprecated = fields.stream().allMatch(f -> f.isDeprecated());
    boolean editable = fields.stream().anyMatch(f -> f.isEditable());
    boolean emptyAllowed = fields.stream().anyMatch(f -> f.isEmptyValueAllowed());
    boolean anyName = fields.stream().allMatch(f -> f.isAnyNameAllowed());

    final List<YamlMetaType> singularTypes = typesGroupedByMultiplicity.get(false);
    YamlMetaType singularCompositeType = singularTypes != null && !singularTypes.isEmpty() ?
                                         composeTypes(singularTypes.toArray(new YamlMetaType[0])) : null;

    final List<YamlMetaType> sequentialTypes = typesGroupedByMultiplicity.get(true);
    YamlMetaType sequentialCompositeType = sequentialTypes != null && !sequentialTypes.isEmpty() ?
                                           composeTypes(sequentialTypes.toArray(new YamlMetaType[0])): null;

    assert singularCompositeType != null || sequentialCompositeType != null;

    Field result = new Field(theName, singularCompositeType != null ? singularCompositeType : sequentialCompositeType);

    if(singularCompositeType == null) {
      result.withMultiplicityMany();
    }
    else if(sequentialCompositeType != null) {
      result.withRelationSpecificType(Field.Relation.SEQUENCE_ITEM, sequentialCompositeType);
    }

    if (required) {
      result.setRequired();
    }
    if (deprecated) {
      result.setDeprecated();
    }
    if (!editable) {
      result.setNonEditable();
    }
    result.withEmptyValueAllowed(emptyAllowed);
    if (anyName) {
      result.withAnyName();
    }
    return result;
  }

  private static Map<Boolean, List<YamlMetaType>> splitByMultiplicity(@NotNull List<Field> fields) {
    return fields.stream().collect(
      Collectors.groupingBy(field -> field.isMany(),
                            Collectors.mapping(field -> field.getDefaultType(), Collectors.toList())));
  }

  protected static ProblemsHolder makeCopy(@NotNull ProblemsHolder original) {
    return new ProblemsHolder(original.getManager(), original.getFile(), original.isOnTheFly());
  }
}
