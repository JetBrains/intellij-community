// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import _LastInSuiteTest
import org.jetbrains.plugins.terminal.block.*
import org.jetbrains.plugins.terminal.block.actions.actions.TerminalDeletePreviousWordTest
import org.jetbrains.plugins.terminal.block.completion.ShellCdCommandTest
import org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecSuggestionsTest
import org.jetbrains.plugins.terminal.block.completion.ShellCommandTreeBuilderTest
import org.jetbrains.plugins.terminal.block.completion.ShellMakefileCompletionTest
import org.jetbrains.plugins.terminal.block.completion.ml.ShMLModelMetadataTest
import org.jetbrains.plugins.terminal.classic.BasicShellTerminalIntegrationTest
import org.junit.runners.Suite

/**
 * Helps to debug project leaks locally, see [_LastInSuiteTest].
 * Not used on TeamCity.
 * New tests can be added here on demand.
 *
 * The suite is disabled to avoid test duplication when running locally
 * all terminal tests in `org.jetbrains.plugins.terminal` package.
 * Uncomment `@org.junit.runner.RunWith(Suite::class)` to enable the suite.
 */
//@org.junit.runner.RunWith(Suite::class)
@Suppress("unused")
@Suite.SuiteClasses(
  BlockTerminalTest::class,
  TerminalOutputModelTest::class,
  ShellCdCommandTest::class,
  TerminalShellCommandTest::class,
  ShellCommandSpecSuggestionsTest::class,
  ShellMakefileCompletionTest::class,
  ShMLModelMetadataTest::class,
  RightPromptAndCommandLayoutTest::class,
  PowerShellCompletionTest::class,
  ShellCommandSpecManagerTest::class,
  BlockTerminalCommandExecutionTest::class,
  TerminalDeletePreviousWordTest::class,
  TerminalTextHighlighterTest::class,
  ShellBaseGeneratorsTest::class,
  ShellCommandTreeBuilderTest::class,
  BasicShellTerminalIntegrationTest::class,
  TerminalStateSerializationTest::class,
  //CommandEndMarkerListeningStringCollectorTest::class, // JUnit 5

  _LastInSuiteTest::class,
)
class ProjectLeakHunterTerminalSuite
