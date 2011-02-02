package com.jetbrains.python;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

import java.io.File;
import java.io.IOException;

/**
 * @author oleg
 */
public class PyControlFlowBuilderTest extends LightMarkedTestCase {

  @Override
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

  public void testAssert() throws Exception {
    doTest();
  }

  public void testAssertFalse() throws Exception {
    doTest();
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

  public void testImport() throws Exception {
    doTest();
  }

  public void testListComp() throws Exception {
    doTest();
  }

  public void testAssignment() throws Exception {
    doTest();
  }

  public void testAssignment2() throws Exception {
    doTest();
  }

  public void testAugAssignment() throws Exception {
    doTest();
  }

  public void testSliceAssignment() throws Exception {
    doTest();
  }

  public void testIfElseReturn() throws Exception {
    doTest();
  }

  public void testRaise() throws Exception {
    doTest();
  }

  public void testReturnFor() throws Exception {
    doTest();
  }

  public void testForIf() throws Exception {
    doTest();
  }

  public void testForReturn() throws Exception {
    doTest();
  }

  public void testForTryContinue() throws Exception {
    doTest();
  }

  public void testTryRaiseFinally() throws Exception {
    doTest();
  }

  public void testTryExceptElseFinally() throws Exception {
    doTest();
  }

  public void testTryFinally() throws Exception {
    doTest();
  }

  public void testDoubleTry() throws Exception {
    doTest();
  }

  public void testIsinstance() throws Exception {
    doTest();
  }

  public void testLambda() throws Exception {
    doTest();
  }

  public void testManyIfs() throws Exception {
    doTest();
  }

  public void testQualifiedSelfReference() throws Exception {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final PyClass pyClass = ((PyFile) myFile).getTopLevelClasses().get(0);
    final ControlFlow flow = pyClass.getMethods() [0].getControlFlow();
    check(fullPath, flow);
  }

  public void testSelf() throws Exception {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final PyClass pyClass = ((PyFile) myFile).getTopLevelClasses().get(0);
    final ControlFlow flow = pyClass.getMethods() [0].getControlFlow();
    check(fullPath, flow);
  }

  public void testTryBreak() throws Exception {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final ControlFlow flow = ((PyFunction)((PyFile)myFile).getStatements().get(0)).getControlFlow();
    final String fullPath = getTestDataPath() + testName + ".txt";
    check(fullPath, flow);
  }

  public void testFunction() throws Exception {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final String fullPath = getTestDataPath() + testName + ".txt";
    final ControlFlow flow = ((PyFunction)((PyFile)myFile).getStatements().get(0)).getControlFlow();
    check(fullPath, flow);
  }

  private static void check(final String fullPath, final ControlFlow flow) throws IOException {
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
