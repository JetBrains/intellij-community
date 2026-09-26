package com.intellij.markdown.backend.highlighting.markers

import com.intellij.codeInsight.daemon.GutterName
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.jcef.mermaid.installMermaidPlugin
import org.intellij.plugins.markdown.extensions.jcef.mermaid.isMermaidPluginInstalled
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class MermaidLineMarkerAdvertisementProvider: LineMarkerProviderDescriptor() {
  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  private val tooltipText: @Nls String
    get() = MarkdownBundle.message("markdown.line.marker.mermaid.advertisement.tooltip.text")

  override fun getName(): @GutterName String = tooltipText

  override fun getIcon(): Icon = AllIcons.Actions.Download

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
