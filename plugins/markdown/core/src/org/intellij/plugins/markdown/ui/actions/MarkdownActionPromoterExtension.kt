// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MarkdownActionPromoterExtension {
  fun shouldPromoteMarkdownActions(context: DataContext): Boolean
}