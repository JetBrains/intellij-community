// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.XmlPatterns


internal class EmmetCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement().inside(XmlPatterns.xmlFile()), EmmetAbbreviationCompletionProvider())
  }
}
