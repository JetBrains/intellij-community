// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.html

import com.intellij.lang.Language
import com.intellij.lang.xml.XMLLanguage
import org.jetbrains.annotations.NonNls

open class HTMLLanguage :
  XMLLanguage {

  private constructor() : super(
    baseLanguage = XMLLanguage.INSTANCE,
    name = "HTML",
    mimeTypes = arrayOf(
      "text/html",
      "text/htmlh",
    )
  )

  protected constructor(
    baseLanguage: Language,
    name: @NonNls String,
    vararg mimeTypes: String,
  ) : super(
    baseLanguage = baseLanguage,
    name = name,
    mimeTypes = mimeTypes,
  )

  companion object {
    @JvmField
    val INSTANCE: HTMLLanguage = HTMLLanguage()
  }
}
