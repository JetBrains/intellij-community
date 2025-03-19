// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.providers.slf4j

import com.intellij.codeInsight.TailTypes
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.TailTypeDecorator
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.patterns.PlatformPatterns
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.ProcessingContext
import com.intellij.util.applyIf

internal class SimpleLoggerPropertiesCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement(PropertyKeyImpl::class.java)
        .inFile(PlatformPatterns.psiFile().withName(SIMPLE_LOGGER_PROPERTIES_CONFIG)),

      object : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
          val delimiterChar = PropertiesCodeStyleSettings.getInstance(parameters.editor.project).delimiter
          val defaultDelimiterType = TailTypes.charType(delimiterChar)

          result.addAllElements(SIMPLE_LOGGER_PROPERTIES.map {
            val builder = LookupElementBuilder.create(it.key)
              .applyIf(it.value.isNotBlank()) {
                withTailText("=" + it.value, true)
              }
              .withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Property))

            TailTypeDecorator.withTail(builder, defaultDelimiterType)
          })
        }
      }
    )
  }
}