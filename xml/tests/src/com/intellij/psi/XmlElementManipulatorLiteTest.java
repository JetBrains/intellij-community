// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.XmlAttributeValueManipulator;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.XmlTextManipulator;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class XmlElementManipulatorLiteTest extends LightPlatformTestCase {
  public XmlFile createXmlFile(@NonNls final String xml) {
    return (XmlFile)createLightFile("a.xml", XmlFileType.INSTANCE, xml);
  }
  public PsiFile createLightFile(final String fileName, final FileType fileType, final String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, fileType, text);
  }

  public void testXmlAttributeValueManipulatorRange() {
    final XmlFile xmlFile = createXmlFile("<root aaa= bbb=\"a\"/>");
    final XmlTag tag = xmlFile.getDocument().getRootTag();

    final XmlAttributeValueManipulator manipulator = new XmlAttributeValueManipulator();
    assertEquals(TextRange.from(0, 0), manipulator.getRangeInElement(tag.getAttribute("aaa").getValueElement()));
    assertEquals(TextRange.from(1, 1), manipulator.getRangeInElement(tag.getAttribute("bbb").getValueElement()));
  }

  public void testXmlTextManipulator() {
    final XmlFile xmlFile = createXmlFile("<root>xxx</root>");
    final XmlTag tag = xmlFile.getDocument().getRootTag();

    final XmlText text = tag.getValue().getTextElements()[0];

    final XmlTextManipulator manipulator = new XmlTextManipulator();
    assertEquals(TextRange.from(0, 3), manipulator.getRangeInElement(text));

    manipulator.handleContentChange(text, TextRange.from(1, 1), "AA");
    assertEquals("<root>xAAx</root>", tag.getText());
  }

  public void testAttrValueChange() {
    final XmlFile xmlFile = createXmlFile("<root bbb=\"${fn:endsWith(item.class,'com.vships.vignette.domain.NewsItem')}\"/>");
    final XmlAttributeValue value = xmlFile.getDocument().getRootTag().getAttribute("bbb").getValueElement();

    final XmlAttributeValueManipulator manipulator = new XmlAttributeValueManipulator();
    manipulator.handleContentChange(value, new TextRange(27, 63), "xxxwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww");
  }

  public void testAttrValueWithEntities() {
    final XmlFile xmlFile = createXmlFile("<root bbb=\"a&gt;\"/>");
    final XmlAttributeValue value = xmlFile.getDocument().getRootTag().getAttribute("bbb").getValueElement();

    final XmlAttributeValueManipulator manipulator = new XmlAttributeValueManipulator();
    manipulator.handleContentChange(value, new TextRange(1, 6), "xxxwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww");
  }

  public void testAttrValueEscapeDouble() {
    final XmlFile xmlFile = createXmlFile("<root bbb=\"\"/>");
    XmlTag tag = xmlFile.getDocument().getRootTag();
    final XmlAttributeValue value = tag.getAttribute("bbb").getValueElement();

    final XmlAttributeValueManipulator manipulator = new XmlAttributeValueManipulator();
    manipulator.handleContentChange(value, TextRange.from(1, 0), "\"'");
    assertEquals("<root bbb=\"&quot;'\"/>", tag.getText());
  }

  public void testAttrValueEscapeSingle() {
    final XmlFile xmlFile = createXmlFile("<root bbb=''/>");
    XmlTag tag = xmlFile.getDocument().getRootTag();
    final XmlAttributeValue value = tag.getAttribute("bbb").getValueElement();

    final XmlAttributeValueManipulator manipulator = new XmlAttributeValueManipulator();
    manipulator.handleContentChange(value, TextRange.from(1, 0), "\"'");
    assertEquals("<root bbb='\"&apos;'/>", tag.getText());
  }

  public void testAttrValueEscapeDoubleWithEntity() {
    final XmlFile xmlFile = createXmlFile("<root bbb=\"&#34;\"/>");
    XmlTag tag = xmlFile.getDocument().getRootTag();
    final XmlAttributeValue value = tag.getAttribute("bbb").getValueElement();

    final XmlAttributeValueManipulator manipulator = new XmlAttributeValueManipulator();
    manipulator.handleContentChange(value, TextRange.from(1, 0), "\"'");
    assertEquals("<root bbb=\"&#34;'&#34;\"/>", tag.getText());
  }

  public void testAttrValueEscapeSingleWithEntity() {
    final XmlFile xmlFile = createXmlFile("<root bbb='&#39;'/>");
    XmlTag tag = xmlFile.getDocument().getRootTag();
    final XmlAttributeValue value = tag.getAttribute("bbb").getValueElement();

    final XmlAttributeValueManipulator manipulator = new XmlAttributeValueManipulator();
    manipulator.handleContentChange(value, TextRange.from(1, 0), "\"'");
    assertEquals("<root bbb='\"&#39;&#39;'/>", tag.getText());
  }
}
