// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.settings.MarkdownSettings

internal object TableTestUtils {
  fun runWithChangedSettings(project: Project, block: () -> Unit) {
    val settings = MarkdownSettings.getInstance(project)
    val oldValue = settings.isEnhancedEditingEnabled
    settings.isEnhancedEditingEnabled = true
    try {
      block.invoke()
    } finally {
      settings.isEnhancedEditingEnabled = oldValue
    }
  }
}
