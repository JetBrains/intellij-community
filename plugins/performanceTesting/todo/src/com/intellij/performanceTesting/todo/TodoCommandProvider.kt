// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.todo

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

internal class TodoCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> = mapOf(
    OpenTodoToolWindowCommand.PREFIX to CreateCommand(::OpenTodoToolWindowCommand),
    EditTodoCommand.PREFIX to CreateCommand(::EditTodoCommand),
    SwitchTodoFileCommand.PREFIX to CreateCommand(::SwitchTodoFileCommand),
    TodoFilterCommand.PREFIX to CreateCommand(::TodoFilterCommand),
    TodoPreviewCommand.PREFIX to CreateCommand(::TodoPreviewCommand),
    SelectTodoScopeCommand.PREFIX to CreateCommand(::SelectTodoScopeCommand),
  )
}
