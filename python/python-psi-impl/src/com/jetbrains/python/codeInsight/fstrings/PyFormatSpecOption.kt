// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.fstrings

import com.intellij.openapi.util.Key
import com.jetbrains.python.PyPsiBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

/** The kind of a format spec option, used to group the catalog. */
enum class PyFormatSpecCategory(private val labelSupplier: Supplier<@Nls String>) {
  ALIGNMENT(PyPsiBundle.messagePointer("fstring.format.spec.doc.category.alignment")),
  SIGN(PyPsiBundle.messagePointer("fstring.format.spec.doc.category.sign")),
  FLAG(PyPsiBundle.messagePointer("fstring.format.spec.doc.category.flag")),
  PRECISION(PyPsiBundle.messagePointer("fstring.format.spec.doc.category.precision")),
  GROUPING(PyPsiBundle.messagePointer("fstring.format.spec.doc.category.grouping")),
  TYPE(PyPsiBundle.messagePointer("fstring.format.spec.doc.category.type")),
  DATETIME(PyPsiBundle.messagePointer("fstring.format.spec.doc.category.datetime")),
  ;

  /** The human-readable name of the category. */
  val label: @Nls String get() = labelSupplier.get()
}

/**
 * One option of the format mini-language, with its documentation.
 *
 * The descriptions resolve from `PyPsiBundle` on demand, so building the catalog does not read the whole
 * bundle while the class initializes.
 */
class PyFormatSpecOption internal constructor(
  val spec: String,
  val category: PyFormatSpecCategory,
  private val shortDescriptionSupplier: Supplier<@Nls String>,
  private val fullDescriptionSupplier: Supplier<@Nls String>,
  /** A canonical one-line example of the option, or `null` when it has none. */
  val example: @NonNls String?,
) {
  val shortDescription: @Nls String get() = shortDescriptionSupplier.get()
  val fullDescription: @Nls String get() = fullDescriptionSupplier.get()

  override fun toString(): String = "PyFormatSpecOption($spec, $category)"
}

/**
 * Key that carries the option a format-spec lookup element stands for, so the documentation provider can
 * describe the element without parsing its text again.
 */
val FORMAT_SPEC_OPTION_KEY: Key<PyFormatSpecOption> = Key.create("py.fstring.format.spec.option")
