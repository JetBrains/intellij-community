/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xml;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.htmlInspections.XmlInspectionToolProvider;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlNamespacesTest extends LightCodeInsightFixtureTestCase {
  public void testUnusedNamespaces() {
    doUnusedDeclarationTest(
      "<all xmlns=\"http://www.w3.org/2001/XMLSchema\" <warning descr=\"Namespace declaration is never used\">xmlns:xsi=\"http://www.w3.org/2001/XMLSc<caret>hema-instance\"</warning>/>",
      "<all xmlns=\"http://www.w3.org/2001/XMLSchema\"/>", XmlBundle.message("xml.inspections.unused.schema.remove"));
  }

  public void testUnusedDefaultNamespace() {
    doUnusedDeclarationTest("<schema:schema \n" +
                            "            xmlns:schema=\"http://www.w3.org/2001/XMLSchema\"\n" +
                            "            <warning descr=\"Namespace declaration is never used\">xmlns=\"http://www.w3.org/2001/X<caret>Include\"</warning>\n" +
                            "            xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "            <warning descr=\"Namespace location is never used\">xsi:noNamespaceSchemaLocation=\"http://www.w3.org/2001/XInclude\"</warning>>\n" +
                            "</schema:schema>",

                            "<schema:schema\n" +
                            "        xmlns:schema=\"http://www.w3.org/2001/XMLSchema\"\n" +
                            "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            ">\n" +
                            "</schema:schema>", XmlBundle.message("xml.inspections.unused.schema.remove"), false);

    doOptimizeImportsTest("<schema:schema \n" +
                          "            xmlns:schema=\"http://www.w3.org/2001/XMLSchema\"\n" +
                          "            xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                          ">\n" +
                          "</schema:schema>");
  }

  public void testDifferentPrefixes() {
    doUnusedDeclarationTest(
      "<x:all  <warning descr=\"Namespace declaration is never used\">xmlns=\"http://www.w3.org/2001/XMLS<caret>chema\"</warning>\n" +
      "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
      "        <warning descr=\"Namespace declaration is never used\">xmlns:y=\"http://www.w3.org/2001/XMLSchema\"</warning>/>",

      "<x:all\n" +
      "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
      "        xmlns:y=\"http://www.w3.org/2001/XMLSchema\"/>",
      XmlBundle.message("xml.inspections.unused.schema.remove"), false);

    doOptimizeImportsTest("<x:all\n" +
                          "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                          "/>");
  }

  public void testUnusedLocation() {
    doUnusedDeclarationTest("<x:all\n" +
                            "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                            "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "        xsi:schemaLocation=\"<warning descr=\"Namespace location is never used\">http://www.w3.org/2001/XML<caret>Sche</warning> " +
                            "<warning descr=\"Namespace location is never used\">http://www.w3.org/2001/XMLSchema.xsd</warning>\"/>",
                            "<x:all\n" +
                            "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                            "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "/>",
                            XmlUnusedNamespaceInspection.RemoveNamespaceLocationFix.NAME);
  }

  public void testUnusedLocationOnly() {
    doUnusedDeclarationTest("<x:all\n" +
                            "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                            "        <warning descr=\"Namespace declaration is never used\">xmlns:y=\"http://www.w3.org/2001/XInclude\"</warning>\n" +
                            "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "        xsi:schemaLocation=\"<warning descr=\"Namespace location is never used\">http://www.w3.org/2001/XI<caret>nclude</warning> <warning descr=\"Namespace location is never used\">http://www.w3.org/2001/XInclude.xsd</warning>\n" +
                            "        http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd\"/>",
                            "<x:all\n" +
                            "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                            "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "        xsi:schemaLocation=\"http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd\"/>",
                            XmlBundle.message("xml.inspections.unused.schema.remove"));
  }

  public void testUnusedDefaultLocation() {
    doUnusedDeclarationTest("<x:all\n" +
                            "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                            "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "        <warning descr=\"Namespace location is never used\">xsi:noNamespaceSc<caret>hemaLocation=\"<error descr=\"Cannot resolve file 'zzz'\">zzz</error>\"</warning> />",
                            "<x:all\n" +
                            "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                            "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            "/>",
                            XmlUnusedNamespaceInspection.RemoveNamespaceLocationFix.NAME);
  }

  public void testKeepFormatting() {
    doUnusedDeclarationTest("<xs:schema attributeFormDefault=\"unqualified\"\n" +
                            "           <warning descr=\"Namespace declaration is never used\">xmlns:xsi=\"http://www.w3.org/20<caret>01/XMLSchema-instance\"</warning>\n" +
                            "           elementFormDefault=\"qualified\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                            "\n" +
                            "        <!-- keep formatting here-->\n" +
                            "    <xs:element name=\"a\" type=\"aType\"/>\n" +
                            "  <xs:complexType name=\"aType\">\n" +
                            "\n" +
                            "  </xs:complexType>\n" +
                            "</xs:schema>",
                            "<xs:schema attributeFormDefault=\"unqualified\"\n" +
                            "           elementFormDefault=\"qualified\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                            "\n" +
                            "        <!-- keep formatting here-->\n" +
                            "    <xs:element name=\"a\" type=\"aType\"/>\n" +
                            "  <xs:complexType name=\"aType\">\n" +
                            "\n" +
                            "  </xs:complexType>\n" +
                            "</xs:schema>",
                            XmlBundle.message("xml.inspections.unused.schema.remove"));
  }

  public void testImplicitPrefixUsage() {
    myFixture.configureByText("a.xml", "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                                       "           xmlns:x2=\"http://www.w3.org/2001/XMLSchema\"\n" +
                                       "           <warning descr=\"Namespace declaration is never used\">xmlns:x3=\"http://www.w3.org/2001/XMLSchema\"</warning> >\n" +
                                       "  <xs:element name=\"a\" type=\"x2:string\"/>\n" +
                                       "</xs:schema>");
    myFixture.testHighlighting();
  }

  public void testUnusedLocationDetection() {
    myFixture.configureByFile("web-app_2_5.xsd");
    myFixture.configureByText("a.xml", "<web-app xmlns=\"http://java.sun.com/xml/ns/javaee\"\n" +
                                       "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                       "         <warning descr=\"Namespace declaration is never used\">xmlns:web=\"http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\"</warning>\n" +
                                       "         xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee\n" +
                                       "         web-app_2_5.xsd\"\n" +
                                       "         version=\"2.5\">\n" +
                                       "</web-app>");
    myFixture.testHighlighting();
  }

  public void testWSDD() {
    myFixture.configureByText("a.xml",
                              "<deployment xmlns=\"http://xml.apache.org/axis/wsdd/\" xmlns:java=\"http://xml.apache.org/axis/wsdd/providers/java\">\n" +
                              "<typeMapping deserializer=\"org.apache.axis.encoding.ser.BeanDeserializerFactory\" encodingStyle=\"\" qname=\"ns38:AxisAnalysis\" serializer=\"org.apache.axis.encoding.ser.BeanSerializerFactory\" languageSpecificType=\"java:com.pls.xactservice.axis.bindings.AxisAnalysis\"/>\n" +
                              "</deployment>");
    myFixture.testHighlighting();
  }

  public void testPrefixesInTagValues() {
    myFixture.configureByText("a.xml",
                              "<<info descr=\"Namespace '' is not bound\">nodeTypes</info> xmlns:nt=\"<error descr=\"URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)\">http://www.jcp.org/jcr/nt/1.0</error>\" xmlns:customns=\"<error descr=\"URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)\">http://customurl</error>\">\n" +
                              "<nodeType name=\"customns:item\" isMixin=\"false\" hasOrderableChildNodes=\"false\">\n" +
                              "   <supertypes>\n" +
                              "      <supertype>nt:folder</supertype>\n" +
                              "   </supertypes>\n" +
                              "</nodeType>\n" +
                              "</<info descr=\"Namespace '' is not bound\">nodeTypes</info>>");
    myFixture.testHighlighting();
  }

  public void testLocallyUsedNamespace() {
    myFixture.configureByText("a.xml", "<x:all\n" +
                                       "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                                       "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                       "        xsi:schemaLocation=\"http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd\n" +
                                       "                http://www.w3.org/2001/XInclude http://www.w3.org/2001/XInclude.xsd\">\n" +
                                       "\n" +
                                       "    <include xmlns=\"http://www.w3.org/2001/XInclude\" href=\"a.xml\"/>\n" +
                                       "</x:all>");
    myFixture.testHighlighting();
  }

  public void testLocallyUsedNamespaceWithPrefix() {
    myFixture.configureByText("a.xml", "<s:foo xmlns:s=\"<error descr=\"URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)\">http://foo</error>\"\n" +
                                       "       <warning descr=\"Namespace declaration is never used\">xmlns:bar=\"<error descr=\"URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)\">http://bar</error>\"</warning>\n" +
                                       "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                       "       xsi:schemaLocation=\"http://bar <error descr=\"Cannot resolve file 'bar.xsd'\">bar.xsd</error> http://foo <error descr=\"Cannot resolve file 'foo.xsd'\">foo.xsd</error>\">\n" +
                                       "\n" +
                                       "    <bar xmlns=\"<error descr=\"URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)\">http://bar</error>\"/>\n" +
                                       "\n" +
                                       "</s:foo>");
    myFixture.testHighlighting();
  }

  public void testSubDirectory() {
    myFixture.testHighlighting("moved.xml", "trg/move-def.xsd");
  }

  public void testSuppressedOptimize() {
    myFixture.configureByFile("web-app_2_5.xsd");
    String text = "<!--suppress XmlUnusedNamespaceDeclaration -->\n" +
                  "<web-app xmlns=\"http://java.sun.com/xml/ns/javaee\"\n" +
                  "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                  "         xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee\n" +
                  "         web-app_2_5.xsd\"\n" +
                  "         version=\"2.5\">\n" +
                  "</web-app>";
    myFixture.configureByText("a.xml", text);

    doOptimizeImportsTest(text);
  }

  public void testUsedInXmlns() {
    myFixture.testHighlighting("spring.xml", "spring-beans-2.5.xsd", "spring-batch-2.1.xsd");
    IntentionAction action = myFixture.getAvailableIntention(XmlBundle.message("xml.inspections.unused.schema.remove"));
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("spring_after.xml");
  }

  public void testXsiType() {
    myFixture.testHighlighting("import.xml", "import.xsd");
  }

  public void testDoNotOptimizeWhenInspectionDisabled() {
    myFixture.disableInspections(new XmlUnusedNamespaceInspection());
    String text = "<all xmlns=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>";
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    doOptimizeImportsTest(text);
  }

  public void testFixAll() {
    myFixture.configureByFiles("fixAll.xml", "spring-beans-2.5.xsd", "spring-batch-2.1.xsd");
    IntentionAction action = myFixture.findSingleIntention("Fix all");
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("fixAll_after.xml");
  }

  public void testImplicitPrefixes() {
    myFixture.configureByText(XmlFileType.INSTANCE, "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\" \n" +
                                                    "        xmlns:x=\"http://www.w3.org/2001/XMLSchema\"\n" +
                                                    "        <warning descr=\"Namespace declaration is never used\">xmlns:y=\"http://www.w3.org/2001/XMLSchema\"</warning>>\n" +
                                                    "    <element name=\"a\" default=\"x:y\"/>\n" +
                                                    "</schema>");
    myFixture.testHighlighting();
  }

  private void doUnusedDeclarationTest(String text, String after, String name) {
    doUnusedDeclarationTest(text, after, name, true);
  }

  private void doUnusedDeclarationTest(String text, String after, String name, boolean testOptimizeImports) {
    myFixture.configureByText("a.xml", text);
    myFixture.testHighlighting();
    IntentionAction action = myFixture.getAvailableIntention(name);
    assertNotNull(name + " not found", action);
    myFixture.launchAction(action);
    myFixture.checkResult(after);

    myFixture.configureByText("a.xml", text);
    if (testOptimizeImports) {
      doOptimizeImportsTest(after);
    }
  }

  private void doOptimizeImportsTest(String after) {
    myFixture.testHighlighting();
    new WriteCommandAction(getProject(), getFile()) {
      @Override
      protected void run(@NotNull Result result) {
        new OptimizeImportsProcessor(getProject(), getFile()).runWithoutProgress();
      }
    }.execute();
    myFixture.checkResult(after);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new XmlInspectionToolProvider());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd",
                                                              getTestDataPath() + "/web-app_2_5.xsd", myFixture.getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://xml.apache.org/axis/wsdd/",
                                                              getTestDataPath() + "/wsdd.dtd", myFixture.getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://xml.apache.org/axis/wsdd/providers/java",
                                                              getTestDataPath() + "/wsdd_provider_java.xsd", myFixture.getTestRootDisposable());
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/unusedNs";
  }
}
