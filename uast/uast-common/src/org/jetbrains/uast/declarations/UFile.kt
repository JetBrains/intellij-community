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

import com.intellij.psi.PsiFile
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
  override val psi: PsiFile

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
    if (annotations.isNotEmpty()) {
      annotations.joinTo(buffer = this, separator = "\n", postfix = "\n", transform = UAnnotation::asRenderString)
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
    annotations.acceptList(visitor)
    imports.acceptList(visitor)
    classes.acceptList(visitor)
    visitor.afterVisitFile(this)
  }

  override fun <D, R> accept(visitor: UastTypedVisitor<D, R>, data: D): R =
    visitor.visitFile(this, data)
}

