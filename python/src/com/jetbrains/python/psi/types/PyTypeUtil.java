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
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Tools and wrappers around {@link PyType} inheritors
 *
 * @author Ilya.Kazakevich
 */
public final class PyTypeUtil {
  private PyTypeUtil() {
  }

  /**
   * Returns members of certain type from {@link PyClassLikeType}.
   */
  @NotNull
  public static <T extends PsiElement> List<T> getMembersOfType(@NotNull final PyClassLikeType type,
                                                                @NotNull final Class<T> expectedMemberType,
                                                                boolean inherited,
                                                                @NotNull final TypeEvalContext context) {

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
  @Nullable
  public static <T> T findData(@NotNull final PyType type, @NotNull final Key<T> key) {
    if (type instanceof UserDataHolder) {
      return ((UserDataHolder)type).getUserData(key);
    }
    if (type instanceof PyUnionType) {
      for (final PyType memberType : ((PyUnionType)type).getMembers()) {
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

  @Nullable
  public static PyTupleType toPositionalContainerType(@NotNull PsiElement anchor, @Nullable PyType elementType) {
    return PyTupleType.createHomogeneous(anchor, elementType);
  }

  @Nullable
  public static PyCollectionType toKeywordContainerType(@NotNull PsiElement anchor, @Nullable PyType valueType) {
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
  @NotNull
  public static StreamEx<PyType> toStream(@Nullable PyType type) {
    if (type instanceof PyUnionType) {
      return StreamEx.of(((PyUnionType)type).getMembers());
    }
    return StreamEx.of(type);
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public static Ref<PyType> notNullToRef(@Nullable PyType type) {
    return type == null ? null : Ref.create(type);
  }

  /**
   * Returns a collector that combines a stream of {@code Ref<PyType>} back into a single {@code Ref<PyType>}
   * using {@link PyUnionType#union(PyType, PyType)}.
   *
   * @see #toUnion()
   */
  @NotNull
  public static Collector<Ref<PyType>, ?, Ref<PyType>> toUnionFromRef() {
    return Collectors.reducing(null, (accType, hintType) -> {
      if (hintType == null) {
        return accType;
      }
      else if (accType == null) {
        return hintType;
      }
      else {
        return Ref.create(PyUnionType.union(accType.get(), hintType.get()));
      }
    });
  }

  /**
   * Returns a collector that combines a stream of types back into a single {@code Ref<PyType>}
   * using {@link PyUnionType#union(PyType, PyType)}.
   * <p>
   * Note that it's different from using {@code foldLeft(PyUnionType::union)} because the latter returns {@code Optional<PyType>},
   * and it doesn't support {@code null} values throwing {@code NullPointerException} if the final result of
   * {@link PyUnionType#union(PyType, PyType)} was {@code null}. This is why this method returns {@code Ref<PyType>} instead,
   * because with {@code Optional} it's impossible to distinguish between an empty stream of types and a stream containing only
   * {@code Any}.
   *
   * @see #toUnionFromRef()
   */
  @NotNull
  public static Collector<PyType, ?, Ref<PyType>> toUnion() {
    return Collectors.reducing(null, Ref::create, (accType, hintType) -> {
      if (accType == null) {
        return hintType;
      }
      else {
        return Ref.create(PyUnionType.union(accType.get(), hintType.get()));
      }
    });
  }
}
