package com.jetbrains.python;

import com.intellij.codeInsight.editorActions.SelectWordHandler;
import com.intellij.ide.DataManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public class PySelectWordTest extends PyLightFixtureTestCase {
  public void testWord() throws Exception {
    doTest();
  }

  public void testSlice() throws Exception {   // PY-288
    doTest();
  }

  private void doTest() throws Exception {
    myFixture.copyDirectoryToProject("", "");
    @NonNls final String path = getTestName(true);
    myFixture.configureByFile(path + "/before.py");
    int i = 1;
    while (true) {
      @NonNls String resultPath = path + "/after" + i + ".py";
      if (new File(getTestDataPath() + resultPath).exists()) {
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

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/selectWord/";
  }
}
