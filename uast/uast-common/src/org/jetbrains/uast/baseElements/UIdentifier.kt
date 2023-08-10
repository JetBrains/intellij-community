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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.internal.log
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

open class UIdentifier(
  override val sourcePsi: PsiElement?,
  override val uastParent: UElement?
) : UElement {
  /**
   * Returns the identifier name.
   */
  open val name: String
    get() = sourcePsi?.text ?: "<error>"

  override fun asLogString(): String = log("Identifier ($name)")

  @Suppress("OverridingDeprecatedMember")
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("sourcePsi"))
  override val psi: PsiElement?
    get() = sourcePsi

  override val javaPsi: PsiElement?
    get() = null
}

open class LazyParentUIdentifier(psi: PsiElement?, givenParent: UElement?) : UIdentifier(psi, givenParent) {
  @Volatile
  private var uastParentValue: Any? = givenParent ?: NonInitializedLazyParentUIdentifierParent

  override val uastParent: UElement?
    get() {
      val currentValue = uastParentValue
      if (currentValue != NonInitializedLazyParentUIdentifierParent) {
        return currentValue as UElement?
      }

      val newValue = computeParent()
      if (updater.compareAndSet(this, NonInitializedLazyParentUIdentifierParent, newValue)) {
        return newValue
      }

      return uastParentValue as UElement?
    }

  protected open fun computeParent(): UElement? {
    return sourcePsi?.parent?.toUElement()
  }

  private companion object {
    val updater: AtomicReferenceFieldUpdater<LazyParentUIdentifier, Any> =
      AtomicReferenceFieldUpdater.newUpdater(
        LazyParentUIdentifier::class.java,
        Any::class.java,
        "uastParentValue"
      )

    val NonInitializedLazyParentUIdentifierParent = Any()
  }
}
