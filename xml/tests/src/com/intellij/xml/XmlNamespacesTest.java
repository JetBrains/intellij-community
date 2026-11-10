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
import com.intellij.javaee.ExternalResourceManagerExBase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class XmlNamespacesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/xml/tests/testData/unusedNs");

  @Test
  public void testUnusedNamespaces() {
    doUnusedDeclarationTest(
      "<all xmlns=\"http://www.w3.org/2001/XMLSchema\" <warning descr=\"Namespace declaration is never used\">xmlns:xsi=\"http://www.w3.org/2001/XMLSc<caret>hema-instance\"</warning>/>",
      "<all xmlns=\"http://www.w3.org/2001/XMLSchema\"/>", XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testImplicitPrefixUsage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.xml", """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                 xmlns:x2="http://www.w3.org/2001/XMLSchema"
                 <warning descr="Namespace declaration is never used">xmlns:x3="http://www.w3.org/2001/XMLSchema"</warning> >
        <xs:element name="a" type="x2:string"/>
      </xs:schema>""");
    myFixture.testHighlighting();
  }

  @Test
  public void testUnusedLocationDetection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testWSDD() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.xml",
                              """
                                <deployment xmlns="http://xml.apache.org/axis/wsdd/" xmlns:java="http://xml.apache.org/axis/wsdd/providers/java">
                                <typeMapping deserializer="org.apache.axis.encoding.ser.BeanDeserializerFactory" encodingStyle="" qname="ns38:AxisAnalysis" serializer="org.apache.axis.encoding.ser.BeanSerializerFactory" languageSpecificType="java:com.pls.xactservice.axis.bindings.AxisAnalysis"/>
                                </deployment>""");
    myFixture.testHighlighting();
  }

  @Test
  public void testPrefixesInTagValues() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testLocallyUsedNamespace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testLocallyUsedNamespaceWithPrefix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.xml", """
      <s:foo xmlns:s="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://foo</error>"
             <warning descr="Namespace declaration is never used">xmlns:bar="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://bar</error>"</warning>
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://bar <error descr="Cannot resolve file 'bar.xsd'">bar.xsd</error> http://foo <error descr="Cannot resolve file 'foo.xsd'">foo.xsd</error>">

          <bar xmlns="<error descr="URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)">http://bar</error>"/>

      </s:foo>""");
    myFixture.testHighlighting();
  }

  @Test
  public void testSubDirectory() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("moved.xml", "trg/move-def.xsd");
  }

  @Test
  public void testSuppressedOptimize() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testUsedInXmlns() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("spring.xml", "spring-beans-2.5.xsd", "spring-batch-2.1.xsd");
    IntentionAction action = myFixture.getAvailableIntention(XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl"));
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("spring_after.xml");
  }

  @Test
  public void testXsiType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("import.xml", "import.xsd");
  }

  @Test
  public void testDoNotOptimizeWhenInspectionDisabled() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.disableInspections(new XmlUnusedNamespaceInspection());
    String text = "<all xmlns=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>";
    myFixture.configureByText(XmlFileType.INSTANCE, text);
    doOptimizeImportsTest(text);
  }

  @Test
  public void testFixAll() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("fixAll.xml", "spring-beans-2.5.xsd", "spring-batch-2.1.xsd");
    IntentionAction action = myFixture.findSingleIntention("Fix all");
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("fixAll_after.xml");
  }

  @Test
  public void testImplicitPrefixes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(XmlFileType.INSTANCE, """
      <schema xmlns="http://www.w3.org/2001/XMLSchema"\s
              xmlns:x="http://www.w3.org/2001/XMLSchema"
              <warning descr="Namespace declaration is never used">xmlns:y="http://www.w3.org/2001/XMLSchema"</warning>>
          <element name="a" default="x:y"/>
      </schema>""");
    myFixture.testHighlighting();
  }

  @Test
  public void testImplicitPrefixesPattern() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(XmlFileType.INSTANCE, """
      <html xmlns="http://www.w3.org/1999/xhtml"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <body about="wsdl:definitions/wsdl:types/xs:schema[@targetNamespace='http://www.w3schools.com/webservices/']">
          </body>
      </html>""");
    myFixture.testHighlighting();
  }

  @Test
  public void testHtml5Namespace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  @PerformanceUnitTest
  public void testPatternPerformanceProblem() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("idproblem.html");
    Benchmark.newBenchmark(myFixture.getTestName(false), () -> myFixture.doHighlighting()).start();
  }

  private void doUnusedDeclarationTest(String text, String after, String name) {
    doUnusedDeclarationTest(text, after, name, true);
  }

  private void doUnusedDeclarationTest(String text, String after, String name, boolean testOptimizeImports) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.xml", text);
    myFixture.testHighlighting();
    IntentionAction action = myFixture.getAvailableIntention(name);
    assertNotNull(action, name + " not found");
    myFixture.launchAction(action);
    myFixture.checkResult(after);

    myFixture.configureByText("a.xml", text);
    if (testOptimizeImports) {
      doOptimizeImportsTest(after);
    }
  }

  private void doOptimizeImportsTest(String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting();
    WriteCommandAction.writeCommandAction(myFixture.getProject(), myFixture.getFile()).run(() -> new OptimizeImportsProcessor(myFixture.getProject(), myFixture.getFile()).runWithoutProgress());
    myFixture.checkResult(after);
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new XmlInspectionToolProvider());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd",
                                                              myFixture.getTestDataPath() + "/web-app_2_5.xsd", myFixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://xml.apache.org/axis/wsdd/",
                                                              myFixture.getTestDataPath() + "/wsdd.dtd", myFixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://xml.apache.org/axis/wsdd/providers/java",
                                                              myFixture.getTestDataPath() + "/wsdd_provider_java.xsd", myFixture.getTestRootDisposable());
  }
}
