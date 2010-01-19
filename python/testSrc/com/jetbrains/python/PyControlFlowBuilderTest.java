package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.controlflow.ControlFlow;
import com.jetbrains.python.psi.controlflow.Instruction;

import java.io.File;

/**
 * @author oleg
 */
public class PyControlFlowBuilderTest extends LightMarkedTestCase {

  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/psi/controlflow/";
  }

  private void doTest() throws Exception {
    final String testName = getTestName(false).toLowerCase();
    configureByFile(testName + ".py");
    final StringBuffer buffer = new StringBuffer();
    final ControlFlow flow = ((PyFile)myFile).getControlFlow();
    final Instruction[] instructions = flow.getInstructions();
    for (Instruction instruction : instructions) {
      buffer.append(instruction).append("\n");
    }
    final String fullPath = getTestDataPath() + testName + ".txt";
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    final String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    assertEquals(fileText.trim(), buffer.toString().trim());
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
}
