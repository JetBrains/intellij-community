// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.problemsView

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

internal class ProblemsViewCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> = mapOf(
    OpenProblemViewPanelCommand.PREFIX to CreateCommand(::OpenProblemViewPanelCommand),
    AssertProblemsViewCountCommand.PREFIX to CreateCommand(::AssertProblemsViewCountCommand),
  )
}
