/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
}
