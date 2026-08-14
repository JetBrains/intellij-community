// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.google.common.collect.Sets;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
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
  private final @NotNull Set<@Nullable PyType> cloning = Sets.newIdentityHashSet();
  private final @NotNull Map<@Nullable PyType, @Nullable PyType> cloned = new IdentityHashMap<>();

  public static @Nullable PyType clone(@Nullable PyType type, @NotNull PyCloningTypeVisitor visitor) {
    return visitor.clone(type);
  }

  protected PyCloningTypeVisitor(@NotNull TypeEvalContext context) {
    myTypeEvalContext = context;
  }

  // Intentionally not marked as @Nullable to avoid false positives. 
  // A recursive type is an exceptional case.
  protected final <T extends PyType> T clone(@Nullable PyType type) {
    //noinspection unchecked
    return (T)doClone(type);
  }

  private @Nullable PyType doClone(@Nullable PyType type) {
    PyAnyType.validate(type);
    if (cloned.containsKey(type)) {
      return cloned.get(type);
    }
    if (!cloning.add(type)) {
      // The "unknown" breaking the cycle is valid for this path only, so it is deliberately not memoized: a type whose
      // components are cloned lazily asks for the same type again outside of any cycle, and has to get its real clone.
      return PyAnyType.getUnknown();
    }
    try {
      final PyType result = visit(type, this);
      PyAnyType.validate(result);
      cloned.put(type, result);
      return result;
    }
    finally {
      cloning.remove(type);
    }
  }

  /**
   * Runs {@code body} as the start of a fresh traversal: a component cloned lazily runs on an arbitrary stack, possibly nested
   * inside the cloning of a type it legitimately has to clone itself. The memo of cloned types is kept, so nothing is repeated.
   * <p>
   * Synchronized because deferring makes the visitor outlive its traversal: the lazy components of one cloned type may be asked
   * for from several threads, and they share its memo and cycle-detection state.
   */
  protected final synchronized <T> T cloneDeferred(@NotNull Supplier<T> body) {
    final List<PyType> suspended = new ArrayList<>(cloning);
    cloning.clear();
    try {
      return body.get();
    }
    finally {
      cloning.addAll(suspended);
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
    // Cloned lazily, so that cloning a TypedDict does not force item types that may refer back to it.
    return new PyTypedDictType(
      typedDictType.getName(),
      () -> cloneFields(typedDictType),
      typedDictType.myClass,
      typedDictType.isDefinition(),
      typedDictType.getDeclarationElement(),
      typedDictType.isClosed(),
      () -> cloneDeferred(() -> clone(typedDictType.getExtraItemsType())),
      typedDictType.getExtraItemsQualifiers(),
      typedDictType.getDeclaredTypeParameters(),
      cloneTypedDictTypeArguments(typedDictType));
  }

  /** Clones the arguments a TypedDict already has. Substitution overrides this to parameterize a generic one. */
  protected @NotNull List<PyType> cloneTypedDictTypeArguments(@NotNull PyTypedDictType typedDictType) {
    return cloneTypeArguments(typedDictType.getSubstitutedTypeArguments());
  }

  private @NotNull Map<String, PyTypedDictType.FieldTypeAndTotality> cloneFields(@NotNull PyTypedDictType typedDictType) {
    // TODO Copied from PyTypeChecker.substitute, revise
    return cloneDeferred(() -> typedDictType.getFields().entrySet().stream().collect(
      Collectors.toMap(
        Map.Entry::getKey,
        field -> new PyTypedDictType.FieldTypeAndTotality(
          field.getValue().getValue(),
          clone(field.getValue().getType()),
          field.getValue().getQualifiers()
        )
      )
    ));
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
  public PyType visitPyIntersectionType(@NotNull PyIntersectionType intersectionType) {
    return PyIntersectionType.intersection(ContainerUtil.map(intersectionType.getMembers(), type -> clone(type)));
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
  public PyType visitPyTypeFormType(@NotNull PyTypeFormType typeFormType) {
    return typeFormType.substitute(clone(typeFormType.getRepresentedType()));
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
    if (!classType.isParameterized()) {
      return classType;
    }
    if (classType instanceof PyClassTypeImpl impl) {
      return impl.withUserDataCopy(
        impl.createInstance(
          classType.getPyClass(),
          classType.isDefinition(),
          cloneTypeArguments(classType.getTypeArguments())
        )
      );
    }
    return new PyCollectionTypeImpl(
      classType.getPyClass(),
      classType.isDefinition(),
      cloneTypeArguments(classType.getTypeArguments())
    );
  }

  /** Overridable because substitution has to normalize the result — flattening an unpacked tuple, for one — everywhere alike. */
  protected @NotNull List<PyType> cloneTypeArguments(@NotNull List<PyType> typeArguments) {
    return ContainerUtil.map(typeArguments, type -> clone(type));
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
    // Cloning a recursive type yields PyAnyType.unknown (see doClone), which is not a
    // PyCallableParameterVariadicType. Use a safe cast so that case degrades to null params instead of a
    // ClassCastException once `python.type.any` is enabled (it was null when disabled).
    var parametersType = callableType.getParametersType(myTypeEvalContext);
    final PyType clonedParams = parametersType != null ? clone(parametersType) : null;
    final PyCallableParameterVariadicType clonedParametersType =
      clonedParams instanceof PyCallableParameterVariadicType variadic ? variadic : null;
    var typeParameters = callableType.getTypeParameters(myTypeEvalContext);
    return new PyCallableTypeImpl(
      typeParameters != null ? ContainerUtil.map(typeParameters, this::clone) : null,
      clonedParametersType,
      clone(callableType.getReturnType(myTypeEvalContext)),
      callableType.getCallable(),
      callableType.getModifier()
    );
  }

  @Override
  public PyType visitPyOverloadType(@NotNull PyOverloadType overloadType) {
    return new PyOverloadType(ContainerUtil.mapNotNull(overloadType.getItems(), this::clone), overloadType.getImpl());
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
          parameter.getDefaultValueText(),
          parameter.getParameter(),
          parameter.isPositionalContainer(),
          parameter.isKeywordContainer(),
          parameter.isSelf(),
          parameter.isKeywordOnlySeparator(),
          parameter.isPositionOnlySeparator(),
          parameter.getDeclarationElement()
        ))
    );
  }

  @Override
  public PyType visitPyUnpackedTypedDictType(@NotNull PyUnpackedTypedDictType unpackedTypedDictType) {
    return new PyUnpackedTypedDictTypeImpl(clone(unpackedTypedDictType.getTypedDictType()));
  }
}
