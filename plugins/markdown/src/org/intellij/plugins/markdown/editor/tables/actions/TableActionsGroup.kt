// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.registry.Registry

internal class TableActionsGroup: DefaultActionGroup() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = Registry.`is`("markdown.tables.editing.support.enable")
  }
}
