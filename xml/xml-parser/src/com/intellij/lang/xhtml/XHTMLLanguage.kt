// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.xhtml

import com.intellij.lang.xml.XMLLanguage

object XHTMLLanguage :
  XMLLanguage(
    baseLanguage = INSTANCE,
    name = "XHTML",
    mimeTypes = arrayOf(
      "text/xhtml",
      "application/xhtml+xml",
    )
  )
