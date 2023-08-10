// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.refactoring.rename.ResourceBundleRenamerFactory;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleTest extends BasePlatformTestCase {

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

  public void testDifferentPropertiesDontCombinedToResourceBundle() {
    final PsiFile xmlFile = myFixture.addFileToProject("p.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
      <properties>
      </properties>""");
    final PsiFile propFile = myFixture.addFileToProject("p.properties", "");

    final PropertiesFile xmlPropFile = PropertiesImplUtil.getPropertiesFile(xmlFile);
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(propFile);

    assertNotNull(xmlPropFile);
    assertNotNull(propertiesFile);

    assertEquals(xmlPropFile, assertOneElement(xmlPropFile.getResourceBundle().getPropertiesFiles()));
    assertEquals(propertiesFile, assertOneElement(propertiesFile.getResourceBundle().getPropertiesFiles()));
  }

  private void doTestRenameResourceBundleEntryFile(String fileNameToRenameBefore,
                                                   String fileNameToCheckBefore,
                                                   String fileNameToRenameAfter,
                                                   String fileNameToCheckAfter) {
    final PsiFile toRenameFile = myFixture.addFileToProject(fileNameToRenameBefore, "");
    final PsiFile toCheck = myFixture.addFileToProject(fileNameToCheckBefore, "");

    final RenameProcessor processor = new RenameProcessor(getProject(), toRenameFile, fileNameToRenameAfter, true, true);
    for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
      if (factory instanceof ResourceBundleRenamerFactory) {
        processor.addRenamerFactory(factory);
      }
    }
    processor.run();

    assertEquals(fileNameToCheckAfter, toCheck.getName());
  }
}
