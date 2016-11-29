/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xml;

import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;

import java.io.File;

/**
 * @author ibessonov
 */
public class XmlEntityManagerCachingTest extends LightPlatformCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ExternalResourceManagerExImpl.registerResourceTemporarily("http://dl.google.com/gwt/DTD/xhtml.ent",
                                                              getTestDataPath() + "xhtml.ent", getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("urn:ui:com.google.gwt.uibinder",
                                                              getTestDataPath() + "UiBinder.xsd", getTestRootDisposable());

    myFixture.enableInspections(CheckXmlFileWithXercesValidatorInspection.class);
  }

  public void testXmlEntityManagerCaching() {
    myFixture.configureByFile(getTestName(false) + ".ui.xml");
    myFixture.checkHighlighting();
    myFixture.type('\b'); // edit content, document has to be valid after that
    myFixture.checkHighlighting();
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/xml/";
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + getBasePath();
  }
}
