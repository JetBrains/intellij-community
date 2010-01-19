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


  public void testFile() throws Exception {
    check("file.py", "file.txt");
  }

  private void check(final String inputFile, final String outputFile) throws Exception {
    configureByFile(inputFile);
    final StringBuffer buffer = new StringBuffer();
    final ControlFlow flow = ((PyFile)myFile).getControlFlow();    
    final Instruction[] instructions = flow.getInstructions();
    for (Instruction instruction : instructions) {
      buffer.append(instruction).append("\n");
    }
    final String fullPath = getTestDataPath() + outputFile;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    final String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    assertEquals(fileText.trim(), buffer.toString().trim());
  }

}
