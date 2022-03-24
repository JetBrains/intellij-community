// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownIcons
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.net.URI
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import javax.swing.Icon

@ApiStatus.Internal
abstract class ConfigureImageLineMarkerProviderBase<T : PsiElement> : LineMarkerProviderDescriptor() {
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

  private fun processFileName(filePath: String): String? {
    try {
      // Path can be eiter a URL or a system path
      val uri = URI.create(FileUtil.toSystemIndependentName(filePath)).path
      return Paths.get(uri).fileName?.toString()
    } catch (exception: IllegalArgumentException) {
      return null
    } catch (exception: InvalidPathException) {
      return null
    }
  }

  private fun getMarkerElementPresentation(element: PsiElement): @Nls String {
    val fileName = obtainPathText(element)?.let(::processFileName) ?: ""
    return when {
      fileName.isEmpty() -> MarkdownBundle.message("markdown.configure.image.text")
      else -> MarkdownBundle.message("markdown.configure.image.line.marker.presentation", fileName)
    }
  }

  private inner class ConfigureImageLineMarkerInfo(
    element: PsiElement,
    textRange: TextRange
  ) : MergeableLineMarkerInfo<PsiElement>(
    element,
    textRange,
    MarkdownIcons.ImageGutter,
    ::getMarkerElementPresentation,
    { _, e -> performAction(e) },
    ALIGNMENT,
    { MarkdownBundle.message("markdown.configure.image.text") }
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
