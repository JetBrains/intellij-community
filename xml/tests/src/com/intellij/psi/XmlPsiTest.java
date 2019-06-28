// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.xml.XmlComment;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class XmlPsiTest extends LightJavaCodeInsightFixtureTestCase {

  public void testXmlComment() {
    PsiFile file = myFixture.configureByText(XmlFileType.INSTANCE, "<!-- foo -->");
    XmlComment element = (XmlComment)file.findElementAt(0).getParent();
    assertEquals(" foo ", element.getCommentText());
  }
}
