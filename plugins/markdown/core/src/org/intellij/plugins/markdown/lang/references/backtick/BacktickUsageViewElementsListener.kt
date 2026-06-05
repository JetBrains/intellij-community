// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references.backtick

import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageView
import com.intellij.usages.impl.UsageViewElementsListener
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan

internal class BacktickUsageViewElementsListener: UsageViewElementsListener {

  override fun beforeUsageAdded(view: UsageView, usage: Usage) {
    if (usage !is UsageInfo2UsageAdapter) return
    val element = usage.usageInfo.element ?: return
    if (element !is MarkdownCodeSpan) return
    val references = element.references
    if (references.any { it is BacktickReference }) {
      usage.usageInfo.isDynamicUsage = true
    }
  }
}
