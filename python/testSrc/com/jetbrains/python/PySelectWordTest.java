package com.jetbrains.python;

import com.intellij.codeInsight.editorActions.SelectWordHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public class PySelectWordTest extends LightCodeInsightTestCase {
  public void testWord() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    @NonNls final String path = getTestName(true);
    configureByFile(path + "/before.py");
    int i = 1;
    while (true) {
      @NonNls String resultPath = path + "/after" + i + ".py";
      if (new File(getTestDataPath() + resultPath).exists()) {
        performAction();
        //System.out.println("comparing with "+resultPath);
        checkResultByFile("Step " + i, resultPath, false);
        i++;
      }
      else {
        break;
      }
    }
    assertTrue(i>1);
  }

  private void performAction() {
    SelectWordHandler action = new SelectWordHandler(null);
    action.execute(getEditor(), DataManager.getInstance().getDataContext());
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/selectWord/";
  }
}
