// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.util.Namespace;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("unchecked")
@TestApplication
public class XPathEvaluationTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);

  @Test
  public void testEvaluate() throws JaxenException {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xml", "<a xmlns='foo'><b<caret>></b></a>");

    XmlFile file = (XmlFile)myFixture.getFile();
    String path = ReadAction.computeBlocking(() -> XPathSupport.getInstance().getUniquePath(file.getRootTag().getSubTags()[0], null));
    assertEquals("/a/b", path);

    List<XmlTag> list = ReadAction.computeBlocking(() -> {
      XPath xPath = XPathSupport.getInstance().createXPath(file, path, Collections.singletonList(new Namespace("", "foo")));
      return (List<XmlTag>)xPath.evaluate(file.getDocument());
    });
    assertEquals(1, list.size(), list::toString);
    assertEquals("b", ReadAction.computeBlocking(() -> list.get(0).getName()));
  }
}
