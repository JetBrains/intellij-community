// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests

import _LastInSuiteTest
import com.intellij.terminal.tests.block.*
import com.intellij.terminal.tests.block.actions.actions.TerminalDeletePreviousWordTest
import com.intellij.terminal.tests.block.completion.ShellCdCommandTest
import com.intellij.terminal.tests.block.completion.ShellCommandSpecSuggestionsTest
import com.intellij.terminal.tests.block.completion.ShellCommandTreeBuilderTest
import com.intellij.terminal.tests.block.completion.ShellMakefileCompletionTest
import com.intellij.terminal.tests.block.completion.ml.ShMLModelMetadataTest
import com.intellij.terminal.tests.classic.ClassicTerminalBasicTest
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
  ClassicTerminalBasicTest::class,
  TerminalStateSerializationTest::class,
  //CommandEndMarkerListeningStringCollectorTest::class, // JUnit 5

  _LastInSuiteTest::class,
)
class ProjectLeakHunterTerminalSuite
