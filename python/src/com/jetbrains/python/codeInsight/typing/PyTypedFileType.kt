// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.jetbrains.python.PyBundle
import javax.swing.Icon

internal object PyTypedFileType : LanguageFileType(PlainTextLanguage.INSTANCE, true) {
  override fun getName(): String = "PyTyped"

  override fun getDescription(): String = PyBundle.message("filetype.py.typed.description")

  override fun getDisplayName(): String = PyBundle.message("filetype.py.typed.display.name")

  override fun getDefaultExtension(): String = "typed"

  override fun getIcon(): Icon = AllIcons.FileTypes.Text
}
