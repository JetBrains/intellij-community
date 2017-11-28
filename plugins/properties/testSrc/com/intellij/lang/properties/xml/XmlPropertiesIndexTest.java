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
package com.intellij.lang.properties.xml;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.indexing.FileContentImpl;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 7/25/11
 */
public class XmlPropertiesIndexTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testIndex() {
    final VirtualFile file = myFixture.configureByFile("foo.xml").getVirtualFile();
    Map<XmlPropertiesIndex.Key, String> map = new XmlPropertiesIndex().map(FileContentImpl.createByFile(file));

    assertEquals(3, map.size());
    assertEquals("bar", map.get(new XmlPropertiesIndex.Key("foo")));
    assertEquals("baz", map.get(new XmlPropertiesIndex.Key("fu")));
    assertTrue(map.containsKey(XmlPropertiesIndex.MARKER_KEY));
  }

  public void testSystemId() {
    final VirtualFile file = myFixture.configureByFile("wrong.xml").getVirtualFile();
    Map<XmlPropertiesIndex.Key, String> map = new XmlPropertiesIndex().map(FileContentImpl.createByFile(file));

    assertEquals(0, map.size());
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/xml/";
  }
}
