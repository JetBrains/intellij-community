/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.analysis.XmlDefaultAttributeValueInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlDeprecatedElementInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xml.analysis.XmlAnalysisBundle;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */
public class XmlInspectionsTest extends BasePlatformTestCase {

  public void testDefaultAttributeValue() {
    myFixture.enableInspections(new XmlDefaultAttributeValueInspection());
    myFixture.configureByText(XmlFileType.INSTANCE, "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=<warning descr=\"Redundant default attribute value assignment\">\"unqua<caret>lified\"</warning>>\n" +
                                                    "</schema>");
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention(XmlAnalysisBundle.message("remove.attribute.quickfix.family") + " e");
    myFixture.launchAction(action);
    myFixture.checkResult("<schema xmlns=\"http://www.w3.org/2001/XMLSchema\">\n" +
                          "</schema>");
  }

  public void testHtmlFromRncSchema() {
    myFixture.enableInspections(new XmlDefaultAttributeValueInspection());
    myFixture.configureByText(HtmlFileType.INSTANCE, "<!DOCTYPE html>\n" +
                                                     "<html lang=\"en\">\n" +
                                                     "<head>\n" +
                                                     "    <meta charset=\"UTF-8\">\n" +
                                                     "    <title>Title</title>\n" +
                                                     "</head>\n" +
                                                     "<body>\n" +
                                                     "<form action=\"index.php\">\n" +
                                                     "    <input type=\"hidden\" name=\"name_1\" value=\"val_1\">\n" +
                                                     "    <input type=\"hidden\" name=\"name_2\" value=\"val_2\">\n" +
                                                     "    <button type=\"button\">Proper js button</button>\n" +
                                                     "    <button type=\"submit\">Proper submit button</button>\n" +
                                                     "    <button>Behave as submit when missing type=\"button\"</button>\n" +
                                                     "</form>\n" +
                                                     "</body>\n" +
                                                     "</html>\n");
    myFixture.checkHighlighting();
  }

  public void testDefaultAttributeInHtml() {
    myFixture.enableInspections(new XmlDefaultAttributeValueInspection());
    myFixture.configureByText(HtmlFileType.INSTANCE, "<input type=\"text\"/>");
    myFixture.checkHighlighting();
  }

  public void testRequiredFixedAttribute() {
    myFixture.enableInspections(new XmlDefaultAttributeValueInspection());
    myFixture.testHighlighting("def.xml", "def.xsd");
  }

  public void testEmptyDefaultAttributeValue() {
    myFixture.enableInspections(new XmlDefaultAttributeValueInspection());
    myFixture.addFileToProject("foo.xsd", "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"def/attr\">\n" +
                                          "  <element name=\"foo\">\n" +
                                          "    <complexType>\n" +
                                          "      <attribute name=\"bar\" default=\"\"/>\n" +
                                          "    </complexType>\n" +
                                          "  </element>\n" +
                                          "</schema>");
    myFixture.configureByText("foo.xml", "<foo xmlns=\"def/attr\" bar=/>");
    myFixture.doHighlighting();
  }

  public void testDeprecations() {
    myFixture.enableInspections(new XmlDeprecatedElementInspection());
    myFixture.testHighlighting("deprecated.xml", "deprecated.xsd");
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/xml";
  }
}
