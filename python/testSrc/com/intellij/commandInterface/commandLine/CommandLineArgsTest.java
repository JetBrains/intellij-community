// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.command.Argument;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;


/**
 * Checks {@link ValidationResult#getNextArg()} functionality
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineArgsTest extends PyTestCase {
  /**
   * Ensures argument is returned if exists
   */
  public void testArgsRequired() {
    CommandTestTools.initFileType(getTestRootDisposable());
    validateNextArgument("command", "positional_argument");
  }

  /**
   * Ensures option argument is returned if exists
   */
  public void testOptionArgsRequired() {
    CommandTestTools.initFileType(getTestRootDisposable());
    validateNextArgument("command positional_argument --available-option", "option argument");
  }

  /**
   * Ensures argument is not returned if not required
   */
  public void testNoMoreArgs() {
    CommandTestTools.initFileType(getTestRootDisposable());
    final CommandLineFile file = CommandTestTools.createFileByText(myFixture, "command foo bar spam eggs");
    final ValidationResult validationResult = file.getValidationResult();
    assert validationResult != null : "validation failed";
    Assert.assertNull("Argument returned while should not", validationResult.getNextArg());
  }


  /**
   * Runs commands and checks argument is required after it
   * @param commandText command text to run
   * @param expectedArgumentText expected next argument help text
   */
  private void validateNextArgument(@NotNull final String commandText, @NotNull final String expectedArgumentText) {
    final CommandLineFile file = CommandTestTools.createFileByText(myFixture, commandText);
    final ValidationResult validationResult = file.getValidationResult();
    assert validationResult != null : "validation failed";
    final Pair<Boolean, Argument> arg = validationResult.getNextArg();
    Assert.assertNotNull("No argument returned, but should", arg);
    Assert.assertTrue("Required argument is not marked as required", arg.first);
    Assert.assertEquals("Wrong argument text", expectedArgumentText, arg.second.getHelp().getHelpString());
  }
}
