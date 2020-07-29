// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class YamlComposedTypeBase extends YamlMetaType {
  private final List<YamlMetaType> myTypes;
  private final Map<String, Field> myFields = new HashMap<>();

  protected static List<YamlMetaType> flattenTypes(YamlMetaType... types) {
    if (types.length == 0) {
      throw new IllegalArgumentException("Nothing to compose");
    }
    List<YamlMetaType> flattenedTypes = new SmartList<>();
    Set<YamlMetaType> cerber = new ReferenceOpenHashSet<>();
    for (YamlMetaType next : types) {
      if (!cerber.add(next)) {
        continue;
      }
      if (next instanceof YamlScalarType) {
        flattenedTypes.add(next);
      }
      else if (next instanceof YamlComposedTypeBase) {
        YamlComposedTypeBase that = (YamlComposedTypeBase)next;
        flattenedTypes.addAll(that.myTypes);
      }
      else {
        flattenedTypes.add(next);
      }
    }
    return flattenedTypes;
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
    if (!myFields.containsKey(name)) {
      List<Pair<Field, YamlMetaType>> fields = new SmartList<>();
      for (YamlMetaType nextSubType : myTypes) {
        if (nextSubType instanceof YamlScalarType) {
          continue;
        }
        Field nextField = nextSubType.findFeatureByName(name);
        if (nextField != null && !name.equals(nextField.getName())) {
          // any name?
          continue;
        }
        if (nextField != null) {
          fields.add(Pair.create(nextField, nextSubType));
        }
      }

      Field result = mergeFields(fields);
      myFields.put(name, result);
    }
    return myFields.get(name);
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

  @Override
  public void buildInsertionSuffixMarkup(@NotNull YamlInsertionMarkup markup,
                                         @NotNull Field.Relation relation,
                                         @NotNull ForcedCompletionPath.Iteration iteration) {

    if (relation == Field.Relation.SCALAR_VALUE ||
        (relation == Field.Relation.OBJECT_CONTENTS && !listScalarSubTypes().isEmpty())) {
      markup.append(": ");
    }
    else {
      markup.append(":");
      markup.increaseTabs(1);
      markup.newLineAndTabs(relation == Field.Relation.SEQUENCE_ITEM);
    }
    markup.appendCaret();
  }

  protected final List<YamlMetaType> listScalarSubTypes() {
    return ContainerUtil.filter(myTypes, next -> next instanceof YamlScalarType);
  }

  protected final List<YamlMetaType> listNonScalarSubTypes() {
    return ContainerUtil.filter(myTypes, next -> !(next instanceof YamlScalarType));
  }

  protected final Iterable<YamlMetaType> getSubTypes() {
    return myTypes;
  }

  protected final Stream<YamlMetaType> streamSubTypes() {
    return myTypes.stream();
  }

  private static <T> List<T> copyList(@NotNull List<T> list) {
    return list.isEmpty() ? Collections.emptyList() : new ArrayList<>(list);
  }

  @Nullable
  private Field mergeFields(@NotNull List<Pair<Field, YamlMetaType>> pairs) {
    switch (pairs.size()) {
      case 0:
        return null;
      case 1:
        return pairs.get(0).getFirst();
    }
    Set<String> allNames = pairs.stream().map(fieldAndType -> fieldAndType.getFirst().getName()).collect(Collectors.toSet());
    assert allNames.size() == 1 : "Can't merge fields with different names: " + allNames;
    String theName = pairs.get(0).getFirst().getName();

    boolean isMany = mergeIsMany(theName, pairs);

    List<Field> fields = ContainerUtil.map(pairs, pair -> pair.getFirst());
    // we will assume that "positive" field qualities are merged by ANY while "negative" qualities are merged by ALL
    boolean required = fields.stream().allMatch(f -> f.isRequired());
    boolean deprecated = fields.stream().allMatch(f -> f.isDeprecated());
    boolean editable = fields.stream().anyMatch(f -> f.isEditable());
    boolean emptyAllowed = fields.stream().anyMatch(f -> f.isEmptyValueAllowed());
    boolean anyName = fields.stream().anyMatch(f -> f.isAnyNameAllowed());

    YamlMetaType type = composeTypes(pairs.stream().map(p -> p.getSecond()).toArray(YamlMetaType[]::new));
    Field result = new Field(theName, type);
    result.withMultiplicityManyNotOne(isMany);
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

  private static boolean mergeIsMany(@NotNull String name, @NotNull List<Pair<Field, YamlMetaType>> fields) {
    // it is not clear how to merge fields with different multiplicities
    // so we will reject the merge if they don't match
    Map<Boolean, List<YamlMetaType>> byMultiplicity = fields.stream().collect(
      Collectors.groupingBy(fieldAndType -> fieldAndType.getFirst().isMany(),
                            Collectors.mapping(fieldAndType -> fieldAndType.getSecond(), Collectors.toList())));

    List<YamlMetaType> forMany = byMultiplicity.getOrDefault(Boolean.TRUE, Collections.emptyList());
    List<YamlMetaType> forSingle = byMultiplicity.getOrDefault(Boolean.FALSE, Collections.emptyList());
    if (!forMany.isEmpty() && !forSingle.isEmpty()) {
      throw new IllegalArgumentException("Can't merge field " + name + ", it is many for: " + forMany + " but singular for: " + forSingle);
    }
    return forSingle.isEmpty(); // isMany for all
  }

  protected static ProblemsHolder makeCopy(@NotNull ProblemsHolder original) {
    return new ProblemsHolder(original.getManager(), original.getFile(), original.isOnTheFly());
  }
}
