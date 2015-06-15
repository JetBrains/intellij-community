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
package org.intellij.lang.xpath;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 17.12.2008
*/
public abstract class TestBase extends UsefulTestCase {

  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final IdeaProjectTestFixture fixture = factory.createLightFixtureBuilder().getFixture();
    myFixture = factory.createCodeInsightFixture(fixture);

    myFixture.setTestDataPath(getTestDataPath());

    myFixture.setUp();
  }

  private String getTestDataPath() {
    return getTestDataPath(getSubPath());
  }

  public static String getTestDataPath(String subPath) {
    // path logic taken from RegExpSupport tests
    final String def = PluginPathManager.getPluginHomePath("xpath") + "/xpath-lang/testData";
    return System.getProperty("idea.xpath.testdata-path", def) + "/" + subPath;
  }

  protected abstract String getSubPath();

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
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
