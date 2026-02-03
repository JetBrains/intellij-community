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
package org.jetbrains.uast

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult

interface UResolvable {
  /**
   * Resolve the reference.
   * Note that the reference is *always* resolved to an unwrapped [PsiElement], never to a [UElement].
   *
   * @return the resolved element, or null if the reference couldn't be resolved.
   */
  fun resolve(): PsiElement?
}

interface UMultiResolvable {
  /**
   * Returns multiple elements where the reference could be resolved.
   * It could happen if there is an ambiguity in code: it is incomplete or resolve target cannot be statically determined
   *
   * @see [com.intellij.psi.PsiPolyVariantReference] as a similar entity for plain PSI
   */
  fun multiResolve(): Iterable<ResolveResult>
}

fun UResolvable.resolveToUElement(): UElement? = resolve().toUElement()

inline fun <reified T : UElement> UResolvable.resolveToUElementOfType(): T? = resolveToUElement() as? T