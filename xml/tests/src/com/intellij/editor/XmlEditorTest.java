package com.intellij.editor;

import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Rustam Vishnyakov
 */
public class XmlEditorTest extends LightCodeInsightTestCase {
  private String getTestFilePath(boolean isOriginal) {
    return "/xml/tests/testData/editor/" + getTestName(true) + (isOriginal ? ".xml" : "_after.xml") ;
  }

  public void testEnterPerformance() throws Exception {
    configureByFile(getTestFilePath(true));
    EditorTestUtil.performTypingAction(myEditor, '\n');
    PlatformTestUtil.assertTiming("PHP editor performance", 7500, 1, new Runnable() {
      public void run() {
        for (int i = 0; i < 3; i ++) {
          EditorTestUtil.performTypingAction(myEditor, '\n');
        }
      }
    });    
    checkResultByFile(getTestFilePath(false));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/');
  }

}
