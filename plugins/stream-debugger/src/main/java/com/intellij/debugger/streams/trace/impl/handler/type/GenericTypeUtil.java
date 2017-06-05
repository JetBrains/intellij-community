/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.trace.impl.handler.type;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Vitaliy.Bibaev
 */
public class GenericTypeUtil {
  private static final Set<GenericType> OPTIONAL_TYPES = StreamEx
    .of(GenericType.OPTIONAL, GenericType.OPTIONAL_INT, GenericType.OPTIONAL_LONG, GenericType.OPTIONAL_DOUBLE)
    .toSet();

  @NotNull
  public static GenericType fromStreamPsiType(@NotNull PsiType streamPsiType) {
    if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) return GenericType.INT;
    if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) return GenericType.LONG;
    if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) return GenericType.DOUBLE;
    if (PsiType.VOID.equals(streamPsiType)) return GenericType.VOID;

    return GenericType.OBJECT;
  }

  @NotNull
  public static GenericType fromPsiType(@NotNull PsiType type) {
    if (PsiType.VOID.equals(type)) return GenericType.VOID;
    if (PsiType.INT.equals(type)) return GenericType.INT;
    if (PsiType.DOUBLE.equals(type)) return GenericType.DOUBLE;
    if (PsiType.LONG.equals(type)) return GenericType.LONG;
    if (PsiType.BOOLEAN.equals(type)) return GenericType.BOOLEAN;
    return new ClassTypeImpl(TypeConversionUtil.erasure(type).getCanonicalText());
  }

  public static boolean isOptional(@NotNull GenericType type) {
    return OPTIONAL_TYPES.contains(type);
  }

  @NotNull
  public static GenericType unwrapOptional(@NotNull GenericType type) {
    assert isOptional(type);

    if (type.equals(GenericType.OPTIONAL_INT)) {
      return GenericType.INT;
    }

    if (type.equals(GenericType.OPTIONAL_LONG)) {
      return GenericType.DOUBLE;
    }

    if (type.equals(GenericType.OPTIONAL_DOUBLE)) {
      return GenericType.DOUBLE;
    }

    return GenericType.OBJECT;
  }
}
