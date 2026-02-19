// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;


import com.intellij.commandInterface.commandLine.psi.CommandLineArgument;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.intellij.testFramework.ParsingTestCase;
import org.junit.Assert;


/**
 * Tests command line parser
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineParserTest extends ParsingTestCase {
  public CommandLineParserTest() {
    super("", CommandLineFileType.EXTENSION, true, new CommandLineParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return CommandTestTools.TEST_PATH;
  }

  public void testSpaces() {
    doTest(true);
    final CommandLineFile commandLineFile = (CommandLineFile)myFile;
    Assert.assertEquals("Bad argument value", "spam and eggs", commandLineFile.getArguments().iterator().next().getValueNoQuotes());
    final CommandLineArgument optionArgument = commandLineFile.getOptions().iterator().next().findArgument();
    Assert.assertNotNull("No option argument found", optionArgument);
    Assert.assertEquals("Bad option argument value", "ketchup", optionArgument.getValueNoQuotes());
  }

  /**
   * Should be ok
   */
  public void testCommandLine() {
    doTest(true);
  }

  /**
   * Should have a lot of errors
   */
  public void testJunk() {
    doTest(true);
  }

  /**
   * Should have error because option ends with "="
   */
  public void testOptionNoValueJunk() {
    doTest(true);
  }
}
