// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;


import com.intellij.commandInterface.commandLine.psi.CommandLineArgument;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;


/**
 * Tests command line parser
 *
 * @author Ilya.Kazakevich
 */
@TestApplication
public final class CommandLineParserTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, CommandTestTools.TEST_PATH + "/", CommandLineFileType.EXTENSION, true, new CommandLineParserDefinition());

  @Test
  public void testSpaces() {
    parsingFixture.get().doTest(true);
    final CommandLineFile commandLineFile = (CommandLineFile)parsingFixture.get().getFile();
    ReadAction.runBlocking(() -> {
      Assertions.assertEquals("spam and eggs", commandLineFile.getArguments().iterator().next().getValueNoQuotes(), "Bad argument value");
      final CommandLineArgument optionArgument = commandLineFile.getOptions().iterator().next().findArgument();
      Assertions.assertNotNull(optionArgument, "No option argument found");
      Assertions.assertEquals("ketchup", optionArgument.getValueNoQuotes(), "Bad option argument value");
    });
  }

  /**
   * Should be ok
   */
  @Test
  public void testCommandLine() {
    parsingFixture.get().doTest(true);
  }

  /**
   * Should have a lot of errors
   */
  @Test
  public void testJunk() {
    parsingFixture.get().doTest(true);
  }

  /**
   * Should have error because option ends with "="
   */
  @Test
  public void testOptionNoValueJunk() {
    parsingFixture.get().doTest(true);
  }
}
