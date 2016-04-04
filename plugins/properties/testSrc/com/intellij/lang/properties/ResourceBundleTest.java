/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.refactoring.rename.ResourceBundleRenamerFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testDefaultPropertyFile() {
    final PsiFile rawDefault = myFixture.addFileToProject("p.properties", "");
    myFixture.addFileToProject("p_en.properties", "");
    final PropertiesFile defaultFile = PropertiesImplUtil.getPropertiesFile(rawDefault);
    assertNotNull(defaultFile);
    final PropertiesFile file = defaultFile.getResourceBundle().getDefaultPropertiesFile();
    assertTrue(file.getContainingFile().isEquivalentTo(defaultFile.getContainingFile()));
  }

  public void testRenameResourceBundleEntryFile() {
    doTestRenameResourceBundleEntryFile("old_p.properties", "old_p_en.properties",
                                        "new_p.properties", "new_p_en.properties");
  }

  public void testRenameResourceBundleEntryFile2() {
    doTestRenameResourceBundleEntryFile("ppp.properties", "ppp_en.properties",
                                        "qqq.properties", "qqq_en.properties");
  }

  public void testRenameResourceBundleEntryFile3() {
    doTestRenameResourceBundleEntryFile("p.properties.properties", "p.properties_en.properties",
                                        "p.properties", "p_en.properties");
  }

  public void testRenamePropertyKey() {
    final PsiFile toCheckFile = myFixture.addFileToProject("p.properties", "key=value");
    myFixture.configureByText("p_en.properties", "ke<caret>y=en_value");
    myFixture.renameElementAtCaret("new_key");
    assertEquals(toCheckFile.getText(), "new_key=value");
  }

  private void doTestRenameResourceBundleEntryFile(String fileNameToRenameBefore,
                                                   String fileNameToCheckBefore,
                                                   String fileNameToRenameAfter,
                                                   String fileNameToCheckAfter) {
    final PsiFile toRenameFile = myFixture.addFileToProject(fileNameToRenameBefore, "");
    final PsiFile toCheck = myFixture.addFileToProject(fileNameToCheckBefore, "");

    final RenameProcessor processor = new RenameProcessor(getProject(), toRenameFile, fileNameToRenameAfter, true, true);
    for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
      if (factory instanceof ResourceBundleRenamerFactory) {
        processor.addRenamerFactory(factory);
      }
    }
    processor.run();

    assertEquals(fileNameToCheckAfter, toCheck.getName());
  }
}
