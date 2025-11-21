// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TaskUiUtil")

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader.getTransparentIcon
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.tasks.LocalTask
import com.intellij.tasks.TaskManager
import com.intellij.tasks.core.TaskSymbol
import com.intellij.ui.LayeredIcon
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.text.MatcherHolder
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil

internal fun getTaskCellRenderer(project: Project) = listCellRenderer<Any> {
  selectionColor = UIUtil.getListSelectionBackground(true)
  val matcher = MatcherHolder.getAssociatedMatcher(list) as? MinusculeMatcher
  val value = value

  when (value) {
    is TaskSymbol -> {
      val task = value.task
      val taskManager = TaskManager.getManager(project)
      val isLocalTask = taskManager.findTask(task.id) != null
      val isClosed = task.isClosed || (task is LocalTask && taskManager.isLocallyClosed(task))

      background = when {
        isLocalTask -> UIUtil.getListBackground()
        else -> UIUtil.getDecoratedRowColor()
      }

      icon(if (isClosed) getTransparentIcon(task.icon) else task.icon)
      text(task.presentableName) {
        if (isClosed) {
          foreground = greyForeground
        }
        speedSearch {
          ranges = matcher?.match(task.presentableName)
        }
      }
    }
    "..." -> {
      icon(EmptyIcon.ICON_16)
      text("...")
    }
    GotoTaskAction.CREATE_NEW_TASK_ACTION -> {
      icon(LayeredIcon.create(AllIcons.FileTypes.Unknown, AllIcons.Actions.New))
      text(GotoTaskAction.CREATE_NEW_TASK_ACTION.actionText)
    }
  }
}