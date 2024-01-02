// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.sphinx

import org.jsoup.Jsoup

/** Filters out errors and displays unknown reStructuredText directives verbatim. */
internal fun applySphinx(output: String): String {
  val html = Jsoup.parse(output)
  html.getElementsByClass("system-message").forEach { systemMessageElement ->
    systemMessageElement.getElementsByClass("literal-block").firstOrNull().let { literalBlockElement ->
      if (literalBlockElement != null) {
        systemMessageElement.replaceWith(literalBlockElement)
      }
      else {
        systemMessageElement.remove()
      }
    }
  }
  val problematicClassName = "problematic"
  html.getElementsByClass(problematicClassName).forEach { problematicElement ->
    problematicElement.removeClass(problematicClassName)
  }
  return html.toString()
}
