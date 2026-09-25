// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * A more descriptively named API alias for [PyTypeParser].
 */
@Suppress("DEPRECATION")
object PyLegacyDocstringTypeParser {
  @JvmStatic
  @Contract("null, _ -> null")
  fun getTypeByName(anchor: PsiElement?, type: String): PyType? =
    PyTypeParser.getTypeByName(anchor, type)

  @JvmStatic
  @Contract("null, _, _ -> null")
  fun getTypeByName(anchor: PsiElement?, type: String, context: TypeEvalContext): PyType? =
    PyTypeParser.getTypeByName(anchor, type, context)

  @JvmStatic
  @Contract("null, _, _, _ -> null")
  fun getTypeByName(anchor: PsiElement?, type: String, context: TypeEvalContext, fqnOnly: Boolean): PyType? =
    PyTypeParser.getTypeByName(anchor, type, context, fqnOnly)

  @JvmStatic
  fun parse(anchor: PsiElement, type: String): PyTypeParser.ParseResult =
    PyTypeParser.parse(anchor, type)

  @JvmStatic
  fun parse(anchor: PsiElement, type: String, context: TypeEvalContext): PyTypeParser.ParseResult =
    PyTypeParser.parse(anchor, type, context)

  @JvmStatic
  fun parse(anchor: PsiElement, type: String, context: TypeEvalContext, fqnOnly: Boolean): PyTypeParser.ParseResult =
    PyTypeParser.parse(anchor, type, context, fqnOnly)
}
