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
package com.intellij.lang.properties;

import com.intellij.lang.properties.editor.IgnoredPropertiesFilesSuffixesManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.util.Collections;

/**
 * @author Dmitry Batkovich
 */
public class IgnoredPropertiesFilesSuffixesTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testPropertyIsComplete() {
    myFixture.addFileToProject("p.properties", "key=value");
    myFixture.addFileToProject("p_en.properties", "key=value eng");
    final PsiFile file = myFixture.addFileToProject("p_ru.properties", "");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    assertNotNull(propertiesFile);
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    assertSize(3, resourceBundle.getPropertiesFiles());
    final IgnoredPropertiesFilesSuffixesManager suffixesManager = IgnoredPropertiesFilesSuffixesManager.getInstance(getProject());
    suffixesManager.addSuffixes(Collections.singleton("ru"));
    assertTrue(suffixesManager.isPropertyComplete(resourceBundle, "key"));
  }

  public void testPropertyIsComplete2() {
    myFixture.addFileToProject("p.properties", "key=value");
    myFixture.addFileToProject("p_en.properties", "key=value eng");
    final PsiFile file = myFixture.addFileToProject("p_ru.properties", "key=value rus");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    assertNotNull(propertiesFile);
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    assertSize(3, resourceBundle.getPropertiesFiles());
    final IgnoredPropertiesFilesSuffixesManager suffixesManager = IgnoredPropertiesFilesSuffixesManager.getInstance(getProject());
    assertTrue(suffixesManager.isPropertyComplete(resourceBundle, "key"));
  }

  public void testPropertyIsIncomplete() {
    myFixture.addFileToProject("p.properties", "key=value");
    myFixture.addFileToProject("p_en.properties", "key=value eng");
    myFixture.addFileToProject("p_fr.properties", "");
    final PsiFile file = myFixture.addFileToProject("p_ru.properties", "");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    assertNotNull(propertiesFile);
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    assertSize(4, resourceBundle.getPropertiesFiles());
    final IgnoredPropertiesFilesSuffixesManager suffixesManager = IgnoredPropertiesFilesSuffixesManager.getInstance(getProject());
    suffixesManager.addSuffixes(Collections.singleton("ru"));
    assertFalse(suffixesManager.isPropertyComplete(resourceBundle, "key"));
  }

  public void testPropertyIsIncomplete2() {
    myFixture.addFileToProject("p.properties", "key=value");
    myFixture.addFileToProject("p_en.properties", "");
    myFixture.addFileToProject("p_en_EN.properties", "");
    myFixture.addFileToProject("p_en_GB.properties", "");
    final PsiFile file = myFixture.addFileToProject("p_en_US.properties", "");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    assertNotNull(propertiesFile);
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    assertSize(5, resourceBundle.getPropertiesFiles());
    final IgnoredPropertiesFilesSuffixesManager suffixesManager = IgnoredPropertiesFilesSuffixesManager.getInstance(getProject());
    suffixesManager.addSuffixes(Collections.singleton("en"));
    assertFalse(suffixesManager.isPropertyComplete(resourceBundle, "key"));
  }

  @Override
  public void tearDown() throws Exception {
    IgnoredPropertiesFilesSuffixesManager.getInstance(getProject()).setSuffixes(Collections.<String>emptySet());
    super.tearDown();
  }
}
