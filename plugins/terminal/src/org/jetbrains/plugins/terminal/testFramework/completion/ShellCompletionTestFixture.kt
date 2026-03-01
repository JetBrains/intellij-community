// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion

import com.intellij.openapi.project.Project
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.testFramework.completion.impl.ShellCompletionTestFixtureBuilderImpl

@ApiStatus.Experimental
@ApiStatus.NonExtendable
@TestOnly
interface ShellCompletionTestFixture {
  /**
   * Returns the completion suggestions for provided [commandText].
   * It is intended that caret is positioned at the end of the [commandText], so the suggestions are returned for the last word.
   * Note that the results are not filtered by the prefix of the last word in the [commandText].
   * The command text should consist of the command name and at least a space after it (empty prefix).
   * For example,
   * 1. `git ` (with a trailing space) -> should return all subcommands and options of `git`.
   * 2. `git --he` -> the same as the previous. No matching or filtering of suggestions is performed.
   * 3. `git branch -b ` (with a trailing space) -> should return git branch suggestions.
   */
  suspend fun getCompletions(commandText: String): List<ShellCompletionSuggestion>

  companion object {
    @TestOnly
    fun builder(project: Project): ShellCompletionTestFixtureBuilder {
      return ShellCompletionTestFixtureBuilderImpl(project)
    }
  }
}