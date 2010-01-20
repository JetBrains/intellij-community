package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.codeInsight.controlflow.ControlFlow;
import com.jetbrains.python.codeInsight.controlflow.Instruction;

import java.io.File;
import java.io.IOException;

/**
 * @author oleg
 */
public class PyControlFlowBuilderTest extends LightMarkedTestCase {

  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/codeInsight/controlflow/";
  }

  private void doTest() throws Exception {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
   final ControlFlow flow = ((PyFile)myFile).getControlFlow();
    final String fullPath = getTestDataPath() + testName + ".txt";
    check(fullPath, flow);
   }
  
  public void testFile() throws Exception {
    doTest();
  }
  
  public void testIf() throws Exception {
    doTest();
  }

  public void testFor() throws Exception {
    doTest();
  }

  public void testWhile() throws Exception {
    doTest();
  }

  public void testBreak() throws Exception {
    doTest();
  }

  public void testContinue() throws Exception {
    doTest();
  }

  public void testReturn() throws Exception {
    doTest();
  }

  public void testTry() throws Exception {
    doTest();
  }

  public void testFunction() throws Exception {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final ControlFlow flow = ((PyFunction)((PyFile)myFile).getStatements().get(0)).getControlFlow();
    check(fullPath, flow);
  }

  private void check(final String fullPath, final ControlFlow flow) throws IOException {
    final StringBuffer buffer = new StringBuffer();
    final Instruction[] instructions = flow.getInstructions();
    for (Instruction instruction : instructions) {
      buffer.append(instruction).append("\n");
    }
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    final String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    assertEquals(fileText.trim(), buffer.toString().trim());
  }
}
