// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.generate

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement

/**
 * Represents a factory for creating UastCommentSaver instances, which can be used to save and restore comments.
 *
 */
@ApiStatus.Experimental
interface UastCommentSaverFactory {
  companion object {
    private val extensionPointName = ExtensionPointName<UastCommentSaverFactory>("org.jetbrains.uast.generate.uastCommentSaverFactory")

    @JvmStatic
    fun byLanguage(language: Language): UastCommentSaverFactory? = extensionPointName.extensionList.firstOrNull { it.language == language }
  }

  /**
   * The underlying programming language.
   */
  val language: Language

  /**
   * Retrieves the comments associated with the given UElement.
   *
   * @return The UastCommentSaver containing the comments associated with the UElement, null if it is impossible to create
   */
  fun grabComments(firstResultUElement: UElement, lastResultUElement: UElement? = null): UastCommentSaver?

  /**
   * Represents an interface for restoring comments which are not included in resultUElements.
   */
  interface UastCommentSaver {
    fun restore(firstResultUElement: UElement, lastResultUElement: UElement? = null)
    fun markUnchanged(firstResultUElement: UElement?, lastResultUElement: UElement? = null)
  }
}