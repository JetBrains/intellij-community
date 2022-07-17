// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class MarkdownImageData(
  val path: String,
  val width: String,
  val height: String,
  val title: String,
  val description: String,
  val shouldConvertToHtml: Boolean
)
