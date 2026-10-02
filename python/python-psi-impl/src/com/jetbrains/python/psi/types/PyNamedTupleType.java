// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;


public class PyNamedTupleType extends PyTupleType implements PyCallableType {
  private static final ImmutableSet<@NotNull String> PYTHON_2_ATTRIBUTES =
    ImmutableSet.of("_make", "_asdict", "_replace", "_fields", "_field_defaults", "_field_types");
  private static final ImmutableSet<@NotNull String> PYTHON_3_ATTRIBUTES =
    ImmutableSet.of("_make", "_asdict", "_replace", "_fields", "_field_defaults");

  public static @NotNull Set<String> getSpecialAttributes(LanguageLevel level) {
    if (level.isPy3K()) {
      return PYTHON_3_ATTRIBUTES;
    }
    else {
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
    this(tupleClass, name, fields, isDefinition, typed, declaration, Collections.emptyList());
  }

  /**
   * @param typeArguments the type arguments of a generic named tuple class, such as {@code [str]} for {@code Base[str]}.
   *                      The field types must already have these type arguments applied.
   */
  public PyNamedTupleType(@NotNull PyClass tupleClass,
                          @NotNull String name,
                          @NotNull LinkedHashMap<String, FieldTypeAndDefaultValue> fields,
                          boolean isDefinition,
                          boolean typed,
                          @Nullable PyQualifiedNameOwner declaration,
                          @NotNull List<? extends PyType> typeArguments) {
    super(tupleClass,
          ContainerUtil.map(fields.values(), typeAndValue -> typeAndValue.getType()),
          false,
          isDefinition,
          typeArguments);

    myFields = new LinkedHashMap<>(fields);
    myName = name;
    myTyped = typed;
    myDeclaration = declaration;
  }

  /**
   * The type arguments of the named tuple class, as in {@code Base[str]}. They are empty for a named tuple that is not generic,
   * or not parameterized. Use {@link #getElementTypes()} or {@link #getFields()} for the field types.
   */
  @Override
  public @NotNull List<PyType> getTypeArguments() {
    return Collections.unmodifiableList(myTypeArguments);
  }

  /**
   * Returns a copy of this named tuple. The copy applies {@code fieldTypeMapper} to each field type,
   * and {@code typeArgumentsMapper} to the list of type arguments.
   */
  public @NotNull PyNamedTupleType mapTypes(@NotNull Function<? super PyType, ? extends PyType> fieldTypeMapper,
                                            @NotNull Function<? super List<PyType>, ? extends List<? extends PyType>> typeArgumentsMapper) {
    final LinkedHashMap<String, FieldTypeAndDefaultValue> newFields = new LinkedHashMap<>();
    for (Map.Entry<String, FieldTypeAndDefaultValue> entry : myFields.entrySet()) {
      final FieldTypeAndDefaultValue field = entry.getValue();
      newFields.put(entry.getKey(), new FieldTypeAndDefaultValue(fieldTypeMapper.apply(field.getType()), field.getDefaultValue()));
    }
    return withUserDataCopy(new PyNamedTupleType(myClass, myName, newFields, isDefinition(), myTyped, myDeclaration,
                                                 typeArgumentsMapper.apply(getTypeArguments())));
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
      result.add(
        LookupElementBuilder.create(field).withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Field)));
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
  public @Nullable PyNamedTupleType getCallType(@NotNull TypeEvalContext context, @NotNull PyElement callSite) {
    return isDefinition() ? toInstance() : null;
  }

  @Override
  public @Nullable PyNamedTupleType getCallType(@NotNull TypeEvalContext context,
                                                @Nullable PyElement callSite,
                                                @NotNull List<PyCallableArgument> arguments) {
    if (isDefinition()) {
      return getCallDefinitionType(arguments, context);
    }

    return null;
  }

  @Override
  public @NotNull PyNamedTupleType toInstance() {
    return isDefinition()
           ? new PyNamedTupleType(myClass, myName, myFields, false, myTyped, myDeclaration, myTypeArguments)
           : this;
  }

  @Override
  public @NotNull PyNamedTupleType toClass() {
    return !isDefinition()
           ? new PyNamedTupleType(myClass, myName, myFields, true, myTyped, myDeclaration, myTypeArguments)
           : this;
  }

  @Override
  public String toString() {
    return "PyNamedTupleType: " + myName + (myTypeArguments.isEmpty() ? "" : "[" + StringUtil.join(myTypeArguments, String::valueOf, ", ") + "]");
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

  @Override
  public @Nullable PyCallableParameterVariadicType getParametersType(@NotNull TypeEvalContext context) {
    List<PyCallableParameter> parameters = getParameters(context);
    return parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null;
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

      return new PyNamedTupleType(myClass, myName, newFields, isDefinition(), false, myDeclaration, myTypeArguments);
    }

    return this;
  }

  private @NotNull PyNamedTupleType getCallDefinitionType(@NotNull List<PyCallableArgument> arguments,
                                                          @NotNull TypeEvalContext context) {
    if (!myTyped) {
      if (arguments.size() == myFields.size()) {
        final Map<String, PyType> result = new HashMap<>();

        for (Map.Entry<String, PyCallableArgument> entry : StreamEx.ofKeys(myFields).zipWith(StreamEx.of(arguments))) {
          final String name = entry.getKey();
          final PyType type = entry.getValue().getType(context);

          result.put(name, type);
        }

        return toInstance().clarifyFields(result);
      }
    }

    return toInstance();
  }

  private static @NotNull PyCallableParameter fieldToCallableParameter(@NotNull String name,
                                                                       @NotNull FieldTypeAndDefaultValue typeAndDefaultValue) {
    return PyCallableParameterImpl.nonPsi(name, typeAndDefaultValue.getType(), typeAndDefaultValue.getDefaultValue());
  }

  public static class FieldTypeAndDefaultValue {

    private final @Nullable PyType myType;

    private final @Nullable PyExpression myDefaultValue;

    public FieldTypeAndDefaultValue(@Nullable PyType type, @Nullable PyExpression defaultValue) {
        PyAnyType.validate(type);
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
