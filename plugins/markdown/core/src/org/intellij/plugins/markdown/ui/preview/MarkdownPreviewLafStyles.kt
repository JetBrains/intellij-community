// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MarkdownPreviewLafStyles {
  fun createStylesheet(): String = PreviewLAFThemeStyles.createStylesheet()

  fun defaultFontSize(): Int = PreviewLAFThemeStyles.defaultFontSize

  fun fontSizeCssVariable(): String = PreviewLAFThemeStyles.Variables.FontSize
}
