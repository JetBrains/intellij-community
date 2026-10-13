// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dtd;

import com.intellij.lang.dtd.DTDParserDefinition;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.parsing.xml.DtdParsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlEntityContextType;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

@TestApplication
public class DtdParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/" + "/" + "psi/dtd",
                   "dtd", new DTDParserDefinition(), new XMLParserDefinition());

  private void doTestDtd(@NonNls String text) {
    ParsingTestFixture fixture = parsingFixture.get();
    PsiFile file = fixture.createFile("test.dtd", text);
    fixture.checkResult("test" + fixture.getTestName(), file);
  }

  @Test
  public void testAttlist1() {
    doTestDtd("<!ATTLIST termdef id ID #REQUIRED name CDATA #IMPLIED>");
  }

  @Test
  public void testAttlist2() {
    doTestDtd("<!ATTLIST termdef type (bullets|ordered|glossary)  \"ordered\">");
  }

  @Test
  public void testAttlist3() {
    doTestDtd("<!ATTLIST termdef  method  CDATA   #FIXED \"POST\">");
  }

  @Test
  public void testAttlist4() {
    doTestDtd("<!ATTLIST termdef default-charset %CHARSET; \"iso-8859-1\">");
  }

  @Test
  public void testComment2() {
    doTestDtd("<!ELEMENT data-sources (#PCDATA)> <!-- abc --> <!ATTLIST data-sources path CDATA #IMPLIED");
  }

  @Test
  public void testManualEmptyAttributeParsing() {
    String value = "<a><b c=\"\" > </b> </a>";
    final CompositeElement element = parseManually(value, XmlElementType.XML_ELEMENT_DECL, XmlEntityContextType.GENERIC_XML);
    parsingFixture.get().checkResult("testManualEmptyAttributeParsing.txt", DebugUtil.treeToString(element, true));
  }

  private static CompositeElement parseManually(final String value,
                                                final IElementType xmlElementDecl,
                                                XmlEntityContextType parseType) {
    return (CompositeElement)new DtdParsing(value, xmlElementDecl, parseType, null).parse();
  }

  @Test
  public void testDtd1() {
    doTestDtd("<!ELEMENT data-sources (#PCDATA)> <!ATTLIST data-sources path CDATA #IMPLIED>");
  }

  @Test
  public void testElementDecl1() {
    doTestDtd("<!ELEMENT data-sources EMPTY>");
  }

  @Test
  public void testElementDecl2() {
    doTestDtd("<!ELEMENT data-sources ANY>");
  }

  @Test
  public void testConditionalSection1() {
    doTestDtd("<![ INCLUDE [ <!ELEMENT data-sources ANY> ]]>");
  }

  @Test
  public void testConditionalSection2() {
    doTestDtd("""
                <![%sgml.features;[
                <![IGNORE[
                <!ENTITY % dbgenent SYSTEM "http://www.oasis-open.org/docbook/xml/configerror.txt">
                ]]>
                ]]>""");
  }

  @Test
  public void testConditionalSection3() {
    doTestDtd("""
                <!ENTITY % dbhier.module "INCLUDE">
                <![ %dbhier.module; [
                <!ENTITY % dbhier PUBLIC
                "-//OASIS//ELEMENTS DocBook Document Hierarchy V4.4//EN"
                "dbhierx.mod">
                %dbhier;
                ]]>""");
  }

  @Test
  public void testEntityRef3() {
    doTestDtd("<!ELEMENT project (target | taskdef | %types; | property )*>");
  }

  @Test
  public void testEntityRef5() {
    doTestDtd("<!ATTLIST a %coreattrs; version CDATA #FIXED \"1.0\"");
  }

  @Test
  public void testEntityRef6() {
    doTestDtd("&common;");
  }

  @Test
  public void testEntityRef7() {
    doTestDtd("<!ATTLIST foo someBoolean (%boolean;) \"true\" someString CDATA #IMPLIED >");
  }

  @Test
  public void testEntityDecl1() {
    doTestDtd("<!ENTITY % types \"fileset | patternset mapper\">");
  }

  @Test
  public void testEntityDecl2() {
    doTestDtd("<!ENTITY types \"fileset | patternset mapper\">");
  }

  @Test
  public void testEntityDecl3() {
    doTestDtd("<!ENTITY build-common SYSTEM \"common.xml\">");
  }

  @Test
  public void testEntityDecl4() {
    doTestDtd(
      "<!ENTITY open-hatch PUBLIC \"-//Textuality//TEXT Standard open-hatch boilerplate//EN\" \"http://www.textuality.com/boilerplate/OpenHatch.xml\">");
  }

  @Test
  public void testNotation1() {
    doTestDtd("<!NOTATION data-sources SYSTEM \"x3\">");
  }

  @Test
  public void testPrologInDtd() {
    doTestDtd("""
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <!ELEMENT idea-plugin>
                <!ATTLIST idea-plugin
                    url CDATA #IMPLIED>""");
  }

  @Test
  public void testEmptyElementsInDtd() {
    doTestDtd("""
                <!ELEMENT >
                <!ELEMENT name (,)
                <!ATTLIST > <!ELEMENT zzz (aa?, bb)+> <!ELEMENT xxx(aaa,)><!ATTLIST %aaa;>""");
  }

  @Test
  public void testElementDeclaration() {
    doTestDtd(
      "<!ELEMENT xxx EMPTY>\n<!ELEMENT xxx2 ANY>\n<!ELEMENT %name.para; %content.para; >\n<!ELEMENT xxx3 (#PCDATA | a) >\n<!ELEMENT xxx4 >");
  }

  @Test
  public void testEntityDeclaration() {
    doTestDtd("<!ENTITY xxx > <!ENTITY >");
  }

  @Test
  public void testEntityDeclaration2() {
    String s = "| %pre.qname; | %blockquote.qname; | %address.qname;";
    CompositeElement element =
      parseManually(s, XmlElementType.XML_ELEMENT_CONTENT_SPEC, XmlEntityContextType.ELEMENT_CONTENT_SPEC);
    parsingFixture.get().checkResult("testEntityDeclaration2.txt", DebugUtil.treeToString(element, true));
  }

  @Test
  public void testEntityInAttlistDeclaration() {
    doTestDtd("<!ATTLIST %span.qname;      %Common.attrib;>");
  }

  @Test
  public void testSGMLDtd() {
    doTestDtd("""
                <!ELEMENT name - - (%inline;)* +(A)> <!ATTLIST (A|B) >
                <!ELEMENT (E|E2) - O (%flow;)*       -- table header cell, table data cell-->
                <!ELEMENT BODY O O (%block;|SCRIPT)+ +(INS|DEL) -- document body -->""");
  }

  @Test
  public void testKeywordsAsName() {
    doTestDtd("""
                <!ELEMENT FIELD ANY>
                <!ELEMENT PUBLIC ANY>
                <!ELEMENT EMPTY ANY>
                <!ELEMENT ANY ANY>
                <!ELEMENT AND (FIELD|PUBLIC|EMPTY|ANY)*>""");
  }

  @Test
  public void testKeywordsAsAttributeName() {
    doTestDtd("<!ATTLIST park conditionType (DONT_CARE|EMPTY|VALUE|REGEXP) 'DONT_CARE'>");
  }
}
