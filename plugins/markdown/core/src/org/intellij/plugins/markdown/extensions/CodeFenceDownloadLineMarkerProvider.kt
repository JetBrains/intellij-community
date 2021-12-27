// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.application
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.intellij.plugins.markdown.settings.MarkdownSettingsUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class CodeFenceDownloadLineMarkerProvider : LineMarkerProviderDescriptor() {
  init {
    application.messageBus.connect().subscribe(MarkdownExtensionsSettings.ChangeListener.TOPIC, object: MarkdownExtensionsSettings.ChangeListener {
      override fun extensionsSettingsChanged(fromSettingsDialog: Boolean) {
        LineMarkerProviders.getInstance().clearCache()
      }
    })
  }

  abstract fun shouldProcessElement(element: PsiElement): Boolean

  abstract fun getExtension(): MarkdownExtensionWithDownloadableFiles?

  abstract val tooltipText: String

  private fun clickAction() {
    invokeLater {
      val extension = getExtension() ?: return@invokeLater
      MarkdownSettingsUtil.downloadExtension(extension, enableAfterDownload = true)
      LineMarkerProviders.getInstance().clearCache()
    }
  }

  override fun getLineMarkerInfo(element: PsiElement): RelatedItemLineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    if (getExtension()?.isAvailable == true) {
      return
    }
    for (element in elements) {
      if (element !is MarkdownCodeFence || !shouldProcessElement(element)) {
        continue
      }
      val fenceLanguage = element.findPsiChildByType(MarkdownTokenTypes.FENCE_LANG) ?: continue
      val marker = LineMarkerInfo(
        fenceLanguage,
        TextRange(fenceLanguage.startOffset, fenceLanguage.endOffset),
        AllIcons.Actions.Download,
        { tooltipText },
        { _, _ -> clickAction() },
        GutterIconRenderer.Alignment.LEFT,
        { tooltipText }
      )
      result.add(marker)
    }
  }
}
