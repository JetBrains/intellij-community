// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.toml.lang.psi.TomlTable

private val TOOL_POETRY_REGEX = """^tool.poetry(?:\.(.*))?$""".toRegex()

internal data class PoetryTomlTable(val header: String, val table: TomlTable)

internal fun PsiElement.getPoetryTomlTable(): PoetryTomlTable? {
  val tomlTable = PsiTreeUtil.getParentOfType(this, TomlTable::class.java) ?: return null
  val headerKeyText = tomlTable.header.key?.text ?: return null
  val headerMatchResult = TOOL_POETRY_REGEX.matchEntire(headerKeyText) ?: return null
  val (header) = headerMatchResult.destructured
  return PoetryTomlTable(header, tomlTable)
}
