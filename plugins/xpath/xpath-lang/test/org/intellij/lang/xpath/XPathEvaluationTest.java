// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.util.Namespace;
import org.jaxen.JaxenException;
import org.jaxen.XPath;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("unchecked")
public class XPathEvaluationTest extends LightPlatformCodeInsightTestCase {

  public void testEvaluate() throws JaxenException {
    configureFromFileText("foo.xml", "<a xmlns='foo'><b<caret>></b></a>");

    XmlFile file = (XmlFile)getFile();
    String path = XPathSupport.getInstance().getUniquePath(file.getRootTag().getSubTags()[0], null);
    assertEquals("/a/b", path);

    XPath xPath = XPathSupport.getInstance().createXPath(file, path, Collections.singletonList(new Namespace("", "foo")));
    List<XmlTag> list = (List<XmlTag>)xPath.evaluate(file.getDocument());
    assertSize(1, list);
    assertEquals("b", list.get(0).getName());
  }
}
