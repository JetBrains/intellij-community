// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml

import com.intellij.lang.CompositeLanguage
import com.intellij.lang.Language
import org.jetbrains.annotations.NonNls

open class XMLLanguage :
  CompositeLanguage {

  private constructor() :
    super("XML", "application/xml", "text/xml")

  protected constructor(
    baseLanguage: Language,
    name: @NonNls String,
    vararg mimeTypes: String,
  ) :
    super(baseLanguage, name, *mimeTypes)

  companion object {
    @JvmField
    val INSTANCE: XMLLanguage = XMLLanguage()
  }
}
