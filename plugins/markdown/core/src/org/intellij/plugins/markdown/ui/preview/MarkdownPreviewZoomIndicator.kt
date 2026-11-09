// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Shows the zoom indicator of a Markdown preview below [JComponent].
 * The frontend registers the project service, so the service is absent on the backend.
 */
@ApiStatus.Internal
interface MarkdownPreviewZoomIndicator {
  fun show(preview: MarkdownPreviewBrowserActions, component: JComponent)
}
