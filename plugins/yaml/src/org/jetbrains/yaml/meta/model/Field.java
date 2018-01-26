/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import javax.swing.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("UnusedReturnValue")
@ApiStatus.Experimental
public class Field {

  public enum Relation {
    SCALAR_VALUE,
    SEQUENCE_ITEM,
    OBJECT_CONTENTS
  }

  private final String myName;
  private final YamlMetaType myMainType;
  private boolean myIsRequired;
  private boolean myEditable = true;
  private boolean myDeprecated = false;
  private boolean myAnyNameAllowed;
  private boolean myAnyValueAllowed;
  private boolean myEmptyValueAllowed;
  private boolean myIsMany;
  private Relation myOverriddenDefaultRelation;

  private Map<Relation, YamlMetaType> myPerRelationTypes = new HashMap<>();

  public Field(@NotNull String name, @NotNull YamlMetaType mainType) {
    myName = name;
    myMainType = mainType;
  }

  @NotNull
  public Field withDefaultRelation(@NotNull Relation relation) {
    myOverriddenDefaultRelation = relation;
    return this;
  }

  public Field withRelationSpecificType(@NotNull Relation relation, @NotNull YamlMetaType specificType) {
    myPerRelationTypes.put(relation, specificType);
    return this;
  }

  @NotNull
  public Field withMultiplicityMany() {
    return withMultiplicityManyNotOne(true);
  }

  @NotNull
  public Field withMultiplicityManyNotOne(boolean manyNotOne) {
    myIsMany = manyNotOne;
    return this;
  }

  @Contract(pure = true)
  public boolean isMany() {
    return myIsMany;
  }

  @NotNull
  public Field setRequired() {
    myIsRequired = true;
    return this;
  }

  @NotNull
  public Field setDeprecated() {
    myDeprecated = true;
    return this;
  }

  /**
   * Marks the field non-editable. This is useful when the file content is not created initially by user, but rather machine-generated,
   * and contains fields not intended for editing, but still valid in terms of the data schema.
   * (This is very common for Kubernetes resource files, for example.)
   * Non-editable fields aren't included in completion lists. Also there is an inspection for highlighting such data.
   */
  @NotNull
  public Field setNonEditable() {
    myEditable = false;
    return this;
  }

  @Contract(pure = true)
  public final boolean isRequired() {
    return myIsRequired;
  }

  /**
   * Returns whether the field is editable. True by default
   *
   * @see #setNonEditable()
   */
  @Contract(pure = true)
  public final boolean isEditable() {
    return myEditable;
  }

  /**
   * Returns whether the field is deprecated. False by default
   *
   * @see #setDeprecated()
   */
  @Contract(pure = true)
  public boolean isDeprecated() {
    return myDeprecated;
  }

  @Contract(pure = true)
  public final String getName() {
    return myName;
  }

  @Contract(pure = true)
  @NotNull
  public YamlMetaType getType(@NotNull Relation relation) {
    return myPerRelationTypes.getOrDefault(relation, myMainType);
  }

  @Contract(pure = true)
  @NotNull
  public YamlMetaType getDefaultType() {
    return getType(getDefaultRelation());
  }

  /**
   * Returns the default relation between the field and its value. For mots normal fields it can be computed based on type and multiplicity
   * but for polymorphic fields the main relation should be assigned explicitly.
   */
  @NotNull
  public Relation getDefaultRelation() {
    if (myOverriddenDefaultRelation != null) {
      return myOverriddenDefaultRelation;
    }
    if (myIsMany) {
      return Relation.SEQUENCE_ITEM;
    }
    return myMainType instanceof YamlScalarType ? Relation.SCALAR_VALUE : Relation.OBJECT_CONTENTS;
  }

  @NotNull
  public Field withEmptyValueAllowed(boolean allow) {
    myEmptyValueAllowed = allow;
    return this;
  }

  @NotNull
  public final Field withAnyName() {
    return withAnyName(true);
  }

  @NotNull
  public Field withAnyName(boolean allowAnyName) {
    myAnyNameAllowed = allowAnyName;
    return this;
  }

  @NotNull
  public Field withAnyValue() {
    return withAnyValue(true);
  }

  @NotNull
  public Field withAnyValue(boolean allowOtherValues) {
    myAnyValueAllowed = allowOtherValues;
    return this;
  }

  public final boolean isAnyValueAllowed() {
    return myAnyValueAllowed;
  }

  public final boolean isAnyNameAllowed() {
    return myAnyNameAllowed;
  }

  public final boolean isEmptyValueAllowed() {
    return myEmptyValueAllowed;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("[").append(getName()).append("]@");
    result.append(Integer.toHexString(hashCode()));
    result.append(" : ");
    result.append(myMainType.getTypeName());

    List<String> nonDefaultTypes = myPerRelationTypes.entrySet().stream()
      .filter(e -> e.getValue() == myMainType)
      .map(e -> e.getKey() + ":" + e.getValue())
      .collect(Collectors.toList());

    if (!nonDefaultTypes.isEmpty()) {
      result.append(nonDefaultTypes);
    }
    return result.toString();
  }

  @NotNull
  public List<LookupElementBuilder> getKeyLookups(@NotNull PsiElement insertedScalar) {
    if (isAnyNameAllowed()) {
      return Collections.emptyList();
    }

    LookupElementBuilder lookup = LookupElementBuilder.create(getName())
      .withTypeText(myMainType.getDisplayName(), getLookupIcon(), true)
      .withStrikeoutness(isDeprecated());

    if (isRequired()) {
      lookup = lookup.bold();
    }
    return Collections.singletonList(lookup);
  }

  @Nullable
  public PsiReference getReferenceFromKey(@NotNull YAMLKeyValue keyValue) {
    return null;
  }

  public boolean hasRelationSpecificType(@NotNull Relation relation) {
    return relation == getDefaultRelation() || myPerRelationTypes.containsKey(relation);
  }

  @Nullable
  private Icon getLookupIcon() {
    if (myIsMany) {
      return AllIcons.Json.Array;
    }
    return null;
  }
}
