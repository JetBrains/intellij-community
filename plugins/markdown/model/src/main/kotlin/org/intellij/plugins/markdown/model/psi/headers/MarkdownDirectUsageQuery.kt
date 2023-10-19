package org.intellij.plugins.markdown.model.psi.headers

import com.intellij.find.usages.api.PsiUsage
import com.intellij.openapi.application.runReadAction
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor

internal class MarkdownDirectUsageQuery(private val usage: PsiUsage): AbstractQuery<PsiUsage>() {
  override fun processResults(consumer: Processor<in PsiUsage>): Boolean {
    return runReadAction {
      consumer.process(usage)
    }
  }
}
