// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
@ApiStatus.Experimental
interface CodeFenceRenderingService {
  /**
   * Creates the view of [source] for the given [language]. Returns null when the service does not render the language.
   * The view lives while [scope] is active.
   */
  suspend fun render(language: String, source: String, scope: CoroutineScope): CodeFenceView?

  companion object {
    val EP_NAME: ExtensionPointName<CodeFenceRenderingService> = ExtensionPointName("org.intellij.markdown.codeFenceRenderingService")

    suspend fun render(language: String, source: String, scope: CoroutineScope): CodeFenceView? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.render(language, source, scope) }
  }
}

/**
 * The live view of a rendered code fence. The editor shows [component] below the fence at the editor width.
 * The editor sets the background of [component] and caps its height.
 */
@ApiStatus.Internal
interface CodeFenceView {
  /**
   * The Swing component of the view. Use it on the EDT only.
   * Its preferred size is the natural size of the view. In a narrower editor, the view keeps the aspect ratio of this size.
   */
  val component: JComponent
}
