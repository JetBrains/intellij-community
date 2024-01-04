// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.requirements

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon


class RequirementsFileType private constructor() : LanguageFileType(RequirementsLanguage.Companion.INSTANCE) {
  override fun getName(): String {
    return "Requirements.txt"
  }

  override fun getDescription(): String {
    return "Requirements.txt"
  }

  override fun getDefaultExtension(): String {
    return "txt"
  }

  override fun getIcon(): Icon? {
    return com.intellij.python.community.impl.requirements.RequirementsIcons.FILE
  }

  companion object {
    val INSTANCE = RequirementsFileType()
  }

}