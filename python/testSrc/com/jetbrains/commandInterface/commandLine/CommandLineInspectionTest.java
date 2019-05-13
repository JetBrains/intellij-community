/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.commandInterface.commandLine;

import com.intellij.psi.PsiFile;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
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
    CommandTestTools.initFileType();
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
