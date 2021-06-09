// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import java.nio.file.Path
import javax.swing.Icon

internal abstract class ConfigureImageLineMarkerProviderBase<T : PsiElement> : LineMarkerProviderDescriptor() {
  /**
   * [element] - outer element returned by [obtainOuterElement]
   */
  abstract fun createDialog(element: T): ConfigureImageDialog?

  /**
   * [element] - leaf element returned by [obtainLeafElement]
   */
  abstract fun obtainPathText(element: PsiElement): String?

  abstract fun applyChanges(element: PsiElement, imageData: MarkdownImageData)

  abstract fun obtainLeafElement(element: PsiElement): PsiElement?

  /**
   * Gets outer element for element returned by [obtainLeafElement].
   * If [element] is itself an outer element, it should be returned as is.
   */
  abstract fun obtainOuterElement(element: PsiElement): T?

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    val leaf = obtainLeafElement(element) ?: return null
    return ConfigureImageLineMarkerInfo(element, leaf.textRange)
  }

  fun performAction(element: PsiElement) {
    val outerElement = obtainOuterElement(element) ?: return
    val dialog = createDialog(outerElement) ?: return
    dialog.show {
      applyChanges(element, it)
    }
  }

  override fun getName(): String {
    return MarkdownBundle.message("markdown.configure.markdown.image.line.marker.provider.name")
  }

  private fun getMarkerElementPresentation(element: PsiElement): String {
    val fileName = obtainPathText(element)?.let { Path.of(it).fileName.toString() } ?: ""
    return when {
      fileName.isEmpty() -> MarkdownBundle.message("markdown.setup.image.line.marker.text")
      else -> MarkdownBundle.message("markdown.configure.image.line.marker.presentation", fileName)
    }
  }

  private inner class ConfigureImageLineMarkerInfo(
    element: PsiElement,
    textRange: TextRange
  ) : MergeableLineMarkerInfo<PsiElement>(
    element,
    textRange,
    AllIcons.General.LayoutPreviewOnly,
    ::getMarkerElementPresentation,
    { _, e -> performAction(e) },
    ALIGNMENT,
    { MarkdownBundle.message("markdown.configure.image.line.marker.configure.command.name") }
  ) {
    override fun getElementPresentation(element: PsiElement): String {
      return getMarkerElementPresentation(element)
    }

    override fun canMergeWith(info: MergeableLineMarkerInfo<*>): Boolean {
      return info is ConfigureImageLineMarkerProviderBase<*>.ConfigureImageLineMarkerInfo
    }

    override fun getCommonIcon(infos: MutableList<out MergeableLineMarkerInfo<*>>): Icon {
      return infos.first().icon
    }

    override fun getCommonIconAlignment(infos: MutableList<out MergeableLineMarkerInfo<*>>) = ALIGNMENT
  }

  companion object {
    private val ALIGNMENT = GutterIconRenderer.Alignment.CENTER
  }
}
