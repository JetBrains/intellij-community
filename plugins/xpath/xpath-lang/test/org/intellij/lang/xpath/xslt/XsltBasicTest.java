/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;

public class XsltBasicTest extends TestBase {
  public void testSupportedXslt10() {
    doTestXsltSupport();
  }

  public void testSupportedXslt10_Loaded() {
    doTestXsltSupport();
  }

  public void testSupportedXslt11() {
    doTestXsltSupport();
  }

  public void testSupportedSimplifiedXslt() {
    doTestXsltSupport();
  }

  public void testSupportedSimplifiedXslt_Loaded() {
    doTestXsltSupport();
  }

  public void testSupportedXslt20() {
    doTestXsltSupport();
  }

  public void testUnsupportedXsltNoVersion() {
    doTestXsltSupport();
  }

  public void testUnsupportedXsltNoVersion_Loaded() {
    doTestXsltSupport();
  }

  public void testUnsupportedNoXslt() {
    doTestXsltSupport();
  }

  public void testUnsupportedNoXslt_Loaded() {
    doTestXsltSupport();
  }

  public void testUnsupportedNoXslt2() {
    doTestXsltSupport();
  }

  // actually a PSI test: IDEADEV-35024
  public void testUnsupportedNoXslt2_Loaded() {
    doTestXsltSupport();
  }

  private void doTestXsltSupport() {
    configure();

    final XsltChecker.LanguageLevel level = XsltSupport.getXsltLanguageLevel(myFixture.getFile());
    if (level != XsltChecker.LanguageLevel.NONE) {
      assertTrue(getName().contains("Supported"));
      assertTrue(XsltSupport.isXsltFile(myFixture.getFile()));
    } else {
      assertTrue(getName().contains("Unsupported"));
      assertFalse(XsltSupport.isXsltFile(myFixture.getFile()));
    }
  }

  private void configure() {
    final String fileName = getTestFileName();
    String path = fileName.replaceAll("_.*$", "") + ".xsl";
    final VirtualFile file = myFixture.copyFileToProject(path);
    myFixture.openFileInEditor(file);
    if (fileName.endsWith("_Loaded")) {
      ((XmlFile)myFixture.getFile()).getDocument();
      assertTrue(((PsiFileEx)myFixture.getFile()).isContentsLoaded());
    } else {
      assertFalse(((PsiFileEx)myFixture.getFile()).isContentsLoaded());
    }
  }

  @Override
  protected String getSubPath() {
    return "xslt";
  }
}
