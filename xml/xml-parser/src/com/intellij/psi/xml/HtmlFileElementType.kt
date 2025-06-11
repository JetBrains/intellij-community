// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.tree.IFileElementType

open class HtmlFileElementType(
  debugName: String,
  language: Language,
) : IFileElementType(debugName, language) {

  protected constructor() :
    this("html", HTMLLanguage.INSTANCE)

  companion object {
    @JvmField
    val INSTANCE: IFileElementType = HtmlFileElementType()
  }
}
