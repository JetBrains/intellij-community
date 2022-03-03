// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*

interface TextCompletionComboBoxConverter<T> {

  fun createItem(text: String): T

  fun createString(element: T): String
}

interface TextCompletionComboBoxRenderer<T> {

  fun customizeCellRenderer(cell: SimpleColoredComponent, item: T, matchedText: @NlsSafe String)

  companion object {
    fun SimpleColoredComponent.append(
      text: @NlsSafe String,
      textAttributes: SimpleTextAttributes,
      matchedText: @NlsSafe String,
      matchedTextAttributes: SimpleTextAttributes
    ) {
      val fragments = text.split(matchedText)
      for ((i, fragment) in fragments.withIndex()) {
        if (fragment.isNotEmpty()) {
          append(fragment, textAttributes)
        }
        if (i < fragments.lastIndex) {
          append(matchedText, matchedTextAttributes)
        }
      }
    }
  }
}