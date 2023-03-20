// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class XmlModificationsTest extends LightPlatformTestCase {
  @NotNull
  private XmlTag createTag(String s) {
    return XmlElementFactory.getInstance(getProject()).createTagFromText(s);
  }

  public void testAddAttribute1() {
    XmlTag tag = createTag("<a></a>");
    tag.setAttribute("b", "");
    assertEquals("<a b=\"\"></a>", tag.getText());
    tag.setAttribute("c", "");
    assertEquals("<a b=\"\" c=\"\"></a>", tag.getText());
  }

  public void testAddAttribute2() {
    XmlTag tag = createTag("<a/>");
    tag.setAttribute("b", "");
    assertEquals("<a b=\"\"/>", tag.getText());
    tag.setAttribute("c", "");
    assertEquals("<a b=\"\" c=\"\"/>", tag.getText());
  }

  public void testAddSubTag1() {
    XmlTag tag = createTag("<a/>");
    tag.add(tag.createChildTag("b", "", null, false));
    assertEquals("<a><b/></a>", tag.getText());
  }

  public void testExceptionMessage() {
    XmlFile file =
      (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText(XMLLanguage.INSTANCE, """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.example.amirsh.myapplication">
        <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity android:name=".MainActivity">
        <intent-filter>
        <action android:name="android.intent.action.MAIN"/>

        <category android:name="android.intent.category.LAUNCHER"/>
        </intent-filter>
        </activity>
        </application>

        </manifest>""");
    XmlTag subtag = createTag("<b/>");
    try {
      file.getRootTag().addSubTag(subtag, true);
      fail();
    }
    catch (IncorrectOperationException e) {
      assertTrue(e.getMessage().startsWith("Must not change PSI outside command or undo-transparent action."));
    }
  }

  public void testSetText() {
    XmlTag tag = createTag("<a>foo</a>");
    XmlText[] elements = tag.getValue().getTextElements();
    assertEquals(1, elements.length);
    elements[0].setValue("");
    assertEquals("<a></a>", tag.getText());
  }
}
