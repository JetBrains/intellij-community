package org.intellij.plugins.markdown.ui.preview

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface MarkdownHtmlPanelEx: MarkdownHtmlPanel {
  fun scrollBy(horizontalUnits: Int, verticalUnits: Int)
}
