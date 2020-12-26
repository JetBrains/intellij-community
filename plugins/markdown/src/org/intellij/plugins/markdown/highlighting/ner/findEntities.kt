// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.ner

import com.intellij.openapi.util.TextRange

internal fun findEntities(textsWithOffsets: Collection<TextWithOffset>): Entities {
  val texts = textsWithOffsets.map(TextWithOffset::first)
  val offsets = textsWithOffsets.map(TextWithOffset::second)

  val entityAnnotations = requestNER(texts) ?: emptyList()

  val labelToRangesMap = entityAnnotations.flatMapIndexed { i, annotations ->
      annotations.map {
        val range = TextRange.create(it.start, it.end).shiftRight(offsets[i])
        it.label.toLowerCase() to range
      }
    }
    .groupBy(Pair<String, TextRange>::first, Pair<String, TextRange>::second)
    .mapValues { it.value.toSet() } // this line is not needed in case it is guaranteed that annotations are unique

  return Entities(labelToRangesMap)
}

class Entities(private val labelToRangesMap: Map<String, Set<TextRange>>) {
  val dates: Set<TextRange>
    get() = labelToRangesMap["date"] ?: emptySet()

  val money: Set<TextRange>
    get() = labelToRangesMap["money"] ?: emptySet()
}
