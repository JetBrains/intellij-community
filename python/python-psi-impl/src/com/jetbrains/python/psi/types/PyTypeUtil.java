/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Tools and wrappers around {@link PyType} inheritors
 *
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal
public final class PyTypeUtil {
  private PyTypeUtil() {
  }

  /**
   * Returns members of certain type from {@link PyClassLikeType}.
   */
  public static @NotNull <T extends PsiElement> List<T> getMembersOfType(final @NotNull PyClassLikeType type,
                                                                final @NotNull Class<T> expectedMemberType,
                                                                boolean inherited,
                                                                final @NotNull TypeEvalContext context) {

    final List<T> result = new ArrayList<>();
    type.visitMembers(t -> {
      if (expectedMemberType.isInstance(t)) {
        @SuppressWarnings("unchecked") // Already checked
        final T castedElement = (T)t;
        result.add(castedElement);
      }
      return true;
    }, inherited, context);
    return result;
  }


  /**
   * Search for data in dataholder or members of union recursively
   * @param type start point
   * @param key key to search
   * @param <T> result tyoe
   * @return data or null if not found
   */
  public static @Nullable <T> T findData(final @NotNull PyType type, final @NotNull Key<T> key) {
    if (type instanceof UserDataHolder) {
      return ((UserDataHolder)type).getUserData(key);
    }
    if (type instanceof PyUnionType unionType) {
      for (final PyType memberType : unionType.getMembers()) {
        if (memberType == null) {
          continue;
        }
        final T result = findData(memberType, key);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  public static @Nullable PyTupleType toPositionalContainerType(@NotNull PsiElement anchor, @Nullable PyType elementType) {
    if (elementType instanceof PyUnpackedTupleTypeImpl unpackedTupleType) {
      return unpackedTupleType.asTupleType(anchor);
    }
    else if (elementType instanceof PyTypeVarTupleType) {
      return PyTupleType.create(anchor, Collections.singletonList(elementType));
    }
    return PyTupleType.createHomogeneous(anchor, elementType);
  }

  public static @Nullable PyCollectionType toKeywordContainerType(@NotNull PsiElement anchor, @Nullable PyType valueType) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(anchor);

    return Optional
      .ofNullable(builtinCache.getDictType())
      .map(PyClassType::getPyClass)
      .map(dictClass -> new PyCollectionTypeImpl(dictClass, false, Arrays.asList(builtinCache.getStrType(), valueType)))
      .orElse(null);
  }

  /**
   * Given a type creates a stream of all its members if it's a union type or of only the type itself otherwise.
   * <p>
   * It allows to process types received as the result of multiresolve uniformly with the others.
   */
  public static @NotNull StreamEx<PyType> toStream(@Nullable PyType type) {
    if (type instanceof PyUnionType unionType) {
      return StreamEx.of(unionType.getMembers());
    }
    if (type instanceof PyUnsafeUnionType weakUnionType) {
      return StreamEx.of(weakUnionType.getMembers());
    }
    return StreamEx.of(type);
  }

  @Contract("null -> null; !null -> !null")
  public static @Nullable Ref<PyType> notNullToRef(@Nullable PyType type) {
    return type == null ? null : Ref.create(type);
  }

  /**
   * Returns a collector that combines a stream of {@code Ref<PyType>} back into a single {@code Ref<PyType>}
   * using {@link PyUnionType#union(PyType, PyType)}.
   *
   * @see #toUnion()
   */
  public static @NotNull Collector<Ref<PyType>, ?, Ref<PyType>> toUnionFromRef() {
    return toUnionFromRef(PyUnionType::union);
  }

  public static @NotNull Collector<Ref<PyType>, ?, Ref<PyType>> toUnsafeUnionFromRef() {
    return toUnionFromRef(PyUnsafeUnionType::unsafeUnion);
  }

  public static @NotNull Collector<Ref<PyType>, ?, Ref<PyType>> toUnionFromRef(@Nullable PyType streamSource) {
    return toUnionFromRef(streamSource instanceof PyUnsafeUnionType ? PyUnsafeUnionType::unsafeUnion : PyUnionType::union);
  }
  
  private static @NotNull Collector<Ref<PyType>, ?, Ref<PyType>> toUnionFromRef(@NotNull BinaryOperator<PyType> unionReduction) {
    return Collectors.reducing(null, (accType, hintType) -> {
      if (hintType == null) {
        return accType;
      }
      else if (accType == null) {
        return hintType;
      }
      else {
        return Ref.create(unionReduction.apply(accType.get(), hintType.get()));
      }
    });
  }

  /**
   * Returns a collector that combines a stream of types back into a single {@code PyType}
   * using {@link PyUnionType#union(java.util.Collection)}.
   * <p>
   * Note that it's different from using {@code foldLeft(PyUnionType::union)} because the latter returns {@code Optional<PyType>},
   * and it doesn't support {@code null} values throwing {@code NullPointerException} if the final result of
   * {@link PyUnionType#union(java.util.Collection)} was {@code null}.
   * <p>
   * This method doesn't distinguish between an empty stream and a stream containing only {@code null} returning {@code null} for both cases.
   *
   * @see #toUnionFromRef()
   */
  public static @NotNull Collector<@Nullable PyType, ?, @Nullable PyType> toUnion() {
    return Collectors.collectingAndThen(Collectors.toList(), PyUnionType::union);
  }

  public static @NotNull Collector<@Nullable PyType, ?, @Nullable PyType> toUnsafeUnion() {
    return Collectors.collectingAndThen(Collectors.toList(), PyUnsafeUnionType::unsafeUnion);
  }

  public static @NotNull Collector<@Nullable PyType, ?, @Nullable PyType> toUnion(@Nullable PyType streamSource) {
    return toUnion(streamSource instanceof PyUnsafeUnionType ? PyUnsafeUnionType::unsafeUnion : PyUnionType::union);
  }

  private static @NotNull Collector<@Nullable PyType, ?, @Nullable PyType> toUnion(@NotNull Function<List<@Nullable PyType>, @Nullable PyType> unionFactory) {
    return Collectors.collectingAndThen(Collectors.toList(), unionFactory);
  }

  public static boolean isDict(@Nullable PyType type) {
    return type instanceof PyCollectionType && "dict".equals(type.getName());
  }

  @ApiStatus.Internal
  public static @Nullable PyType getEffectiveBound(@NotNull PyTypeVarType typeVarType) {
    return typeVarType.getConstraints().isEmpty() ? typeVarType.getBound() : PyUnionType.union(typeVarType.getConstraints());
  }

  @ApiStatus.Internal
  public static @Nullable PyType convertToType(@Nullable PyType type,
                                               @NotNull String superTypeName,
                                               @NotNull PsiElement anchor,
                                               @NotNull TypeEvalContext context) {
    PyClass superClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(superTypeName, anchor);
    if (superClass == null) return null;
    PyClassType superClassType = ObjectUtils.notNull(PyTypeChecker.findGenericDefinitionType(superClass, context),
                                                     new PyClassTypeImpl(superClass, false));
    return PyTypeChecker.convertToType(type, superClassType, context);
  }
  
  public static boolean inheritsAny(@NotNull PyType type, @NotNull TypeEvalContext context) {
    return type instanceof PyClassLikeType classLikeType && classLikeType.getAncestorTypes(context).contains(null);
  }

  /**
   * Collects a set of types that participate in the textual type hint representation of {@code type}.
   * The returned set preserves a stable DFS order and is unmodifiable.
   */
  public static @NotNull @UnmodifiableView Set<PyType> collectTypeComponentsFromType(@Nullable PyType type,
                                                                                     @NotNull TypeEvalContext context) {
    Set<PyType> result = new LinkedHashSet<>();

    PyRecursiveTypeVisitor.traverse(type, context, new PyRecursiveTypeVisitor.PyTypeTraverser() {
      @Override
      public @NotNull PyRecursiveTypeVisitor.Traversal visitPyType(@NotNull PyType pyType) {
        result.add(pyType);
        return super.visitPyType(pyType);
      }

      @Override
      public PyRecursiveTypeVisitor.@NotNull Traversal visitPyLiteralType(@NotNull PyLiteralType literalType) {
        PyClassLikeType literalClassType = literalType.getPyClass().getType(context);
        if (literalClassType != null) {
          // Adds eg. signal.Handler when the given type was Literal[Handlers.SIG_DFL]
          result.add(literalClassType);
        }
        return super.visitPyLiteralType(literalType);
      }

      @Override
      public PyRecursiveTypeVisitor.@NotNull Traversal visitUnknownType() {
        result.add(null); // add Any type
        return super.visitUnknownType();
      }
    });

    return Collections.unmodifiableSet(result);
  }
}
