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

/*
 * @author max
 */
package com.intellij.codeInsight;

import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilderDriver;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlBuilderTest extends LightJavaCodeInsightTestCase {
  private static class TestXmlBuilder implements XmlBuilder {
    private final StringBuilder builder = new StringBuilder();
    private final StringBuilder currentPhysicalText = new StringBuilder();
    private final StringBuilder currentDisplayText = new StringBuilder();
    private final @NotNull ProcessingOrder myTagProcessingOrder;

    TestXmlBuilder(@NotNull ProcessingOrder tagsAndAttributes) {
      myTagProcessingOrder = tagsAndAttributes;
    }

    @Override
    public void attribute(final @NotNull CharSequence name, final @NotNull CharSequence value, final int startoffset, final int endoffset) {
      flushText();
      builder.append("ATT: name='").append(name).append("' value='").append(value).append("'\n");
    }

    @Override
    public void endTag(final @NotNull CharSequence localName, final @NotNull String namespace, final int startoffset, final int endoffset) {
      flushText();
      builder.append("ENDTAG: name='").append(localName).append("' namespace='").append(namespace).append("'\n");
    }

    @Override
    public void doctype(@Nullable final CharSequence publicId, @Nullable final CharSequence systemId, final int startOffset, final int endOffset) {
        }

    @Override
    public @NotNull ProcessingOrder startTag(final @NotNull CharSequence localName, final @NotNull String namespace, final int startoffset, final int endoffset,
                                             final int headerEndOffset) {
      flushText();
      builder.append("TAG: name='").append(localName).append("' namespace='").append(namespace).append("'\n");
      return myTagProcessingOrder;
    }

    @Override
    public void textElement(final @NotNull CharSequence display, final @NotNull CharSequence physical, final int startoffset, final int endoffset) {
      currentPhysicalText.append(physical);
      currentDisplayText.append(display);
    }

    @Override
    public void entityRef(final @NotNull CharSequence ref, final int startOffset, final int endOffset) {
      flushText();
      builder.append("REF: '").append(ref).append("'\n");
    }

    @Override
    public void error(@NotNull String message, int startOffset, int endOffset) {
      flushText();
      builder.append("ERROR: '").append(message).append("'\n");
    }

    private void flushText() {
      if (currentPhysicalText.length() > 0) {
        builder.append("TEXT: '").append(currentPhysicalText).append("' DISPLAY: '").append(currentDisplayText).append("'\n");
        currentPhysicalText.setLength(0);
        currentDisplayText.setLength(0);
      }
    }

    public String getResult() {
      flushText();
      return builder.toString();
    }
  }
  
  public void testEmptyXml() {
    doTest("<root/>", """
      TAG: name='root' namespace=''
      ENDTAG: name='root' namespace=''
      """, XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES);
  }

  public void testRealJspx() {
    doTest(
      /* Test: */
      """
        <jsp:root xmlns:jsp="http://java.sun.com/JSP/Page" xmlns="http://www.w3.org/1999/xhtml" version="2.0"
                  xmlns:spring="http://www.springframework.org/tags" xmlns:c="http://java.sun.com/jsp/jstl/core">
        <html>
          <c:set var="foo" value="${1}"/>
          <c:set var="foobar" value="${2}"/>
          <spring:bind path="test.fieldName">
            <jsp:scriptlet></jsp:scriptlet>
            </spring:bind>
        </html>
        </jsp:root>""",

      /* Expected result: */
      """
        TAG: name='root' namespace='http://java.sun.com/JSP/Page'
        ATT: name='xmlns:jsp' value='http://java.sun.com/JSP/Page'
        ATT: name='xmlns' value='http://www.w3.org/1999/xhtml'
        ATT: name='version' value='2.0'
        ATT: name='xmlns:spring' value='http://www.springframework.org/tags'
        ATT: name='xmlns:c' value='http://java.sun.com/jsp/jstl/core'
        TAG: name='html' namespace='http://www.w3.org/1999/xhtml'
        TAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'
        ATT: name='var' value='foo'
        ATT: name='value' value='${1}'
        ENDTAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'
        TAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'
        ATT: name='var' value='foobar'
        ATT: name='value' value='${2}'
        ENDTAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'
        TAG: name='bind' namespace='http://www.springframework.org/tags'
        ATT: name='path' value='test.fieldName'
        TAG: name='scriptlet' namespace='http://java.sun.com/JSP/Page'
        ENDTAG: name='scriptlet' namespace='http://java.sun.com/JSP/Page'
        ENDTAG: name='bind' namespace='http://www.springframework.org/tags'
        ENDTAG: name='html' namespace='http://www.w3.org/1999/xhtml'
        ENDTAG: name='root' namespace='http://java.sun.com/JSP/Page'
        """,
      XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES);
  }

  public void testRealJspxNoAttributes() {
    doTest(
      /* Test: */
      """
        <jsp:root xmlns:jsp="http://java.sun.com/JSP/Page" xmlns="http://www.w3.org/1999/xhtml" version="2.0"
                  xmlns:spring="http://www.springframework.org/tags" xmlns:c="http://java.sun.com/jsp/jstl/core">
        <html>
          <c:set var="foo" value="${1}"/>
          <c:set var="foobar" value="${2}"/>
          <spring:bind path="test.fieldName">
            <jsp:scriptlet></jsp:scriptlet>
            </spring:bind>
        </html>
        </jsp:root>""",

      /* Expected result: */
      """
        TAG: name='root' namespace='http://java.sun.com/JSP/Page'
        TAG: name='html' namespace='http://www.w3.org/1999/xhtml'
        TAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'
        ENDTAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'
        TAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'
        ENDTAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'
        TAG: name='bind' namespace='http://www.springframework.org/tags'
        TAG: name='scriptlet' namespace='http://java.sun.com/JSP/Page'
        ENDTAG: name='scriptlet' namespace='http://java.sun.com/JSP/Page'
        ENDTAG: name='bind' namespace='http://www.springframework.org/tags'
        ENDTAG: name='html' namespace='http://www.w3.org/1999/xhtml'
        ENDTAG: name='root' namespace='http://java.sun.com/JSP/Page'
        """,
      XmlBuilder.ProcessingOrder.TAGS);
  }


  public void testNamespaceOverride() {
    doTest(
      """
        <c:x xmlns:c="ns1">
          <c:y/>
          <c:x xmlns:c="ns2">
            <c:y/>
          </c:x>
          <c:y/>
        </c:x>
        """,

      """
        TAG: name='x' namespace='ns1'
        ATT: name='xmlns:c' value='ns1'
        TAG: name='y' namespace='ns1'
        ENDTAG: name='y' namespace='ns1'
        TAG: name='x' namespace='ns2'
        ATT: name='xmlns:c' value='ns2'
        TAG: name='y' namespace='ns2'
        ENDTAG: name='y' namespace='ns2'
        ENDTAG: name='x' namespace='ns2'
        TAG: name='y' namespace='ns1'
        ENDTAG: name='y' namespace='ns1'
        ENDTAG: name='x' namespace='ns1'
        """,
      XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES);
  }

  public void testSimpleEntityResolution() {
    doTest(
      "<root>&lt;</root>",
      """
        TAG: name='root' namespace=''
        TEXT: '&lt;' DISPLAY: '<'
        ENDTAG: name='root' namespace=''
        """,
      XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS);
  }

  public void testCDATA() {
    doTest(
      "<root><![CDATA[<asis/>]]></root>",
      """
        TAG: name='root' namespace=''
        TEXT: '<![CDATA[<asis/>]]>' DISPLAY: '<asis/>'
        ENDTAG: name='root' namespace=''
        """,
      XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS
    );
  }

  public void testErrors() {
    doTest(
      "<root>" +
      "<foo>" +
      "<bar" +
      "<" +
      "</root>",
      """
        TAG: name='root' namespace=''
        TAG: name='foo' namespace=''
        ERROR: 'Element foo is not closed'
        ENDTAG: name='foo' namespace=''
        TAG: name='bar' namespace=''
        ERROR: 'Tag start is not closed'
        ENDTAG: name='bar' namespace=''
        TAG: name='' namespace=''
        ERROR: 'Tag name expected'
        ENDTAG: name='' namespace=''
        ENDTAG: name='root' namespace=''
        """,
      XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS
    );
  }

  public void testComments() {
    doTest(
      """
        <root><foo><!--aa--></foo><foo><!----></foo><foo>aaa<!--aa-->aaa</foo><foo>
        aaa
        <!--aa-->
        aaa
        </foo></root>""",
      """
        TAG: name='root' namespace=''
        TAG: name='foo' namespace=''
        ENDTAG: name='foo' namespace=''
        TAG: name='foo' namespace=''
        ENDTAG: name='foo' namespace=''
        TAG: name='foo' namespace=''
        TEXT: 'aaaaaa' DISPLAY: 'aaaaaa'
        ENDTAG: name='foo' namespace=''
        TAG: name='foo' namespace=''
        TEXT: '
        aaa

        aaa
        ' DISPLAY: '
        aaa

        aaa
        '
        ENDTAG: name='foo' namespace=''
        ENDTAG: name='root' namespace=''
        """,
      XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS
    );
  }

  private static void doTest(String xml, String expectedEventSequence, @NotNull XmlBuilder.ProcessingOrder tagsAndAttributes) {
    final TestXmlBuilder builder = new TestXmlBuilder(tagsAndAttributes);
    new XmlBuilderDriver(xml).build(builder);
    assertEquals(expectedEventSequence, builder.getResult());
  }
}