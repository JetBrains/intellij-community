// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.date

import com.intellij.openapi.util.TextRange

fun findDates(vararg texts: String) = findDates(texts.asList())

fun findDates(texts: Collection<String>): Collection<Collection<TextRange>> {
  val annotationArrays = requestNER(texts) ?: return emptyList()

  return annotationArrays.map {
    it.filter { annotation -> annotation.label.toLowerCase() == "date" }
      .map { annotation -> TextRange.create(annotation.start, annotation.end) }
  }
}
