/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.javaee.XMLCatalogManager;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.apache.xml.resolver.CatalogManager;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Vector;

/**
 * @author Dmitry Avdeev
 *         Date: 7/20/12
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public class XMLCatalogManagerTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testCatalogManager() throws Exception {
    XMLCatalogManager manager = getManager();
    CatalogManager catalogManager = manager.getManager();
    Vector files = catalogManager.getCatalogFiles();
    assertEquals(1, files.size());
    String filePath = (String)files.get(0);
    assertTrue(filePath, filePath.endsWith("catalog.xml"));
    assertTrue(filePath, new File(new URI(filePath)).exists());
  }

  public void testResolve() throws Exception {
    String resolve = getManager().resolve("-//W3C//DTD XHTML 1.0 Strict//EN");
    assertNotNull(resolve);
    assertTrue(resolve, resolve.endsWith("/catalog/xhtml1-strict.dtd"));
  }

  private XMLCatalogManager getManager() throws IOException {
    return new XMLCatalogManager(getTestDataPath() + "catalog.properties");
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/catalog/";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public XMLCatalogManagerTest() {
    IdeaTestCase.initPlatformPrefix();
  }
}
