// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.sphinx

import com.jetbrains.rest.RestOutputHandler
import org.jsoup.Jsoup

/** Filters out errors and displays unknown reStructuredText directives verbatim. */
class SphinxDirectivesHandler : RestOutputHandler {
  override fun apply(output: String): String {
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
}
