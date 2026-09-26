/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.ast;


import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface PyAstExpressionCodeFragment extends PyAstFile, PsiCodeFragment {
  /**
   * Retrieves the real context of fragment, e.g., if fragment is breakpoint condition,
   * returns the file in which breakpoint is set.
   * On the `getContext()` may return a hidden file with imports (and real context is context of that file)
   *
   * @return the real context of the element, or null if there is no context
   */
  default @Nullable PsiElement getRealContext() {
    return getContext();
  }

  @Override
  default void forceResolveScope(GlobalSearchScope scope) {}

  @Override
  default GlobalSearchScope getForcedResolveScope() { return null; }
}
