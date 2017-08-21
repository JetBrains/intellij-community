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

  public void testEnterPerformance() {
    configureByFile(getTestFilePath(true));
    for (int i = 0; i < 3; i++) {
      EditorTestUtil.performTypingAction(myEditor, '\n');
    }
    PlatformTestUtil.startPerformanceTest("Xml editor enter", 5000, () -> {
      for (int i = 0; i < 3; i ++) {
        EditorTestUtil.performTypingAction(myEditor, '\n');
      }
    }).attempts(1).assertTiming();
    checkResultByFile(getTestFilePath(false));
  }

  public void testHardWrap() {
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

  public void testHardWrapInComment() {
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
