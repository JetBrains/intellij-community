// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyTestCase;
import org.junit.Assert;


/**
 * Tests command line inspection
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineInspectionTest extends PyTestCase {


  @Override
  public void setUp() throws Exception {
    super.setUp();
    CommandTestTools.initFileType(getTestRootDisposable());
  }

  /**
   * Everything should be ok
   */
  public void testGoodCommandLine() {
    doTest();
  }

  /**
   * No command provided in file
   */
  public void testBadCommandLineNoCommand() {
    doTest();
  }

  /**
   * Command provided, but args and opts have errors
   */
  public void testBadCommandLineWithCommand() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return CommandTestTools.TEST_PATH;
  }

  /**
   * Enables inspection on testName.cmdline and checks it.
   */
  private void doTest() {
    final PsiFile file = myFixture.configureByFile(getTestName(true) + '.' + CommandLineFileType.EXTENSION);
    Assert.assertSame("Bad file type!", CommandLineFile.class, file.getClass());
    final CommandLineFile commandLineFile = (CommandLineFile)file;
    commandLineFile.setCommands(CommandTestTools.createCommands());
    myFixture.enableInspections(CommandLineInspection.class);
    myFixture.checkHighlighting();
  }
}
