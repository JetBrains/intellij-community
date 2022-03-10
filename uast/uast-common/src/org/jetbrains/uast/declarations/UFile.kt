// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastTypedVisitor
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Represents a Uast file.
 */
interface UFile : UElement, UAnnotated {
  /**
   * Returns the original [PsiFile].
   */
  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("see the base property description")
  @Deprecated("see the base property description", ReplaceWith("javaPsi"))
  override val psi: PsiFile

  @JvmDefault
  @Suppress("DEPRECATION")
  override val sourcePsi: PsiFile
    get() = psi

  /**
   * Returns the Java package name of this file.
   * Returns an empty [String] for the default package.
   */
  val packageName: String

  /**
   * Returns the import statements for this file.
   */
  val imports: List<UImportStatement>

  /**
   * Returns the list of top-level classes declared in this file.
   */
  val classes: List<UClass>

  /**
   * Returns the plugin for a language used in this file.
   */
  val languagePlugin: UastLanguagePlugin

  /**
   * Returns all comments in file.
   */
  val allCommentsInFile: List<UComment>

  override fun asLogString(): String = log("package = $packageName")

  override fun asRenderString(): String = buildString {
    if (uAnnotations.isNotEmpty()) {
      uAnnotations.joinTo(buffer = this, separator = "\n", postfix = "\n", transform = UAnnotation::asRenderString)
    }

    val packageName = this@UFile.packageName
    if (packageName.isNotEmpty()) appendln("package $packageName").appendln()

    val imports = this@UFile.imports
    if (imports.isNotEmpty()) {
      imports.forEach { appendln(it.asRenderString()) }
      appendln()
    }

    classes.forEachIndexed { index, clazz ->
      if (index > 0) appendln()
      appendln(clazz.asRenderString())
    }
  }

  /**
   * [UFile] is a top-level element of the Uast hierarchy, thus the [uastParent] always returns null for it.
   */
  override val uastParent: UElement?
    get() = null

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitFile(this)) return
    uAnnotations.acceptList(visitor)
    imports.acceptList(visitor)
    classes.acceptList(visitor)
    visitor.afterVisitFile(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitFile(this, data)
}

