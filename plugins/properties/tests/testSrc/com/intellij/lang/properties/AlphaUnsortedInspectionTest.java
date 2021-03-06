// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.properties.codeInspection.unsorted.AlphaUnsortedPropertiesFileInspection;
import com.intellij.lang.properties.codeInspection.unsorted.AlphaUnsortedPropertiesFileInspectionSuppressor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Locale;

/**
 * @author Dmitry Batkovich
 */
public class AlphaUnsortedInspectionTest extends BasePlatformTestCase {
  public void testUnsorted() {
    doTest();
  }

  public void testSorted() {
    doTest();
  }

  public void testUnsortedSuppressed() {
    ExtensionTestUtil.maskExtensions(AlphaUnsortedPropertiesFileInspectionSuppressor.EP_NAME,
                                     Collections.<AlphaUnsortedPropertiesFileInspectionSuppressor>singletonList(new AlphaUnsortedPropertiesFileInspectionSuppressor() {
                                      @Override
                                      public boolean suppressInspectionFor(@NotNull PropertiesFile propertiesFile) {
                                        return propertiesFile.getName().toLowerCase(Locale.ENGLISH).contains("suppress");
                                      }
                                    }), myFixture.getTestRootDisposable());
    doTest();
  }

  public void testFix() {
    myFixture.configureByText("p.properties", "a=\n" +
                                              "c=\n" +
                                              "b=\\r\\n\\\n" +
                                              "f");
    myFixture.enableInspections(new AlphaUnsortedPropertiesFileInspection());
    final IntentionAction intention = myFixture.getAvailableIntention("Sort resource bundle files", "p.properties");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("a=\n" +
                          "b=\\r\\n\\\n" +
                          "f\n" +
                          "c=");
  }

  public void testFixComments() {
    myFixture.configureByText("p.properties", "a=a\n" +
                                              "d=d\n" +
                                              "# some comment on \"e\"\n" +
                                              "# this is multiline comment\n" +
                                              "e=e\n" +
                                              "b=b\n" +
                                              "c=b");
    myFixture.enableInspections(new AlphaUnsortedPropertiesFileInspection());
    final IntentionAction intention = myFixture.getAvailableIntention("Sort resource bundle files", "p.properties");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("a=a\n" +
                          "b=b\n" +
                          "c=b\n" +
                          "d=d\n" +
                          "# some comment on \"e\"\n" +
                          "# this is multiline comment\n" +
                          "e=e");
  }

  private void doTest() {
    myFixture.testInspection(getTestName(true), new LocalInspectionToolWrapper(new AlphaUnsortedPropertiesFileInspection()));
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/tests/testData/alphaUnsorted/";
  }
}
