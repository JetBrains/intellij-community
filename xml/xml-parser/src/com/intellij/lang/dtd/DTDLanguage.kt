// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.dtd

import com.intellij.lang.xml.XMLLanguage

object DTDLanguage :
  XMLLanguage(
    baseLanguage = INSTANCE,
    name = "DTD",
    mimeTypes = arrayOf(
      "application/xml-dtd",
      "text/dtd",
      "text/x-dtd",
    ),
  )
