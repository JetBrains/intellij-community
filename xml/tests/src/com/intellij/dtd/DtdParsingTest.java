// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dtd;

import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.dtd.DTDParserDefinition;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lang.xml.XmlASTFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.parsing.xml.DtdParsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.StartTagEndTokenProvider;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class DtdParsingTest extends ParsingTestCase {

  public DtdParsingTest() {
    super("psi/dtd", "dtd", new DTDParserDefinition(), new XMLParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addExplicitExtension(LanguageASTFactory.INSTANCE, XMLLanguage.INSTANCE, new XmlASTFactory());
    addExplicitExtension(LanguageASTFactory.INSTANCE, DTDLanguage.INSTANCE, new XmlASTFactory());
    registerExtensionPoint(StartTagEndTokenProvider.EP_NAME, StartTagEndTokenProvider.class);
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  private void doTestDtd(@NonNls String text) throws Exception {
    PsiFile file = createFile("test.dtd", text);
    checkResult("test" + getTestName(false), file);
  }

  public void testAttlist1() throws Exception {
    doTestDtd("<!ATTLIST termdef id ID #REQUIRED name CDATA #IMPLIED>");
  }

  public void testAttlist2() throws Exception {
    doTestDtd("<!ATTLIST termdef type (bullets|ordered|glossary)  \"ordered\">");
  }

  public void testAttlist3() throws Exception {
    doTestDtd("<!ATTLIST termdef  method  CDATA   #FIXED \"POST\">");
  }

  public void testAttlist4() throws Exception {
    doTestDtd("<!ATTLIST termdef default-charset %CHARSET; \"iso-8859-1\">");
  }

  public void testComment2() throws Exception {
    doTestDtd("<!ELEMENT data-sources (#PCDATA)> <!-- abc --> <!ATTLIST data-sources path CDATA #IMPLIED");
  }

  public void testManualEmptyAttributeParsing() {
    String value = "<a><b c=\"\" > </b> </a>";
    final CompositeElement element = parseManually(value, XmlElementType.XML_ELEMENT_DECL, XmlEntityDecl.EntityContextType.GENERIC_XML);
    checkResult("testManualEmptyAttributeParsing.txt", DebugUtil.treeToString(element, true));
  }

  private static CompositeElement parseManually(final String value,
                                                final IElementType xmlElementDecl,
                                                XmlEntityDecl.EntityContextType parseType) {
    return (CompositeElement)new DtdParsing(value, xmlElementDecl, parseType, null).parse();
  }

  public void testDtd1() throws Exception {
    doTestDtd("<!ELEMENT data-sources (#PCDATA)> <!ATTLIST data-sources path CDATA #IMPLIED>");
  }

  public void testElementDecl1() throws Exception {
    doTestDtd("<!ELEMENT data-sources EMPTY>");
  }

  public void testElementDecl2() throws Exception {
    doTestDtd("<!ELEMENT data-sources ANY>");
  }

  public void testConditionalSection1() throws Exception {
    doTestDtd("<![ INCLUDE [ <!ELEMENT data-sources ANY> ]]>");
  }

  public void testConditionalSection2() throws Exception {
    doTestDtd("""
                <![%sgml.features;[
                <![IGNORE[
                <!ENTITY % dbgenent SYSTEM "http://www.oasis-open.org/docbook/xml/configerror.txt">
                ]]>
                ]]>""");
  }

  public void testConditionalSection3() throws Exception {
    doTestDtd("""
                <!ENTITY % dbhier.module "INCLUDE">
                <![ %dbhier.module; [
                <!ENTITY % dbhier PUBLIC
                "-//OASIS//ELEMENTS DocBook Document Hierarchy V4.4//EN"
                "dbhierx.mod">
                %dbhier;
                ]]>""");
  }

  public void testEntityRef3() throws Exception {
    doTestDtd("<!ELEMENT project (target | taskdef | %types; | property )*>");
  }

  public void testEntityRef5() throws Exception {
    doTestDtd("<!ATTLIST a %coreattrs; version CDATA #FIXED \"1.0\"");
  }

  public void testEntityRef6() throws Exception {
    doTestDtd("&common;");
  }

  public void testEntityRef7() throws Exception {
    doTestDtd("<!ATTLIST foo someBoolean (%boolean;) \"true\" someString CDATA #IMPLIED >");
  }

  public void testEntityDecl1() throws Exception {
    doTestDtd("<!ENTITY % types \"fileset | patternset mapper\">");
  }

  public void testEntityDecl2() throws Exception {
    doTestDtd("<!ENTITY types \"fileset | patternset mapper\">");
  }

  public void testEntityDecl3() throws Exception {
    doTestDtd("<!ENTITY build-common SYSTEM \"common.xml\">");
  }

  public void testEntityDecl4() throws Exception {
    doTestDtd(
      "<!ENTITY open-hatch PUBLIC \"-//Textuality//TEXT Standard open-hatch boilerplate//EN\" \"http://www.textuality.com/boilerplate/OpenHatch.xml\">");
  }

  public void testNotation1() throws Exception {
    doTestDtd("<!NOTATION data-sources SYSTEM \"x3\">");
  }

  public void testPrologInDtd() throws Exception {
    doTestDtd("""
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <!ELEMENT idea-plugin>
                <!ATTLIST idea-plugin
                    url CDATA #IMPLIED>""");
  }

  public void testEmptyElementsInDtd() throws Exception {
    doTestDtd("""
                <!ELEMENT >
                <!ELEMENT name (,)
                <!ATTLIST > <!ELEMENT zzz (aa?, bb)+> <!ELEMENT xxx(aaa,)><!ATTLIST %aaa;>""");
  }

  public void testElementDeclaration() throws Exception {
    doTestDtd(
      "<!ELEMENT xxx EMPTY>\n<!ELEMENT xxx2 ANY>\n<!ELEMENT %name.para; %content.para; >\n<!ELEMENT xxx3 (#PCDATA | a) >\n<!ELEMENT xxx4 >");
  }

  public void testEntityDeclaration() throws Exception {
    doTestDtd("<!ENTITY xxx > <!ENTITY >");
  }

  public void testEntityDeclaration2() {
    String s = "| %pre.qname; | %blockquote.qname; | %address.qname;";
    CompositeElement element =
      parseManually(s, XmlElementType.XML_ELEMENT_CONTENT_SPEC, XmlEntityDecl.EntityContextType.ELEMENT_CONTENT_SPEC);
    checkResult("testEntityDeclaration2.txt", DebugUtil.treeToString(element, true));
  }

  public void testEntityInAttlistDeclaration() throws Exception {
    doTestDtd("<!ATTLIST %span.qname;      %Common.attrib;>");
  }

  public void testSGMLDtd() throws Exception {
    doTestDtd("""
                <!ELEMENT name - - (%inline;)* +(A)> <!ATTLIST (A|B) >
                <!ELEMENT (E|E2) - O (%flow;)*       -- table header cell, table data cell-->
                <!ELEMENT BODY O O (%block;|SCRIPT)+ +(INS|DEL) -- document body -->""");
  }

  public void testKeywordsAsName() throws Exception {
    doTestDtd("""
                <!ELEMENT FIELD ANY>
                <!ELEMENT PUBLIC ANY>
                <!ELEMENT EMPTY ANY>
                <!ELEMENT ANY ANY>
                <!ELEMENT AND (FIELD|PUBLIC|EMPTY|ANY)*>""");
  }

  public void testKeywordsAsAttributeName() throws Exception {
    doTestDtd("<!ATTLIST park conditionType (DONT_CARE|EMPTY|VALUE|REGEXP) 'DONT_CARE'>");
  }
}
