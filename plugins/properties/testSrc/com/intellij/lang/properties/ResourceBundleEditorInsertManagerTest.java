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

import com.intellij.lang.properties.editor.ResourceBundlePropertiesUpdateManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleEditorInsertManagerTest extends LightPlatformCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/insertManager/";
  }

  public void testIsAlphaSorted() {
    myFixture.configureByFile(getTestName(true) + "/p.properties");
    myFixture.configureByFile(getTestName(true) + "/p_en.properties");
    myFixture.configureByFile(getTestName(true) + "/p_fr.properties");
    final PsiFile file = myFixture.configureByFile(getTestName(true) + "/p_ru.properties");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    final ResourceBundlePropertiesUpdateManager
      manager = new ResourceBundlePropertiesUpdateManager(propertiesFile.getResourceBundle());
    manager.reload();
    assertTrue(manager.isAlphaSorted());
  }

  public void testAddToAlphaOrdered() {
    doTest(new TestAction() {
      @Override
      public void doTestAction(PropertiesFile baseFile, PropertiesFile translationFile, ResourceBundlePropertiesUpdateManager manager) {
        assertTrue(manager.isAlphaSorted());
        manager.insertNewProperty("l", "v");
        manager.insertOrUpdateTranslation("d", "v", baseFile);
        manager.insertOrUpdateTranslation("r", "v", translationFile);
        manager.insertNewProperty("a", "v");
        manager.insertOrUpdateTranslation("a", "v", translationFile);
        manager.insertNewProperty("z", "v");
        manager.insertOrUpdateTranslation("z", "v", translationFile);
        manager.insertOrUpdateTranslation("l", "v", translationFile);
        manager.insertOrUpdateTranslation("e", "v", translationFile);
        manager.insertOrUpdateTranslation("t", "v", baseFile);
      }
    });
  }

  public void testAddToOrdered() {
    doTest(new TestAction() {
      @Override
      public void doTestAction(PropertiesFile baseFile, PropertiesFile translationFile, ResourceBundlePropertiesUpdateManager manager) {
        assertFalse(manager.isAlphaSorted());
        manager.insertNewProperty("bnm", "v");
        manager.insertNewProperty("uio", "v");
        manager.insertOrUpdateTranslation("uio", "v", translationFile);
        manager.insertOrUpdateTranslation("qwe", "v", translationFile);
        manager.insertOrUpdateTranslation("zxc", "v", baseFile);
      }
    });
  }

  public void testAddToUnordered() {
    doTest(new TestAction() {
      @Override
      public void doTestAction(PropertiesFile baseFile, PropertiesFile translationFile, ResourceBundlePropertiesUpdateManager manager) {
        assertFalse(manager.isAlphaSorted());
        manager.insertOrUpdateTranslation("bnm", "v", translationFile);
        manager.insertNewProperty("ghj", "v");
        manager.insertOrUpdateTranslation("ghj", "v", translationFile);
        manager.insertNewProperty("uio", "v");
        manager.insertOrUpdateTranslation("uio", "v", translationFile);
      }
    });
  }

  private void doTest(final TestAction testAction) {
    final PsiFile baseFile = myFixture.configureByFile(getTestName(true) + "/p.properties");
    final PsiFile translationFile = myFixture.configureByFile(getTestName(true) + "/p_en.properties");
    final PropertiesFile basePropertiesFile = PropertiesImplUtil.getPropertiesFile(baseFile);
    assertNotNull(basePropertiesFile);
    final ResourceBundle bundle = basePropertiesFile.getResourceBundle();
    final ResourceBundlePropertiesUpdateManager manager = new ResourceBundlePropertiesUpdateManager(bundle);
    assertInstanceOf(manager, ResourceBundlePropertiesUpdateManager.class);
    testAction.doTestAction(basePropertiesFile, PropertiesImplUtil.getPropertiesFile(translationFile),
                            manager);
    myFixture.checkResultByFile(getTestName(true) + "/p.properties", getTestName(true) + "/p-after.properties", true);
    myFixture.checkResultByFile(getTestName(true) + "/p_en.properties", getTestName(true) + "/p-after_en.properties",true);
  }

  private interface TestAction {
    void doTestAction(PropertiesFile baseFile, PropertiesFile translationFile, ResourceBundlePropertiesUpdateManager manager);
  }
}
