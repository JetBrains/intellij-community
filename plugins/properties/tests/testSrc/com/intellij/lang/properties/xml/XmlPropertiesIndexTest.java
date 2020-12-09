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
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContentImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XmlPropertiesIndexTest extends BasePlatformTestCase {

  public void testIndex() throws IOException {
    PsiFile psiFile = myFixture.configureByFile("foo.xml");
    final VirtualFile file = psiFile.getVirtualFile();
    Map<XmlPropertiesIndex.Key, String> map = new XmlPropertiesIndex().map(FileContentImpl.createByFile(file));

    assertEquals(3, map.size());
    XmlPropertiesIndex.Key fooKey = new XmlPropertiesIndex.Key("foo");
    assertEquals("bar", map.get(fooKey));
    assertEquals("baz", map.get(new XmlPropertiesIndex.Key("fu")));
    assertTrue(map.containsKey(XmlPropertiesIndex.MARKER_KEY));

    assertTrue(XmlPropertiesIndex.isPropertiesFile((XmlFile)psiFile));

    List<String> values = new ArrayList<>();
    FileBasedIndex.getInstance().processValues(
      XmlPropertiesIndex.NAME,
      fooKey,
      null,
      (file1, value) -> values.add(value),
      GlobalSearchScope.allScope(getProject()));
    assertEquals("bar", assertOneElement(values));
  }

  public void testSystemId() throws IOException {
    PsiFile psiFile = myFixture.configureByFile("wrong.xml");
    final VirtualFile file = psiFile.getVirtualFile();
    Map<XmlPropertiesIndex.Key, String> map = new XmlPropertiesIndex().map(FileContentImpl.createByFile(file));

    assertEquals(0, map.size());

    assertFalse(XmlPropertiesIndex.isPropertiesFile((XmlFile)psiFile));
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/tests/testData/xml/";
  }
}
