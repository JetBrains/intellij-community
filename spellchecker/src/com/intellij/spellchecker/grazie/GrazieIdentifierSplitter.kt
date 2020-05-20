// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie

import com.intellij.grazie.speller.utils.spitter.Splitter
import com.intellij.openapi.util.TextRange
import com.intellij.spellchecker.inspections.IdentifierSplitter

internal object GrazieIdentifierSplitter : Splitter {
  private val splitter = IdentifierSplitter.getInstance()

  override fun split(text: String): Sequence<String> {
    if (text.all { it.isLowerCase() && it.isLetter() }) return sequenceOf(text)

    //It is safe to use here just list, since this splitter should never encounter too long words
    val splits = ArrayList<String>()
    splitter.split(text, TextRange.allOf(text)) {
      splits.add(it.substring(text))
    }

    return splits.asSequence()
  }
}