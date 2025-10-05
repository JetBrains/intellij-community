// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Allows to re-construct a {@link PyType} instance replacing some of its components.
 * <p>
 * Should be used as follows:
 * <pre>{@code
 * PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(typeEvalContext) {
 *   @Override
 *   public PyType visitPyClassType(@NotNull PyClassType classType) {
 *     if ("foo.Bar".equals(classType.getPyClass().getQualifiedName())) {
 *       PyClassType replacement = ... // Create a replacement type
 *       return replacement;
 *     }
 *     return clone(classType);
 *   }
 * });
 * }</pre>
 * <p>
 * If you need to recursively invoke cloning for nested types use {@link #clone(PyType)} instead of raw
 * {@code PyTypeVisitor.visitPyType(type, this)} or {@code type.acceptTypeVisitor(this)}.
 * This method has protection against recursive types and properly handles "unknown" nullable {@link PyType} instances.
 */
@ApiStatus.Experimental
public abstract class PyCloningTypeVisitor extends PyTypeVisitorExt<PyType> {
  private final @NotNull TypeEvalContext myTypeEvalContext;
  private final @NotNull Set<@Nullable PyType> cloning = new HashSet<>();

  public static @Nullable PyType clone(@Nullable PyType type, @NotNull PyCloningTypeVisitor visitor) {
    return visitor.clone(type);
  }

  protected PyCloningTypeVisitor(@NotNull TypeEvalContext context) {
    myTypeEvalContext = context;
  }

  // Intentionally not marked as @Nullable to avoid false positives. 
  // A recursive type is an exceptional case.
  protected <T extends PyType> T clone(@Nullable PyType type) {
    if (!cloning.add(type)) {
      return null;
    }
    try {
      //noinspection unchecked
      return (T)visit(type, this);
    }
    finally {
      cloning.remove(type);
    }
  }

  @Override
  public PyType visitPyLiteralType(@NotNull PyLiteralType literalType) {
    return literalType;
  }

  @Override
  public PyType visitPyLiteralStringType(@NotNull PyLiteralStringType literalStringType) {
    return literalStringType;
  }

  @Override
  public PyType visitPyModuleType(@NotNull PyModuleType moduleType) {
    return moduleType;
  }

  @Override
  public PyType visitPyParamSpecType(@NotNull PyParamSpecType paramSpecType) {
    return paramSpecType;
  }

  @Override
  public PyType visitPyGenericType(@NotNull PyCollectionType genericType) {
    return new PyCollectionTypeImpl(
      genericType.getPyClass(),
      genericType.isDefinition(),
      ContainerUtil.map(genericType.getElementTypes(), type -> clone(type))
    );
  }

  @Override
  public PyType visitPyTupleType(@NotNull PyTupleType tupleType) {
    return new PyTupleType(
      tupleType.getPyClass(),
      ContainerUtil.map(tupleType.getElementTypes(), type -> clone(type)),
      tupleType.isHomogeneous(),
      tupleType.isDefinition()
    );
  }

  @Override
  public PyType visitPyNamedTupleType(@NotNull PyNamedTupleType namedTupleType) {
    return namedTupleType;
  }

  @Override
  public PyType visitPySelfType(@NotNull PySelfType selfType) {
    return selfType;
  }

  @Override
  public PyType visitPyTypedDictType(@NotNull PyTypedDictType typedDictType) {
    // TODO Copied from PyTypeChecker.substitute, revise
    final var substitutedTDFields = typedDictType.getFields().entrySet().stream().collect(
      Collectors.toMap(
        Map.Entry::getKey,
        field -> new PyTypedDictType.FieldTypeAndTotality(
          field.getValue().getValue(),
          clone(field.getValue().getType()),
          new PyTypedDictType.TypedDictFieldQualifiers()
        )
      )
    );
    return new PyTypedDictType("TypedDict", substitutedTDFields, typedDictType.myClass, PyTypedDictType.DefinitionLevel.INSTANCE,
                               List.of());
  }

  @Override
  public PyType visitPyUnionType(@NotNull PyUnionType unionType) {
    return PyUnionType.union(ContainerUtil.map(unionType.getMembers(), type -> clone(type)));
  }

  @Override
  public PyType visitPyUnsafeUnionType(@NotNull PyUnsafeUnionType unsafeUnionType) {
    return PyUnsafeUnionType.unsafeUnion(ContainerUtil.map(unsafeUnionType.getMembers(), type -> clone(type)));
  }

  @Override
  public PyType visitPyTypingNewType(@NotNull PyTypingNewType typingNewType) {
    return typingNewType;
  }

  @Override
  public PyType visitPyNarrowedType(@NotNull PyNarrowedType narrowedType) {
    return narrowedType.substitute(clone(narrowedType.getNarrowedType()));
  }

  @Override
  public PyType visitPyConcatenateType(@NotNull PyConcatenateType concatenateType) {
    return new PyConcatenateType(
      ContainerUtil.map(concatenateType.getFirstTypes(), this::clone),
      clone(concatenateType.getParamSpec())
    );
  }

  @Override
  public PyType visitPyType(@NotNull PyType type) {
    return type;
  }

  @Override
  public PyType visitPyClassType(@NotNull PyClassType classType) {
    return classType;
  }

  @Override
  public PyType visitPyClassLikeType(@NotNull PyClassLikeType classLikeType) {
    return classLikeType;
  }

  @Override
  public PyType visitPyFunctionType(@NotNull PyFunctionType functionType) {
    // Create a new callable type for a function type. The constructor of PyFunctionType doesn't accept its return type explicitly.
    return super.visitPyFunctionType(functionType);
  }

  @Override
  public PyType visitPyCallableType(@NotNull PyCallableType callableType) {
    List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
    return new PyCallableTypeImpl(
      parameters != null ? ContainerUtil.map(parameters, parameter ->
        new PyCallableParameterImpl(
          parameter.getName(),
          Ref.create(clone(parameter.getType(myTypeEvalContext))),
          parameter.getDefaultValue(),
          parameter.getParameter(),
          parameter.isPositionalContainer(),
          parameter.isKeywordContainer(),
          parameter.getDeclarationElement()
        )) : null,
      clone(callableType.getReturnType(myTypeEvalContext)),
      callableType.getCallable(),
      callableType.getModifier(),
      callableType.getImplicitOffset()
    );
  }

  @Override
  public PyType visitPyTypeVarType(@NotNull PyTypeVarType typeVarType) {
    return typeVarType;
  }

  @Override
  public PyType visitPyTypeVarTupleType(@NotNull PyTypeVarTupleType typeVarTupleType) {
    return typeVarTupleType;
  }

  @Override
  public PyType visitPyTypeParameterType(@NotNull PyTypeParameterType typeParameterType) {
    return typeParameterType;
  }

  @Override
  public PyType visitPyUnpackedTupleType(@NotNull PyUnpackedTupleType unpackedTupleType) {
    return new PyUnpackedTupleTypeImpl(
      ContainerUtil.map(unpackedTupleType.getElementTypes(), this::clone),
      unpackedTupleType.isUnbound()
    );
  }

  @Override
  public PyType visitPyCallableParameterListType(@NotNull PyCallableParameterListType callableParameterListType) {
    return new PyCallableParameterListTypeImpl(
      ContainerUtil.map(callableParameterListType.getParameters(), parameter ->
        new PyCallableParameterImpl(
          parameter.getName(),
          Ref.create(clone(parameter.getType(myTypeEvalContext))),
          parameter.getDefaultValue(),
          parameter.getParameter(),
          parameter.isPositionalContainer(),
          parameter.isKeywordContainer(),
          parameter.getDeclarationElement()
        ))
    );
  }
}
