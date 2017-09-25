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

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 7/26/11
 */
public class XmlPropertiesTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testXmlProperties() {
    myFixture.configureByFile("foo.xml");
    List<PropertiesFile> files = PropertiesReferenceManager.getInstance(getProject()).findPropertiesFiles(myModule, "foo");
    assertEquals(1, files.size());
    PropertiesFile file = files.get(0);
    assertEquals(1, file.findPropertiesByKey("foo").size());

    List<IProperty> properties = PropertiesImplUtil.findPropertiesByKey(getProject(), "foo");
    assertEquals(1, properties.size());
  }

  public void testWrongFile() {
    PsiFile psiFile = myFixture.configureByFile("wrong.xml");
    PropertiesFile file = PropertiesImplUtil.getPropertiesFile(psiFile);
    assertNull(file);
  }

  public void testHighlighting() {
    myFixture.testHighlighting("foo.xml");
  }

  public void testAddProperty() {
    final PsiFile psiFile = myFixture.configureByFile("foo.xml");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    assertNotNull(propertiesFile);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      propertiesFile.addProperty("kkk", "vvv");
    });

    final IProperty property = propertiesFile.findPropertyByKey("kkk");
    assertNotNull(property);
    assertEquals("vvv", property.getValue());
  }

  public void testAddProperty2() {
    final PsiFile psiFile = myFixture.configureByFile("foo.xml");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    assertNotNull(propertiesFile);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      propertiesFile.addProperty("kkk", "vvv");
    });

    final IProperty property = propertiesFile.findPropertyByKey("kkk");
    assertNotNull(property);
    assertEquals("vvv", property.getValue());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      propertiesFile.addProperty("kkk2", "vvv");
    });

    final IProperty property2 = propertiesFile.findPropertyByKey("kkk2");
    assertNotNull(property2);
    assertEquals("vvv", property2.getValue());
  }

  public void testAddPropertyInAlphaOrder() {
    final PsiFile psiFile = myFixture.configureByFile("bar.xml");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);
    assertNotNull(propertiesFile);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      propertiesFile.addProperty("d", "vvv");
      propertiesFile.addProperty("a", "vvv");
      propertiesFile.addProperty("l", "vvv");
      propertiesFile.addProperty("v", "vvv");
    });
    assertTrue(propertiesFile.isAlphaSorted());
    assertTrue(PropertiesImplUtil.getPropertiesFile(psiFile).isAlphaSorted());
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/xml/";
  }
}
