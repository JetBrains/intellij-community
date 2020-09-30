// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

public class XmlTextTest extends LightJavaCodeInsightTestCase {
  public void testInsertAtOffset() {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      @Language("XML")
      String xml = "<root>0123456789</root>";
      XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject())
                                            .createFileFromText("foo.xml", XmlFileType.INSTANCE, xml, 1, true, false);
      XmlTag root = file.getDocument().getRootTag();
      final XmlText text1 = root.getValue().getTextElements()[0];

      assertFalse(CodeEditUtil.isNodeGenerated(root.getNode()));
      final XmlText text = text1;

      final XmlElement element = text.insertAtOffset(XmlElementFactory.getInstance(getProject()).createTagFromText("<bar/>"), 5);
      assertNotNull(element);
      assertTrue(element instanceof XmlText);
      assertEquals("01234", element.getText());
      assertEquals("<root>01234<bar/>56789</root>", text.getContainingFile().getText());
    });
  }

  public void testPhysicalToDisplayIfHasGaps2() {
    @Language("XML")
    String xml = "<div>&amp;abc</div>";
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("foo.xml", XmlFileType.INSTANCE, xml);
    XmlTag root = file.getDocument().getRootTag();
    final XmlText text = root.getValue().getTextElements()[0];

    assertEquals("&abc", text.getValue());
    assertEquals(0, text.physicalToDisplay(0));
    assertEquals(1, text.physicalToDisplay(5));
    assertEquals(2, text.physicalToDisplay(6));
    assertEquals(3, text.physicalToDisplay(7));
    assertEquals(4, text.physicalToDisplay(8));
  }

  public void testDisplayToPhysical() {
    @Language("XML")
    String xml = "<div>&amp;abc</div>";
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("foo.xml", XmlFileType.INSTANCE, xml);
    XmlTag root = file.getDocument().getRootTag();
    final XmlText text = root.getValue().getTextElements()[0];

    assertEquals("&abc", text.getValue());
    assertEquals(0, text.displayToPhysical(0));
    assertEquals(5, text.displayToPhysical(1));
    assertEquals(6, text.displayToPhysical(2));
    assertEquals(7, text.displayToPhysical(3));
    assertEquals(8, text.displayToPhysical(4));
  }

  public void testDisplayToPhysical2() {
    @Language("XML")
    String xml = "<div><![CDATA[ ]]></div>";
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("foo.xml", XmlFileType.INSTANCE, xml);
    XmlTag root = file.getDocument().getRootTag();
    final XmlText text = root.getValue().getTextElements()[0];

    assertEquals(" ", text.getValue());
    assertEquals(9, text.displayToPhysical(0));
    assertEquals(13, text.displayToPhysical(1));
  }

  public void testXmlAttributeEscaperCalculatesDisplayToPhysicalCorrectlyInPresenseOfXmlEntities() {
    @Language("HTML")
    String xml = "<!DOCTYPE html>\n" +
                 "<html xmlns=\"http://www.w3.org/1999/xhtml\"\n" +
                 "\txmlns:th=\"http://www.thymeleaf.org\">\n" +
                 "  <td style=\"text-align: right\" th:utext=\"'&euro; ' + ${{item.netPrice}}\">XXX</td>\n" +
                 "</html>";
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("foo.xml", XmlFileType.INSTANCE, xml);
    XmlTag root = file.getDocument().getRootTag();
    XmlTag tag = root.findFirstSubTag("td");
    XmlAttribute attribute = tag.getAttribute("th:utext");
    XmlAttributeValueImpl value = (XmlAttributeValueImpl)attribute.getValueElement();
    assertEquals("'&#8364; ' + ${{item.netPrice}}", attribute.getDisplayValue());

    LiteralTextEscaper<XmlAttributeValueImpl> escaper = value.createLiteralTextEscaper();
    int offset = escaper.getOffsetInHost(31, new TextRange(1, 31));
    assertEquals(31, offset);
  }
}
