package com.intellij.markdown.java

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.extensions.MarkdownCodeSpanConfigurationContextSearcher

internal class JvmCodeSpanConfigurationContextSearcher : MarkdownCodeSpanConfigurationContextSearcher {

  override fun findConfigurations(runnableName: String, host: PsiElement): List<ConfigurationContext> {
    val project = host.project
    val scope = GlobalSearchScope.projectScope(project)
    return PsiShortNamesCache.getInstance(project)
      .getClassesByName(runnableName, scope)
      .mapNotNull { psiClass ->
        val context = ConfigurationContext(psiClass)
        val fromContext = context.configurationsFromContext
          ?.firstOrNull { PsiTreeUtil.isAncestor(psiClass.navigationElement, it.sourceElement.navigationElement, false) }
          ?: return@mapNotNull null
        context.setConfiguration(fromContext.configurationSettings)
        context
      }
  }
}
