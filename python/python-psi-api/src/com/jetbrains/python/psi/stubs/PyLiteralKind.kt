// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.jetbrains.python.psi.*
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.util.*

@ApiStatus.Internal
enum class PyLiteralKind {
  INT,
  FLOAT,
  STRING,
  BOOL,
  NONE;

  companion object {
    @JvmStatic
    fun fromExpression(expression: PyExpression?): PyLiteralKind? = when (expression) {
      is PyNumericLiteralExpression -> if (expression.isIntegerLiteral) INT else FLOAT
      is PyStringLiteralExpression -> STRING
      is PyBoolLiteralExpression -> BOOL
      is PyNoneLiteralExpression -> NONE
      else -> null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun serialize(stream: StubOutputStream, kind: PyLiteralKind?) {
      val index = kind?.ordinal ?: -1
      stream.writeVarInt(index)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyLiteralKind? {
      val index = stream.readVarInt()
      return if (index == -1) null else entries[index]
    }
  }
}
