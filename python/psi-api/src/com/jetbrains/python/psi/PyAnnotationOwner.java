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
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public interface PyAnnotationOwner extends PsiElement {
  @Nullable
  PyAnnotation getAnnotation();

  /**
   * Returns the text of the annotation with the leading colon and arrow stripped.
   * <p>
   * It's supposed to be the same value as one can get by calling {@code elem.getAnnotation().getValue().getText()},
   * but taken from the corresponding stub instead of AST.
   */
  @Nullable
  default String getAnnotationValue() {
    return null;
  }
}
