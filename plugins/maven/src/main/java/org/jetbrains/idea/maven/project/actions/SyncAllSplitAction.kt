// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.SplitButtonAction

internal class SyncAllSplitAction : SplitButtonAction(openActionGroup("Maven.SyncAllGroup")) {
  override fun useDynamicSplitButton(): Boolean = false
}

private fun openActionGroup(actionGroupId: String): ActionGroup {
  val children = mutableListOf<AnAction>()

  fun collectChildren(group: ActionGroup) {
    val childActions = (group as? DefaultActionGroup)?.childActionsOrStubs ?: group.getChildren(null)
    childActions.forEach { innerAction ->
      if (innerAction is ActionGroup) {
        collectChildren(innerAction)
      }
      else {
        children.add(innerAction)
      }
    }
  }

  val originalGroup = ActionManager.getInstance().getAction(actionGroupId) as ActionGroup
  collectChildren(originalGroup)
  return DefaultActionGroup(children)
}