// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyNamedTupleType extends PyTupleType implements PyCallableType {

  @NotNull
  private final String myName;

  @NotNull
  private final LinkedHashMap<String, FieldTypeAndDefaultValue> myFields;

  @NotNull
  private final DefinitionLevel myDefinitionLevel;

  private final boolean myTyped;
  private final PyTargetExpression myTargetExpression;

  public PyNamedTupleType(@NotNull PyClass tupleClass,
                          @NotNull String name,
                          @NotNull LinkedHashMap<String, FieldTypeAndDefaultValue> fields,
                          @NotNull DefinitionLevel definitionLevel,
                          boolean typed) {
    this(tupleClass, name, fields, definitionLevel, typed, null);
  }

  public PyNamedTupleType(@NotNull PyClass tupleClass,
                          @NotNull String name,
                          @NotNull LinkedHashMap<String, FieldTypeAndDefaultValue> fields,
                          @NotNull DefinitionLevel definitionLevel,
                          boolean typed,
                          @Nullable PyTargetExpression target) {
    super(tupleClass,
          Collections.unmodifiableList(ContainerUtil.map(fields.values(), typeAndValue -> typeAndValue.getType())),
          false,
          definitionLevel != DefinitionLevel.INSTANCE);

    myFields = new LinkedHashMap<>(fields);
    myName = name;
    myDefinitionLevel = definitionLevel;
    myTyped = typed;
    myTargetExpression = target;
  }

  @NotNull
  @Override
  public PyQualifiedNameOwner getDeclarationElement() {
    return myTargetExpression;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<Object> result = new ArrayList<>();
    Collections.addAll(result, super.getCompletionVariants(completionPrefix, location, context));
    for (String field : myFields.keySet()) {
      result.add(LookupElementBuilder.create(field));
    }
    return ArrayUtil.toObjectArray(result);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Nullable
  @Override
  public PyNamedTupleType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    if (myDefinitionLevel == DefinitionLevel.NT_FUNCTION) {
      return new PyNamedTupleType(myClass, myName, myFields, DefinitionLevel.NEW_TYPE, myTyped, myTargetExpression);
    }
    else if (myDefinitionLevel == DefinitionLevel.NEW_TYPE) {
      return getCallDefinitionType(callSite, context);
    }

    return null;
  }

  @NotNull
  @Override
  public PyNamedTupleType toInstance() {
    return myDefinitionLevel == DefinitionLevel.NEW_TYPE
           ? new PyNamedTupleType(myClass, myName, myFields, DefinitionLevel.INSTANCE, myTyped, myTargetExpression)
           : this;
  }

  @NotNull
  @Override
  public PyNamedTupleType toClass() {
    return myDefinitionLevel == DefinitionLevel.INSTANCE
           ? new PyNamedTupleType(myClass, myName, myFields, DefinitionLevel.NEW_TYPE, myTyped, myTargetExpression)
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
           Objects.equals(myFields.keySet(), type.myFields.keySet()) &&
           myDefinitionLevel == type.myDefinitionLevel;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myName, myFields.keySet(), myDefinitionLevel);
  }

  @NotNull
  @Override
  public Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
    final Set<String> result = super.getMemberNames(inherited, context);
    result.addAll(myFields.keySet());

    return result;
  }

  @NotNull
  public Map<String, FieldTypeAndDefaultValue> getFields() {
    return Collections.unmodifiableMap(myFields);
  }

  @Override
  public boolean isCallable() {
    return myDefinitionLevel != DefinitionLevel.INSTANCE;
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return isCallable()
           ? ContainerUtil.map(myFields.entrySet(), field -> fieldToCallableParameter(field.getKey(), field.getValue()))
           : null;
  }

  public boolean isTyped() {
    return myTyped;
  }

  @NotNull
  public PyNamedTupleType clarifyFields(@NotNull Map<String, PyType> fieldNameToType) {
    if (!myTyped) {
      final LinkedHashMap<String, FieldTypeAndDefaultValue> newFields = new LinkedHashMap<>(myFields);

      for (Map.Entry<String, PyType> entry : fieldNameToType.entrySet()) {
        final String fieldName = entry.getKey();

        if (newFields.containsKey(fieldName)) {
          newFields.put(fieldName, new FieldTypeAndDefaultValue(entry.getValue(), null));
        }
      }

      return new PyNamedTupleType(myClass, myName, newFields, myDefinitionLevel, false, myTargetExpression);
    }

    return this;
  }

  @NotNull
  private PyNamedTupleType getCallDefinitionType(@NotNull PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
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

  @NotNull
  private static PyCallableParameter fieldToCallableParameter(@NotNull String name, @NotNull FieldTypeAndDefaultValue typeAndDefaultValue) {
    return PyCallableParameterImpl.nonPsi(name, typeAndDefaultValue.getType(), typeAndDefaultValue.getDefaultValue());
  }

  public enum DefinitionLevel {

    NT_FUNCTION, // type for collections.namedtuple and typing.NamedTuple.__init__
    NEW_TYPE,
    INSTANCE
  }

  public static class FieldTypeAndDefaultValue {

    @Nullable
    private final PyType myType;

    @Nullable
    private final PyExpression myDefaultValue;

    public FieldTypeAndDefaultValue(@Nullable PyType type, @Nullable PyExpression defaultValue) {
      myType = type;
      myDefaultValue = defaultValue;
    }

    @Nullable
    public PyType getType() {
      return myType;
    }

    @Nullable
    public PyExpression getDefaultValue() {
      return myDefaultValue;
    }
  }
}
