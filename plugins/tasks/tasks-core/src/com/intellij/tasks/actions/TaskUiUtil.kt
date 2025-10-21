// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TaskUiUtil")

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader.getTransparentIcon
import com.intellij.tasks.LocalTask
import com.intellij.tasks.TaskManager
import com.intellij.tasks.doc.TaskPsiElement
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls

internal fun getTaskCellRenderer(project: Project) = listCellRenderer<Any> {
  selectionColor = UIUtil.getListSelectionBackground(true)

  when (value) {
    is TaskPsiElement -> {
      val task = (value as TaskPsiElement).task
      val taskManager = TaskManager.getManager(project)
      val isLocalTask = taskManager.findTask(task.id) != null
      val isClosed = task.isClosed || (task is LocalTask && taskManager.isLocallyClosed(task))

      background = when {
        isLocalTask -> UIUtil.getListBackground()
        else -> UIUtil.getDecoratedRowColor()
      }

      icon(if (isClosed) getTransparentIcon(task.icon) else task.icon)
      text(task.presentableName) {
        attributes = SimpleTextAttributes(
          SimpleTextAttributes.STYLE_PLAIN,
          if (isClosed) UIUtil.getLabelDisabledForeground()
          else UIUtil.getListForeground(this@listCellRenderer.selected, this@listCellRenderer.cellHasFocus)
        )
        speedSearch {}
      }
    }
    "..." -> {
      icon(EmptyIcon.ICON_16)
      text(value as @Nls String)
    }
    GotoTaskAction.CREATE_NEW_TASK_ACTION -> {
      icon(LayeredIcon.create(AllIcons.FileTypes.Unknown, AllIcons.Actions.New))
      text(GotoTaskAction.CREATE_NEW_TASK_ACTION.actionText) {
        speedSearch {}
      }
    }
  }
}