// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.mermaid

import com.intellij.lang.Language

class MermaidLanguage private constructor() : Language("Mermaid") {
  companion object {
    val INSTANCE = MermaidLanguage()
  }
}
