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
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class GenericTypeUtil {

  public static GenericType fromStreamPsiType(@NotNull PsiType streamPsiType) {
    if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) return GenericType.INT;
    if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) return GenericType.LONG;
    if (InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) return GenericType.DOUBLE;
    if (PsiType.VOID.equals(streamPsiType)) return GenericType.VOID;

    return GenericType.OBJECT;
  }

  public static GenericType fromPsiType(@NotNull PsiType type) {
    if (PsiType.VOID.equals(type)) return GenericType.VOID;
    if (PsiType.INT.equals(type)) return GenericType.INT;
    if (PsiType.DOUBLE.equals(type)) return GenericType.DOUBLE;
    if (PsiType.LONG.equals(type)) return GenericType.LONG;
    if (PsiType.BOOLEAN.equals(type)) return GenericType.BOOLEAN;
    return GenericType.OBJECT;
  }
}
