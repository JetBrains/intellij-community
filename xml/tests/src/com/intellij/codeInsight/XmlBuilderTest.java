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
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.Nullable;

public class XmlBuilderTest extends LightCodeInsightTestCase {
  private static class TestXmlBuilder implements XmlBuilder {
    private final StringBuilder builder = new StringBuilder();
    private final StringBuilder currentPhysicalText = new StringBuilder();
    private final StringBuilder currentDisplayText = new StringBuilder();
    private final ProcessingOrder myTagProcessingOrder;

    public TestXmlBuilder(final ProcessingOrder tagsAndAttributes) {
      myTagProcessingOrder = tagsAndAttributes;
    }

    @Override
    public void attribute(final CharSequence name, final CharSequence value, final int startoffset, final int endoffset) {
      flushText();
      builder.append("ATT: name='").append(name).append("' value='").append(value).append("'\n");
    }

    @Override
    public void endTag(final CharSequence localName, final String namespace, final int startoffset, final int endoffset) {
      flushText();
      builder.append("ENDTAG: name='").append(localName).append("' namespace='").append(namespace).append("'\n");
    }

    @Override
    public void doctype(@Nullable final CharSequence publicId, @Nullable final CharSequence systemId, final int startOffset, final int endOffset) {
        }

    @Override
    public ProcessingOrder startTag(final CharSequence localName, final String namespace, final int startoffset, final int endoffset,
                                    final int headerEndOffset) {
      flushText();
      builder.append("TAG: name='").append(localName).append("' namespace='").append(namespace).append("'\n");
      return myTagProcessingOrder;
    }

    @Override
    public void textElement(final CharSequence display, final CharSequence physical, final int startoffset, final int endoffset) {
      currentPhysicalText.append(physical);
      currentDisplayText.append(display);
    }

    @Override
    public void entityRef(final CharSequence ref, final int startOffset, final int endOffset) {
      flushText();
      builder.append("REF: '").append(ref).append("'\n");
    }

    @Override
    public void error(String message, int startOffset, int endOffset) {
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
    doTest("<root/>", "TAG: name='root' namespace=''\n" +
                      "ENDTAG: name='root' namespace=''\n", XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES);
  }

  public void testRealJspx() {
    doTest(
      /* Test: */
      "<jsp:root xmlns:jsp=\"http://java.sun.com/JSP/Page\" xmlns=\"http://www.w3.org/1999/xhtml\" version=\"2.0\"\n" +
      "          xmlns:spring=\"http://www.springframework.org/tags\" xmlns:c=\"http://java.sun.com/jsp/jstl/core\">\n" +
      "<html>\n" +
      "  <c:set var=\"foo\" value=\"${1}\"/>\n" +
      "  <c:set var=\"foobar\" value=\"${2}\"/>\n" +
      "  <spring:bind path=\"test.fieldName\">\n" +
      "    <jsp:scriptlet></jsp:scriptlet>\n" +
      "    </spring:bind>\n" +
      "</html>\n" +
      "</jsp:root>",

      /* Expected result: */
      "TAG: name='root' namespace='http://java.sun.com/JSP/Page'\n" +
      "ATT: name='xmlns:jsp' value='http://java.sun.com/JSP/Page'\n" +
      "ATT: name='xmlns' value='http://www.w3.org/1999/xhtml'\n" +
      "ATT: name='version' value='2.0'\n" +
      "ATT: name='xmlns:spring' value='http://www.springframework.org/tags'\n" +
      "ATT: name='xmlns:c' value='http://java.sun.com/jsp/jstl/core'\n" +
      "TAG: name='html' namespace='http://www.w3.org/1999/xhtml'\n" +
      "TAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'\n" +
      "ATT: name='var' value='foo'\n" +
      "ATT: name='value' value='${1}'\n" +
      "ENDTAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'\n" +
      "TAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'\n" +
      "ATT: name='var' value='foobar'\n" +
      "ATT: name='value' value='${2}'\n" +
      "ENDTAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'\n" +
      "TAG: name='bind' namespace='http://www.springframework.org/tags'\n" +
      "ATT: name='path' value='test.fieldName'\n" +
      "TAG: name='scriptlet' namespace='http://java.sun.com/JSP/Page'\n" +
      "ENDTAG: name='scriptlet' namespace='http://java.sun.com/JSP/Page'\n" +
      "ENDTAG: name='bind' namespace='http://www.springframework.org/tags'\n" +
      "ENDTAG: name='html' namespace='http://www.w3.org/1999/xhtml'\n" +
      "ENDTAG: name='root' namespace='http://java.sun.com/JSP/Page'\n",
      XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES);
  }

  public void testRealJspxNoAttributes() {
    doTest(
      /* Test: */
      "<jsp:root xmlns:jsp=\"http://java.sun.com/JSP/Page\" xmlns=\"http://www.w3.org/1999/xhtml\" version=\"2.0\"\n" +
      "          xmlns:spring=\"http://www.springframework.org/tags\" xmlns:c=\"http://java.sun.com/jsp/jstl/core\">\n" +
      "<html>\n" +
      "  <c:set var=\"foo\" value=\"${1}\"/>\n" +
      "  <c:set var=\"foobar\" value=\"${2}\"/>\n" +
      "  <spring:bind path=\"test.fieldName\">\n" +
      "    <jsp:scriptlet></jsp:scriptlet>\n" +
      "    </spring:bind>\n" +
      "</html>\n" +
      "</jsp:root>",

      /* Expected result: */
      "TAG: name='root' namespace='http://java.sun.com/JSP/Page'\n" +
      "TAG: name='html' namespace='http://www.w3.org/1999/xhtml'\n" +
      "TAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'\n" +
      "ENDTAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'\n" +
      "TAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'\n" +
      "ENDTAG: name='set' namespace='http://java.sun.com/jsp/jstl/core'\n" +
      "TAG: name='bind' namespace='http://www.springframework.org/tags'\n" +
      "TAG: name='scriptlet' namespace='http://java.sun.com/JSP/Page'\n" +
      "ENDTAG: name='scriptlet' namespace='http://java.sun.com/JSP/Page'\n" +
      "ENDTAG: name='bind' namespace='http://www.springframework.org/tags'\n" +
      "ENDTAG: name='html' namespace='http://www.w3.org/1999/xhtml'\n" +
      "ENDTAG: name='root' namespace='http://java.sun.com/JSP/Page'\n",
      XmlBuilder.ProcessingOrder.TAGS);
  }


  public void testNamespaceOverride() {
    doTest(
      "<c:x xmlns:c=\"ns1\">\n" +
      "  <c:y/>\n" +
      "  <c:x xmlns:c=\"ns2\">\n" +
      "    <c:y/>\n" +
      "  </c:x>\n" +
      "  <c:y/>\n" +
      "</c:x>\n",

      "TAG: name='x' namespace='ns1'\n" +
      "ATT: name='xmlns:c' value='ns1'\n" +
      "TAG: name='y' namespace='ns1'\n" +
      "ENDTAG: name='y' namespace='ns1'\n" +
      "TAG: name='x' namespace='ns2'\n" +
      "ATT: name='xmlns:c' value='ns2'\n" +
      "TAG: name='y' namespace='ns2'\n" +
      "ENDTAG: name='y' namespace='ns2'\n" +
      "ENDTAG: name='x' namespace='ns2'\n" +
      "TAG: name='y' namespace='ns1'\n" +
      "ENDTAG: name='y' namespace='ns1'\n" +
      "ENDTAG: name='x' namespace='ns1'\n",
      XmlBuilder.ProcessingOrder.TAGS_AND_ATTRIBUTES);
  }

  public void testSimpleEntityResolution() {
    doTest(
      "<root>&lt;</root>",
      "TAG: name='root' namespace=''\n" +
      "TEXT: '&lt;' DISPLAY: '<'\n" +
      "ENDTAG: name='root' namespace=''\n",
      XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS);
  }

  public void testCDATA() {
    doTest(
      "<root><![CDATA[<asis/>]]></root>",
      "TAG: name='root' namespace=''\n" +
      "TEXT: '<![CDATA[<asis/>]]>' DISPLAY: '<asis/>'\n" +
      "ENDTAG: name='root' namespace=''\n",
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
      "TAG: name='root' namespace=''\n" +
      "TAG: name='foo' namespace=''\n" +
      "ERROR: 'Element foo is not closed'\n" +
      "ENDTAG: name='foo' namespace=''\n" +
      "TAG: name='bar' namespace=''\n" +
      "ERROR: 'Tag start is not closed'\n" +
      "ENDTAG: name='bar' namespace=''\n" +
      "TAG: name='' namespace=''\n" +
      "ERROR: 'Tag name expected'\n" +
      "ENDTAG: name='' namespace=''\n" +
      "ENDTAG: name='root' namespace=''\n",
      XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS
    );
  }

  public void testComments() {
    doTest(
      "<root>" +
      "<foo>" +
      "<!--aa-->" +
      "</foo>" +
      "<foo>" +
      "<!---->" +
      "</foo>" +
      "<foo>" +
      "aaa<!--aa-->aaa" +
      "</foo>" +
      "<foo>" +
      "\naaa\n<!--aa-->\naaa\n" +
      "</foo>" +
      "</root>",
      "TAG: name='root' namespace=''\n" +
      "TAG: name='foo' namespace=''\n" +
      "ENDTAG: name='foo' namespace=''\n" +
      "TAG: name='foo' namespace=''\n" +
      "ENDTAG: name='foo' namespace=''\n" +
      "TAG: name='foo' namespace=''\n" +
      "TEXT: 'aaaaaa' DISPLAY: 'aaaaaa'\n" +
      "ENDTAG: name='foo' namespace=''\n" +
      "TAG: name='foo' namespace=''\n" +
      "TEXT: '\naaa\n\naaa\n' DISPLAY: '\naaa\n\naaa\n'\n" +
      "ENDTAG: name='foo' namespace=''\n" +
      "ENDTAG: name='root' namespace=''\n",
      XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS
    );
  }

  private static void doTest(String xml, String expectedEventSequence, final XmlBuilder.ProcessingOrder tagsAndAttributes) {
    final TestXmlBuilder builder = new TestXmlBuilder(tagsAndAttributes);
    new XmlBuilderDriver(xml).build(builder);
    assertEquals(expectedEventSequence, builder.getResult());
  }
}