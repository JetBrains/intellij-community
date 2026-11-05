// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.paint.use
import com.intellij.util.PathUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File

internal object MarkdownTestingUtil {
  @JvmField
  val TEST_DATA_PATH = findTestDataPath()

  private fun findTestDataPath(): String {
    val homePath = PathManager.getHomePath()
    if (File("$homePath/community/plugins").isDirectory) {
      return FileUtil.toSystemIndependentName("$homePath/community/plugins/markdown/test/data")
    }
    val parentPath = PathUtil.getParentPath(homePath)
    val targetPath = when {
      File("$parentPath/intellij-plugins").isDirectory -> "$parentPath/intellij-plugins/markdown/test/data"
      File("$parentPath/community/plugins").isDirectory -> "$parentPath/community/plugins/markdown/test/data"
      else -> null
    }
    return targetPath?.let(FileUtil::toSystemIndependentName) ?: ""
  }

  fun paintPresentation(presentation: InlayPresentation) {
    newGraphics().use { presentation.paint(it, TextAttributes()) }
  }

  fun paintInlay(inlay: Inlay<*>) {
    newGraphics().use { inlay.renderer.paint(inlay, it, Rectangle2D.Double(), TextAttributes()) }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  fun paintEditor(editor: Editor, clip: Rectangle? = null): BufferedImage {
    val component = editor.contentComponent
    val preferredSize = component.preferredSize
    component.setSize(maxOf(preferredSize.width, editor.scrollingModel.visibleArea.width), preferredSize.height)
    val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_RGB)
    image.createGraphics().use { graphics ->
      if (clip != null) graphics.clipRect(clip.x, clip.y, clip.width, clip.height)
      component.paint(graphics)
    }
    return image
  }

  private fun newGraphics() = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
}

@OptIn(DelicateCoroutinesApi::class)
internal fun MarkdownLivePreviewReconciler.publishSpecsAndWait(specSet: MarkdownLivePreviewSpecSet?) {
  PlatformTestUtil.waitForFuture(GlobalScope.future { publishSpecs(specSet) })
}
