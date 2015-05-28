package com.intellij.editor;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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

  public void testHardWrap() throws Exception {
    configureFromFileText("a.xml",
                          "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n" +
                          "<svg version=\"1.1\" id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
                          "<g>\n" +
                          "        <path clip-path=\"url(#SVGID_2_)\" fill=\"#ffffff\" stroke=\"<selection>#000000</selection>\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-miterlimit=\"10\" d=\"M19.333,8.333V12c0,1.519-7.333,4-7.333,4s-7.333-2.481-7.333-4V8.333\"/>\n" +
                          "</g>\n" +
                          "</svg>");

    CodeStyleSettings clone = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().clone();
    clone.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    try {
      CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(clone);
      EditorTestUtil.performTypingAction(getEditor(), 'x');
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
    checkResultByText("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n" +
                      "<svg version=\"1.1\" id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
                      "<g>\n" +
                      "        <path clip-path=\"url(#SVGID_2_)\" fill=\"#ffffff\" stroke=\"x\" stroke-width=\"2\" stroke-linecap=\"round\" \n" +
                      "              stroke-linejoin=\"round\" stroke-miterlimit=\"10\" d=\"M19.333,8.333V12c0,1.519-7.333,4-7.333,4s-7.333-2.481-7.333-4V8.333\"/>\n" +
                      "</g>\n" +
                      "</svg>");
  }

  public void testHardWrapInComment() throws Exception {
    configureFromFileText("a.xml",
                          "<!-- Some very long and informative xml comment to trigger hard wrapping indeed. Too short? Dave, let me ask you something. Are hard wraps working? What do we live for? What ice-cream do you like? Who am I?????????????????????????????????????????????????????????????????????????????????????????????????<caret>-->");

    CodeStyleSettings clone = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().clone();
    clone.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    try {
      CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(clone);
      EditorTestUtil.performTypingAction(getEditor(), '?');
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
    checkResultByText("<!-- Some very long and informative xml comment to trigger hard wrapping indeed. Too short? Dave, let me ask you \n" +
                      "something. Are hard wraps working? What do we live for? What ice-cream do you like? Who am I??????????????????????????????????????????????????????????????????????????????????????????????????-->");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/');
  }

}
