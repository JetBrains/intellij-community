// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IconManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class PyNamedTupleType extends PyTupleType implements PyCallableType {
  private static final ImmutableSet<String> PYTHON_2_ATTRIBUTES = ImmutableSet.of("_make", "_asdict", "_replace", "_fields", "_field_defaults", "_field_types");
  private static final ImmutableSet<String> PYTHON_3_ATTRIBUTES = ImmutableSet.of("_make", "_asdict", "_replace", "_fields", "_field_defaults");
  public static @NotNull Set<String> getSpecialAttributes(LanguageLevel level) {
    if (level.isPy3K()) {
      return PYTHON_3_ATTRIBUTES;
    } else {
      return PYTHON_2_ATTRIBUTES;
    }
  }

  private final @NotNull String myName;

  private final @NotNull LinkedHashMap<String, FieldTypeAndDefaultValue> myFields;

  private final boolean myTyped;

  private final @Nullable PyQualifiedNameOwner myDeclaration;

  public PyNamedTupleType(@NotNull PyClass tupleClass,
                          @NotNull String name,
                          @NotNull LinkedHashMap<String, FieldTypeAndDefaultValue> fields,
                          boolean isDefinition,
                          boolean typed,
                          @Nullable PyQualifiedNameOwner declaration) {
    super(tupleClass,
          ContainerUtil.map(fields.values(), typeAndValue -> typeAndValue.getType()),
          false,
          isDefinition);

    myFields = new LinkedHashMap<>(fields);
    myName = name;
    myTyped = typed;
    myDeclaration = declaration;
  }

  @Override
  public @NotNull PyQualifiedNameOwner getDeclarationElement() {
    return ObjectUtils.notNull(myDeclaration, super::getDeclarationElement);
  }

  @Override
  public Object @NotNull [] getCompletionVariants(String completionPrefix, PsiElement location, @NotNull ProcessingContext context) {
    final List<Object> result = new ArrayList<>();
    Collections.addAll(result, super.getCompletionVariants(completionPrefix, location, context));

    for (String field : myFields.keySet()) {
      result.add(LookupElementBuilder.create(field).withIcon(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Field)));
    }

    if (completionPrefix == null) {
      final Condition<String> nameFilter = getSpecialAttributes(LanguageLevel.forElement(location))::contains;
      final CompletionVariantsProcessor processor =
        new CompletionVariantsProcessor(location, null, nameFilter, false, context.get(CTX_SUPPRESS_PARENTHESES) != null);

      myClass.processClassLevelDeclarations(processor);

      result.addAll(processor.getResultList());
    }

    return ArrayUtil.toObjectArray(result);
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public @Nullable PyNamedTupleType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    if (isDefinition()) {
      return getCallDefinitionType(callSite, context);
    }

    return null;
  }

  @Override
  public @NotNull PyNamedTupleType toInstance() {
    return isDefinition()
           ? new PyNamedTupleType(myClass, myName, myFields, false, myTyped, myDeclaration)
           : this;
  }

  @Override
  public @NotNull PyNamedTupleType toClass() {
    return !isDefinition()
           ? new PyNamedTupleType(myClass, myName, myFields, true, myTyped, myDeclaration)
           : this;
  }

  @Override
  public String toString() {
    return "PyNamedTupleType: " + myName;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final PyNamedTupleType type = (PyNamedTupleType)o;
    return Objects.equals(myName, type.myName) &&
           Objects.equals(myFields.keySet(), type.myFields.keySet());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myName, myFields.keySet());
  }

  @Override
  public @NotNull Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
    final Set<String> result = super.getMemberNames(inherited, context);
    result.addAll(myFields.keySet());

    return result;
  }

  public @NotNull Map<String, FieldTypeAndDefaultValue> getFields() {
    return Collections.unmodifiableMap(myFields);
  }

  @Override
  public boolean isCallable() {
    return isDefinition();
  }

  @Override
  public @Nullable List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return isCallable()
           ? ContainerUtil.map(myFields.entrySet(), field -> fieldToCallableParameter(field.getKey(), field.getValue()))
           : null;
  }

  public boolean isTyped() {
    return myTyped;
  }

  public @NotNull PyNamedTupleType clarifyFields(@NotNull Map<String, PyType> fieldNameToType) {
    if (!myTyped) {
      final LinkedHashMap<String, FieldTypeAndDefaultValue> newFields = new LinkedHashMap<>(myFields);

      for (Map.Entry<String, PyType> entry : fieldNameToType.entrySet()) {
        final String fieldName = entry.getKey();

        if (newFields.containsKey(fieldName)) {
          newFields.put(fieldName, new FieldTypeAndDefaultValue(entry.getValue(), null));
        }
      }

      return new PyNamedTupleType(myClass, myName, newFields, isDefinition(), false, myDeclaration);
    }

    return this;
  }

  private @NotNull PyNamedTupleType getCallDefinitionType(@NotNull PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    if (!myTyped) {
      final List<PyExpression> arguments = callSite.getArguments(null);

      if (arguments.size() == myFields.size()) {
        final Map<String, PyType> result = new HashMap<>();

        for (Map.Entry<String, PyExpression> entry : StreamEx.ofKeys(myFields).zipWith(StreamEx.of(arguments))) {
          final String name = entry.getKey();
          final PyType type = context.getType(entry.getValue());

          result.put(name, type);
        }

        return toInstance().clarifyFields(result);
      }
    }

    return toInstance();
  }

  private static @NotNull PyCallableParameter fieldToCallableParameter(@NotNull String name, @NotNull FieldTypeAndDefaultValue typeAndDefaultValue) {
    return PyCallableParameterImpl.nonPsi(name, typeAndDefaultValue.getType(), typeAndDefaultValue.getDefaultValue());
  }

  public static class FieldTypeAndDefaultValue {

    private final @Nullable PyType myType;

    private final @Nullable PyExpression myDefaultValue;

    public FieldTypeAndDefaultValue(@Nullable PyType type, @Nullable PyExpression defaultValue) {
      myType = type;
      myDefaultValue = defaultValue;
    }

    public @Nullable PyType getType() {
      return myType;
    }

    public @Nullable PyExpression getDefaultValue() {
      return myDefaultValue;
    }
  }

  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
      return visitorExt.visitPyNamedTupleType(this);
    }
    return visitor.visitPyClassType(this);
  }
}
