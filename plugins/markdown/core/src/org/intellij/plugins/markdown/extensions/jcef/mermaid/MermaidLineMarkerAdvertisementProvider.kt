package org.intellij.plugins.markdown.extensions.jcef.mermaid

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.jetbrains.annotations.Nls

internal class MermaidLineMarkerAdvertisementProvider: LineMarkerProvider {
  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  private val tooltipText: @Nls String
    get() = MarkdownBundle.message("markdown.line.marker.mermaid.advertisement.tooltip.text")

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    if (isMermaidPluginInstalled()) {
      return
    }
    for (element in elements) {
      if (element !is MarkdownCodeFence || element.fenceLanguage != "mermaid") {
        continue
      }
      val fenceLanguage = element.findPsiChildByType(MarkdownTokenTypes.FENCE_LANG) ?: continue
      val project = element.project
      val marker = LineMarkerInfo(
        fenceLanguage,
        fenceLanguage.textRange,
        AllIcons.Actions.Download,
        { tooltipText },
        { _, _ -> installMermaidPlugin(project) },
        GutterIconRenderer.Alignment.LEFT,
        { tooltipText }
      )
      result.add(marker)
    }
  }
}
