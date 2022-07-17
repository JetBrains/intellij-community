// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;

public abstract class TestBase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final IdeaProjectTestFixture fixture = factory.createLightFixtureBuilder(getTestName(false)).getFixture();
    myFixture = factory.createCodeInsightFixture(fixture);
    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
  }

  private String getTestDataPath() {
    return getTestDataPath(getSubPath());
  }

  public static String getTestDataPath(String subPath) {
    // path logic taken from intellij.regexp tests
    final String def = PluginPathManager.getPluginHomePath("xpath") + "/xpath-lang/testData";
    return System.getProperty("idea.xpath.testdata-path", def) + "/" + subPath;
  }

  protected abstract String getSubPath();

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  protected String getTestFileName() {
    final String s = getName().substring("test".length());
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }
}
