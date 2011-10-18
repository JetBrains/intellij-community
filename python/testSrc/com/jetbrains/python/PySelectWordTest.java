package com.jetbrains.python;

import com.intellij.codeInsight.editorActions.SelectWordHandler;
import com.intellij.ide.DataManager;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public class PySelectWordTest extends PyTestCase {
  public void testWord() {
    doTest();
  }

  public void testSlice() {   // PY-288
    doTest();
  }

  public void testLiteral() {   // PY-1489
    doTest();
  }

  public void testList() {   // PY-1686
    doTest();
  }

  public void testComma() {   // PY-1378  
    doTest();
  }

  private void doTest() {
    @NonNls final String path = "selectWord/" + getTestName(true);
    myFixture.copyDirectoryToProject(path, path);
    myFixture.configureByFile(path + "/before.py");
    int i = 1;
    while (true) {
      @NonNls String resultPath = path + "/after" + i + ".py";
      if (new File(getTestDataPath() + "/" + resultPath).exists()) {
        performAction();
        //System.out.println("comparing with "+resultPath);
        myFixture.checkResultByFile(resultPath, false);
        i++;
      }
      else {
        break;
      }
    }
    assertTrue(i > 1);
  }

  private void performAction() {
    SelectWordHandler action = new SelectWordHandler(null);
    action.execute(myFixture.getEditor(), DataManager.getInstance().getDataContext());
  }
}
