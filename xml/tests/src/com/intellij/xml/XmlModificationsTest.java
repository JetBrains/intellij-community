// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.util.IncorrectOperationException;

public class XmlModificationsTest extends PsiTestCase {
  private XmlElementFactory myFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFactory = XmlElementFactory.getInstance(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    myFactory = null;

    super.tearDown();
  }

  public void testAddAttribute1() {
    final XmlTag tag = myFactory.createTagFromText("<a></a>");
    tag.setAttribute("b", "");
    assertEquals("<a b=\"\"></a>", tag.getText());
    tag.setAttribute("c", "");
    assertEquals("<a b=\"\" c=\"\"></a>", tag.getText());
  }

  public void testAddAttribute2() {
    final XmlTag tag = myFactory.createTagFromText("<a/>");
    tag.setAttribute("b", "");
    assertEquals("<a b=\"\"/>", tag.getText());
    tag.setAttribute("c", "");
    assertEquals("<a b=\"\" c=\"\"/>", tag.getText());
  }

  public void testAddSubTag1() {
    final XmlTag tag = myFactory.createTagFromText("<a/>");
    tag.add(tag.createChildTag("b", "", null, false));
    assertEquals("<a><b/></a>", tag.getText());
  }

  public void testExceptionMessage() {
    XmlFile file =
      (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText(XMLLanguage.INSTANCE, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                                                                 "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                                                                 "package=\"com.example.amirsh.myapplication\">\n" +
                                                                                                 "<application\n" +
                                                                                                 "android:allowBackup=\"true\"\n" +
                                                                                                 "android:icon=\"@mipmap/ic_launcher\"\n" +
                                                                                                 "android:label=\"@string/app_name\"\n" +
                                                                                                 "android:roundIcon=\"@mipmap/ic_launcher_round\"\n" +
                                                                                                 "android:supportsRtl=\"true\"\n" +
                                                                                                 "android:theme=\"@style/AppTheme\">\n" +
                                                                                                 "<activity android:name=\".MainActivity\">\n" +
                                                                                                 "<intent-filter>\n" +
                                                                                                 "<action android:name=\"android.intent.action.MAIN\"/>\n" +
                                                                                                 "\n" +
                                                                                                 "<category android:name=\"android.intent.category.LAUNCHER\"/>\n" +
                                                                                                 "</intent-filter>\n" +
                                                                                                 "</activity>\n" +
                                                                                                 "</application>\n" +
                                                                                                 "\n" +
                                                                                                 "</manifest>");
    XmlTag subtag = myFactory.createTagFromText("<b/>");
    try {
      file.getRootTag().addSubTag(subtag, true);
      fail();
    }
    catch (IncorrectOperationException e) {
      assertTrue(e.getMessage().startsWith("Must not change PSI outside command or undo-transparent action."));
    }
  }

  public void testSetText() {
    final XmlTag tag = myFactory.createTagFromText("<a>foo</a>");
    XmlText[] elements = tag.getValue().getTextElements();
    assertEquals(1, elements.length);
    elements[0].setValue("");
    assertEquals("<a></a>", tag.getText());
  }
}
