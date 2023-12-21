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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.xml.analysis.XmlAnalysisBundle;

/**
 * @author Dmitry Avdeev
 */
public class XmlNamespacesTest extends LightJavaCodeInsightFixtureTestCase {
  public void testUnusedNamespaces() {
    doUnusedDeclarationTest(
      "<all xmlns=\"http://www.w3.org/2001/XMLSchema\" <warning descr=\"Namespace declaration is never used\">xmlns:xsi=\"http://www.w3.org/2001/XMLSc<caret>hema-instance\"</warning>/>",
      "<all xmlns=\"http://www.w3.org/2001/XMLSchema\"/>", XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl"));
  }

  public void testUnusedDefaultNamespace() {
    doUnusedDeclarationTest("""
                              <schema:schema
                                      xmlns:schema="http://www.w3.org/2001/XMLSchema"
                                      <warning descr="Namespace declaration is never used">xmlns="http://www.w3.org/2001/X<caret>Include"</warning>
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                      <warning descr="Namespace location is never used">xsi:noNamespaceSchemaLocation="http://www.w3.org/2001/XInclude"</warning>>
                              </schema:schema>""",

                            """
                              <schema:schema
                                      xmlns:schema="http://www.w3.org/2001/XMLSchema"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              >
                              </schema:schema>""", XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl"), false);
    doOptimizeImportsTest("""
                              <schema:schema
                                      xmlns:schema="http://www.w3.org/2001/XMLSchema"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              >
                              </schema:schema>""");
  }

  public void testDifferentPrefixes() {
    doUnusedDeclarationTest(
      """
        <x:all  <warning descr="Namespace declaration is never used">xmlns="http://www.w3.org/2001/XMLS<caret>chema"</warning>
                xmlns:x="http://www.w3.org/2001/XMLSchema"
                <warning descr="Namespace declaration is never used">xmlns:y="http://www.w3.org/2001/XMLSchema"</warning>/>""",

      """
        <x:all
                xmlns:x="http://www.w3.org/2001/XMLSchema"
                xmlns:y="http://www.w3.org/2001/XMLSchema"/>""",
      XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl"), false);

    doOptimizeImportsTest("""
                            <x:all
                                    xmlns:x="http://www.w3.org/2001/XMLSchema"
                            />""");
  }

  public void testUnusedLocation() {
    doUnusedDeclarationTest("""
                              <x:all
                                      xmlns:x="http://www.w3.org/2001/XMLSchema"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                      xsi:schemaLocation="<warning descr="Namespace location is never used">http://www.w3.org/2001/XML<caret>Sche</warning> <warning descr="Namespace location is never used">http://www.w3.org/2001/XMLSchema.xsd</warning>"/>""",
                            """
                              <x:all
                                      xmlns:x="http://www.w3.org/2001/XMLSchema"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              />""",
                            XmlAnalysisBundle.message("xml.intention.remove.unused.namespace.location"));
  }

  public void testUnusedLocationOnly() {
    doUnusedDeclarationTest("""
                              <x:all
                                      xmlns:x="http://www.w3.org/2001/XMLSchema"
                                      <warning descr="Namespace declaration is never used">xmlns:y="http://www.w3.org/2001/XInclude"</warning>
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                      xsi:schemaLocation="<warning descr="Namespace location is never used">http://www.w3.org/2001/XI<caret>nclude</warning> <warning descr="Namespace location is never used">http://www.w3.org/2001/XInclude.xsd</warning>
                                      http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd"/>""",
                            """
                              <x:all
                                      xmlns:x="http://www.w3.org/2001/XMLSchema"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                      xsi:schemaLocation="http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd"/>""",
                            XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl"));
  }

  public void testUnusedDefaultLocation() {
    doUnusedDeclarationTest("""
                              <x:all
                                      xmlns:x="http://www.w3.org/2001/XMLSchema"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                      <warning descr="Namespace location is never used">xsi:noNamespaceSc<caret>hemaLocation="<error descr="Cannot resolve file 'zzz'">zzz</error>"</warning> />""",
                            """
                              <x:all
                                      xmlns:x="http://www.w3.org/2001/XMLSchema"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              />""",
                            XmlAnalysisBundle.message("xml.intention.remove.unused.namespace.location"));
  }

  public void testKeepFormatting() {
    doUnusedDeclarationTest("""
                              <xs:schema attributeFormDefault="unqualified"
                                         <warning descr="Namespace declaration is never used">xmlns:xsi="http://www.w3.org/20<caret>01/XMLSchema-instance"</warning>
                                         elementFormDefault="qualified" xmlns:xs="http://www.w3.org/2001/XMLSchema">

                                      <!-- keep formatting here-->
                                  <xs:element name="a" type="aType"/>
                                <xs:complexType name="aType">

                                </xs:complexType>
                              </xs:schema>""",
                            """
                              <xs:schema attributeFormDefault="unqualified"
                                         elementFormDefault="qualified" xmlns:xs="http://www.w3.org/2001/XMLSchema">

                                      <!-- keep formatting here-->
                                  <xs:element name="a" type="aType"/>
                                <xs:complexType name="aType">

                                </xs:complexType>
                              </xs:schema>""",
                            XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl"));
  }

  public void testImplicitPrefixUsage() {
    myFixture.configureByText("a.xml", """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                 xmlns:x2="http://www.w3.org/2001/XMLSchema"
                 <warning descr="Namespace declaration is never used">xmlns:x3="http://www.w3.org/2001/XMLSchema"</warning> >
        <xs:element name="a" type="x2:string"/>
      </xs:schema>""");
    myFixture.testHighlighting();
  }

  public void testUnusedLocationDetection() {
    myFixture.configureByFile("web-app_2_5.xsd");
    myFixture.configureByText("a.xml", """
      <web-app xmlns="http://java.sun.com/xml/ns/javaee"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               <warning descr="Namespace declaration is never used">xmlns:web="http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"</warning>
               xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
               web-app_2_5.xsd"
               version="2.5">
      </web-app>""");
    myFixture.testHighlighting();
  }

  public void testWSDD() {
    myFixture.configureByText("a.xml",
                              """
                                <deployment xmlns="http://xml.apache.org/axis/wsdd/" xmlns:java="http://xml.apache.org/axis/wsdd/providers/java">
                                <typeMapping deserializer="org.apache.axis.encoding.ser.BeanDeserializerFactory" encodingStyle="" qname="ns38:AxisAnalysis" serializer="org.apache.axis.encoding.ser.BeanSerializerFactory" languageSpecificType="java:com.pls.xactservice.axis.bindings.AxisAnalysis"/>
                                </deployment>""");
    myFixture.testHighlighting();
  }

  public void testPrefixesInTagValues() {
    myFixture.configureByText("a.xml",
                              """
                                <<info descr="Namespace '' is not bound">nodeTypes</info> xmlns:nt="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://www.jcp.org/jcr/nt/1.0</error>" xmlns:customns="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://customurl</error>">
                                <nodeType name="customns:item" isMixin="false" hasOrderableChildNodes="false">
                                   <supertypes>
                                      <supertype>nt:folder</supertype>
                                   </supertypes>
                                </nodeType>
                                </<info descr="Namespace '' is not bound">nodeTypes</info>>""");
    myFixture.testHighlighting();
  }

  public void testLocallyUsedNamespace() {
    myFixture.configureByText("a.xml", """
      <x:all
              xmlns:x="http://www.w3.org/2001/XMLSchema"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd
                      http://www.w3.org/2001/XInclude http://www.w3.org/2001/XInclude.xsd">

          <include xmlns="http://www.w3.org/2001/XInclude" href="a.xml"/>
      </x:all>""");
    myFixture.testHighlighting();
  }

  public void testLocallyUsedNamespaceWithPrefix() {
    myFixture.configureByText("a.xml", """
      <s:foo xmlns:s="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://foo</error>"
             <warning descr="Namespace declaration is never used">xmlns:bar="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://bar</error>"</warning>
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://bar <error descr="Cannot resolve file 'bar.xsd'">bar.xsd</error> http://foo <error descr="Cannot resolve file 'foo.xsd'">foo.xsd</error>">

          <bar xmlns="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://bar</error>"/>

      </s:foo>""");
    myFixture.testHighlighting();
  }

  public void testSubDirectory() {
    myFixture.testHighlighting("moved.xml", "trg/move-def.xsd");
  }

  public void testSuppressedOptimize() {
    myFixture.configureByFile("web-app_2_5.xsd");
    String text = """
      <!--suppress XmlUnusedNamespaceDeclaration -->
      <web-app xmlns="http://java.sun.com/xml/ns/javaee"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
               web-app_2_5.xsd"
               version="2.5">
      </web-app>""";
    myFixture.configureByText("a.xml", text);

    doOptimizeImportsTest(text);
  }

  public void testUsedInXmlns() {
    myFixture.testHighlighting("spring.xml", "spring-beans-2.5.xsd", "spring-batch-2.1.xsd");
    IntentionAction action = myFixture.getAvailableIntention(XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl"));
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
    myFixture.configureByText(XmlFileType.INSTANCE, """
      <schema xmlns="http://www.w3.org/2001/XMLSchema"\s
              xmlns:x="http://www.w3.org/2001/XMLSchema"
              <warning descr="Namespace declaration is never used">xmlns:y="http://www.w3.org/2001/XMLSchema"</warning>>
          <element name="a" default="x:y"/>
      </schema>""");
    myFixture.testHighlighting();
  }

  public void testImplicitPrefixesPattern() {
    myFixture.configureByText(XmlFileType.INSTANCE, """
      <html xmlns="http://www.w3.org/1999/xhtml"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <body about="wsdl:definitions/wsdl:types/xs:schema[@targetNamespace='http://www.w3schools.com/webservices/']">
          </body>
      </html>""");
    myFixture.testHighlighting();
  }

  public void testHtml5Namespace() {
    myFixture.configureByText("test.xslt",
                              """
                                <?xml version="1.0" encoding="utf-8"?>
                                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml">
                                  <xsl:template match="foo">
                                    <div data-foo="bar"
                                         <error>dta-foo</error>="bar">
                                    </div>
                                  </xsl:template>
                                </xsl:stylesheet>
                                """);
    myFixture.testHighlighting();
  }

  public void testPatternPerformanceProblem() {
    myFixture.configureByFile("idproblem.html");
    PlatformTestUtil.startPerformanceTest(getTestName(false), 100, () -> myFixture.doHighlighting()).assertTiming();
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
    WriteCommandAction.writeCommandAction(getProject(), getFile()).run(() -> new OptimizeImportsProcessor(getProject(), getFile()).runWithoutProgress());
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
