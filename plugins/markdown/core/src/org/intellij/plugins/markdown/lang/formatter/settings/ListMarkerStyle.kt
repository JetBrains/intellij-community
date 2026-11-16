// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.formatter.settings

import org.jetbrains.annotations.PropertyKey

/**
 * The marker of an unordered list item that the IDE writes.
 * [bullet] is `null` when the IDE keeps the existing marker.
 */
enum class UnorderedListMarker(@PropertyKey(resourceBundle = "messages.MarkdownBundle") val messageKey: String, val bullet: Char?) {
  KEEP("markdown.style.settings.list.marker.keep", null),
  ASTERISK("markdown.style.settings.list.marker.force.asterisk", '*'),
  DASH("markdown.style.settings.list.marker.force.dash", '-'),
  PLUS("markdown.style.settings.list.marker.force.plus", '+')
}

/**
 * The delimiter after the number of an ordered list item that the IDE writes.
 * [delimiter] is `null` when the IDE keeps the existing delimiter.
 */
enum class OrderedListMarker(@PropertyKey(resourceBundle = "messages.MarkdownBundle") val messageKey: String, val delimiter: Char?) {
  KEEP("markdown.style.settings.list.marker.keep", null),
  PARENTHESIS("markdown.style.settings.list.marker.force.parenthesis", ')'),
  DOT("markdown.style.settings.list.marker.force.dot", '.')
}
