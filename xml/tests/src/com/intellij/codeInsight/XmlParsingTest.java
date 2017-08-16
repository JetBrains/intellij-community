/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.lang.*;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.dtd.DTDParserDefinition;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HTMLParserDefinition;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lang.xml.XmlASTFactory;
import com.intellij.lexer.EmbeddedTokenTypesProvider;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.parsing.xml.DtdParsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Mike, ik
 */
public class XmlParsingTest extends ParsingTestCase {

  public XmlParsingTest() {
    super("psi", "???", new XMLParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, XMLLanguage.INSTANCE, new XMLParserDefinition());
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, DTDLanguage.INSTANCE, new DTDParserDefinition());
    addExplicitExtension(LanguageASTFactory.INSTANCE, XMLLanguage.INSTANCE, new XmlASTFactory());
    addExplicitExtension(LanguageASTFactory.INSTANCE, DTDLanguage.INSTANCE, new XmlASTFactory());
    registerExtensionPoint(StartTagEndTokenProvider.EP_NAME, StartTagEndTokenProvider.class);
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  private void doTestXml(@NonNls String text) throws Exception {
    myLanguage = XMLLanguage.INSTANCE;
    doTest(text, "test.xml");
  }
  private void doTestDtd(@NonNls String text) throws Exception {
    myLanguage = DTDLanguage.INSTANCE;
    doTest(text, "test.dtd");
  }
  private void doTest(@NonNls String text, @NonNls String fileName) throws Exception {
    PsiFile file = createFile(fileName, text);
    checkResult("test" +getTestName(false), file);
  }

  public void testNewParsing1() throws Exception {
    doTestXml("<a></a>");
  }

  public void testEmptyCommentParsing() throws Exception {
    doTestXml("<!---->");
  }

  public void testNewParsing2() throws Exception {
    doTestXml("<a>   </a>");
  }

  public void testNewParsing3() throws Exception {
    doTestXml("<a   </a>");
  }

  public void testNewParsing4() throws Exception {
    doTestXml("<a> <b> </a>");
  }

  public void testNewParsing5() throws Exception {
    doTestXml("<a> <b/> </a>");
  }

  public void testNewParsing6() throws Exception {
    doTestXml("<!DOCTYPE greeting SYSTEM \"hello.dtd\" [<!ELEMENT greeting EMPTY>]> <a> <b/> </a>");
  }

  public void testNewParsing7() throws Exception {
    doTestXml("<a> <b> <c> <d> </a>");
  }

  public void testNewParsing8() throws Exception {
    doTestXml("<a blah=\"blah\"/>");
  }

  public void testNewParsing9() throws Exception {
    doTestXml("<a asd=\"asd\"></a>");
  }

  public void testNewParsing10() throws Exception {
    doTestXml("<a asd=\"asd\"> <s> </a>");
  }

  public void testNewParsing11() throws Exception {
    doTestXml("<!-- <!-- --> <a asd=\"asd\"> <s> </a>");
  }

  public void testNewParsing12() throws Exception {
    doTestXml("<a><a ajsdg<a></a></a>");
  }

  public void testNewParsing13() throws Exception {
    doTestXml("<a><b><c>\n" + "xxx \n" + "xxxx\n" + "<</b></a>");
  }

  public void testNewParsing14() throws Exception {
    doTestXml("<a><a ajsdg = \"\"></a></a>");
  }

  public void testNewParsing15() throws Exception {
    doTestXml("<!DOCTYPE a [<!ELEMENT a (a)>]> <a><a ajsdg = \"\"></a></a>");
  }

  public void testNewParsing19() throws Exception {
    doTestXml("<!DOCTYPE aaa [" + "<!ELEMENT a #EMPTY>" + "<!ATTLIST a" + " xx CDATA #IMPLIED" + " yy  #IMPLIED" + " zz CDATA #IMPLIED>]>\n" +
              "<a> <b> </b> </a>");
  }

  public void testNewParsing20() throws Exception {
    doTestXml("<!DOCTYPE root [\n" + "<!\n" + "]>\n" + "<root>\n" + "\n" + "</root>");
  }

  public void testEntityInAttr() throws Exception {
    doTestXml("<a href=\"&n\"/>");
  }

  public void testEntityInContent() throws Exception {
    doTestXml("<a> &n </a>");
  }

  public void testMultyRoots() throws Exception {
    doTestXml("<a href=\"\"/> <b/>");
  }

  public void testMultyRootsWithErrorsBetween() throws Exception {
    doTestXml("<a href=\"\"/> </ss><b/>");
  }

  public void testGtInTagContent() throws Exception {
    doTestXml("<a>></a>");
  }

  public void testManyErrors() throws Exception {
    doTestXml(loadFile("manyErrors.xml"));
  }

  public void _testLexerPerformance1() throws Exception {
    final String text = loadFile("pallada.xml");
    XmlLexer lexer = new XmlLexer();
    doLex(lexer, text);
    final FilterLexer filterLexer = new FilterLexer(new XmlLexer(),
                                                    new FilterLexer.SetFilter(
                                                      LanguageParserDefinitions.INSTANCE.forLanguage(XMLLanguage.INSTANCE)
                                                        .getWhitespaceTokens()));
    doLex(filterLexer, text);
    doLex(lexer, text);
    doLex(filterLexer, text);
    doLex(filterLexer, text);
  }

  public void _testLexerPerformance2() throws Exception {
    final String text = loadFile("performance2.xml");
    XmlLexer lexer = new XmlLexer();
    doLex(lexer, text);
    final FilterLexer filterLexer = new FilterLexer(new XmlLexer(),
                                                    new FilterLexer.SetFilter(
                                                      LanguageParserDefinitions.INSTANCE.forLanguage(XMLLanguage.INSTANCE)
                                                        .getWhitespaceTokens()));
    doLex(filterLexer, text);
    doLex(lexer, text);
    for (int i = 0; i < 20; i++) {
      doLex(filterLexer, text);
    }
  }

  private static void doLex(Lexer lexer, final String text) {
    lexer.start(text);
    long time = System.currentTimeMillis();
    int count = 0;
    while (lexer.getTokenType() != null) {
      lexer.advance();
      count++;
    }
    System.out.println("Plain lexing took " + (System.currentTimeMillis() - time) + "ms lexems count:" + count);
  }

  private static void transformAllChildren(final ASTNode file) {
    for (ASTNode child = file.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      transformAllChildren(child);
    }
  }

  public void _testPerformance1() throws Exception {
    final String text = loadFile("pallada.xml");
    long time = System.currentTimeMillis();
    final PsiFile file = createFile("test.xml", text);
    transformAllChildren(file.getNode());
    System.out.println("Old parsing took " + (System.currentTimeMillis() - time) + "ms");
    int index = 0;
    while (index++ < 10) {
      newParsing(text);
    }
    LeafElement firstLeaf = TreeUtil.findFirstLeaf(file.getNode());
    index = 0;
    do {
      index++;
    }
    while ((firstLeaf = TreeUtil.nextLeaf(firstLeaf, null)) != null);
    System.out.println("For " + index + " lexems");
  }

  public void _testReparsePerformance() throws Exception {
    final String text = loadFile("performance2.xml");
    final PsiFile file = createFile("test.xml", text);
    transformAllChildren(file.getNode());
    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);

    System.gc();
    System.gc();

    new WriteCommandAction(getProject(), file) {
      @Override
      protected void run(@NotNull final Result result) {
        PlatformTestUtil.startPerformanceTest("XML reparse using PsiBuilder", 2500, () -> {
          for (int i = 0; i < 10; i++) {
            final long tm = System.currentTimeMillis();
            doc.insertString(0, "<additional root=\"tag\"/>");
            PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
            System.out.println("Reparsed for: " + (System.currentTimeMillis() - tm));
          }
        }).useLegacyScaling().assertTiming();
      }
    }.execute();
  }

  public void _testPerformance2() throws Exception {
    final String text = loadFile("performance2.xml");
    long time = System.currentTimeMillis();
    final PsiFile file = createFile("test.xml", text);
    transformAllChildren(file.getNode());
    System.out.println("Old parsing took " + (System.currentTimeMillis() - time) + "ms");
    int index = 0;
    while (index++ < 10) {
      newParsing(text);
    }
    LeafElement firstLeaf = TreeUtil.findFirstLeaf(file.getNode());
    index = 0;
    do {
      index++;
    }
    while ((firstLeaf = TreeUtil.nextLeaf(firstLeaf, null)) != null);
    System.out.println("For " + index + " lexems");
  }

  private static void newParsing(final String text) {
    long time = System.currentTimeMillis();

    ASTFactory.lazy(XmlElementType.XML_FILE, text).getFirstChildNode(); // ensure parsed

    System.out.println("parsed for " + (System.currentTimeMillis() - time) + "ms");
  }

  public void testXmlDecl() throws Exception {
    doTestXml("<?xml version=\"1.0\" encoding='cp1251' ?>");
  }

  public void testXmlDecl2() throws Exception {
    doTestXml("<?xml version=\"1.0\" encoding='cp1251' ?> <foo></foo>");
  }

  public void testXmlDecl3() throws Exception {
    doTestXml("<?xml version=\"1.0\" encoding='cp1251' > <foo></foo>");
  }

  public void testDoctype1() throws Exception {
    doTestXml("<!DOCTYPE greeting SYSTEM \"hello.dtd\">");
  }

  public void testDoctype2() throws Exception {
    doTestXml("<!DOCTYPE toc " + "PUBLIC \"-//Sun Microsystems Inc.//DTD JavaHelp TOC Version 1.0//EN\" " +
           "\"http://java.sun.com/products/javahelp/toc_1_0.dtd\">");
  }

  public void testDoctype3() throws Exception {
    doTestXml("<!DOCTYPE greeting [" + "<!ELEMENT greeting (#PCDATA)>" + "]>");
  }

  public void testDoctype4() throws Exception {
    doTestXml("<!DOCTYPE greeting [" + "<!ELEMENT br EMPTY>" + "]>");
  }

  public void testDoctype5() throws Exception {
    doTestXml("<!DOCTYPE greeting [" + "<!ELEMENT p (#PCDATA|a|ul|b|i|em)*>" + "]>");
  }

  public void testDoctype6() throws Exception {
    doTestXml("<!DOCTYPE greeting [" + "<!ELEMENT %name.para; %content.para; >" + "]>");
  }

  public void testDoctype7() throws Exception {
    doTestXml("<?xml version='1.0' encoding='ISO-8859-1' ?>" + "<!DOCTYPE toc SYSTEM 'dtds/ejb-jar_2_0.dtd'>" + "<test>" + "</test>");
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

  public void testComment1() throws Exception {
    doTestXml("<!-- declarations for <head> & <body> -->");
  }

  public void testComment2() throws Exception {
    doTestDtd("<!ELEMENT data-sources (#PCDATA)> <!-- abc --> <!ATTLIST data-sources path CDATA #IMPLIED");
  }

  public void testComment3() throws Exception {
    doTestXml("<a><!--<b>--></a>");
  }

  public void testElements1() throws Exception {
    doTestXml("<a></a>");
  }

  public void testElements2() throws Exception {
    doTestXml("<a/>");
  }

  public void testElements3() throws Exception {
    doTestXml("<a><b><c/><d/></b><e/><f><g/></f></a>");
  }

  public void testElements4() throws Exception {
    doTestXml("<project name=\"IDEA_ZKM\">\n" + "<!-- set global properties for this build -->\n" + "<property value=\"off\" />\n" +
           "</project>\n");
  }

  public void testElements5() throws Exception {
    doTestXml("<ns1:a><ns2:b/></ns1:a>");
  }

  public void testElements6() throws Exception {
    doTestXml("<!DOCTYPE project [" + "<!ENTITY targets SYSTEM \"file:../myproject/targets.xml\">" + "]>" + "<project>" + "</project>");
  }

  public void testAttributes1() throws Exception {
    doTestXml("<a att1='val1' att2=\"val2\"></a>");
  }

  public void testAttributes2() throws Exception {
    doTestXml("<a><b c=\"\" > </b> </a>");
  }

  public void testAttributes3() throws Exception {
    doTestXml("<a><b c=\"a, b, c\" > </b> </a>");
  }

  public void testAttributes4() throws Exception {
    doTestXml("<a><b c=\"a  b\" > </b> </a>");
  }

  public void testAttributes5() throws Exception {
    doTestXml("<a><b c=\"a\"d=\"\" > </b> </a>");
  }

  public void testManualEmptyAttributeParsing() throws Exception{
    String value = "<a><b c=\"\" > </b> </a>";
    final CompositeElement element = parseManually(value, XmlElementType.XML_ELEMENT_DECL, XmlEntityDecl.EntityContextType.GENERIC_XML);
    checkResult("testManualEmptyAttributeParsing.txt", DebugUtil.treeToString(element, false));
  }

  private static CompositeElement parseManually(final String value,
                                                final IElementType xmlElementDecl,
                                                XmlEntityDecl.EntityContextType parseType) {
    return (CompositeElement)new DtdParsing(value, xmlElementDecl, parseType, null).parse();
  }

  public void testCharacters1() throws Exception {
    doTestXml("<a>someChar\nData</a>");
  }

  public void testEditing1() throws Exception {
    doTestXml("<<a></a>");
  }

  public void testEditing2() throws Exception {
    doTestXml("<a><b/> < <c/></a>");
  }

  public void testEditing3() throws Exception {
    doTestXml("<a><b <c/></a>");
  }

  public void testEditing4() throws Exception {
    doTestXml("<a><b>< <c/></a>");
  }

  public void testEditing5() throws Exception {
    doTestXml("<a><b></ <c/></a>");
  }

  public void testEditing6() throws Exception {
    doTestXml("<one>     <two ,b\"/></one>");
  }

  public void testEditing7() throws Exception {
    doTestXml("<one>     <two a,b\"/></one>");
  }

  public void testEditing8() throws Exception {
    doTestXml("<one>     <two a b=\"\"/></one>");
  }

  public void testEditing9() throws Exception {
    doTestXml("<one> <two a b=\"\"> ashdgjkasgd <aksjhdk></two></one>");
  }


  public void testCdata1() throws Exception {
    doTestXml("<a><![CDATA[<greeting>Hello, world!</greeting>]]></a>");
  }

  public void testCdata2() throws Exception {
    doTestXml("<a><![CDATA[someData <!--</greeting>--> more data]]></a>");
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
    doTestDtd("<![%sgml.features;[\n" + "<![IGNORE[\n" +
              "<!ENTITY % dbgenent SYSTEM \"http://www.oasis-open.org/docbook/xml/configerror.txt\">\n" + "]]>\n" + "]]>");
  }

  public void testConditionalSection3() throws Exception {
    doTestDtd("<!ENTITY % dbhier.module \"INCLUDE\">\n" + "<![ %dbhier.module; [\n" + "<!ENTITY % dbhier PUBLIC\n" +
              "\"-//OASIS//ELEMENTS DocBook Document Hierarchy V4.4//EN\"\n" + "\"dbhierx.mod\">\n" + "%dbhier;\n" + "]]>");
  }

  public void testEntityRef1() throws Exception {
    doTestXml("<a>%types;</a>");
  }

  public void testEntityRef3() throws Exception {
    doTestDtd("<!ELEMENT project (target | taskdef | %types; | property )*>");
  }

  public void testEntityRef4() throws Exception {
    doTestXml("<a b=\"aa &gt; bb\" />");
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

  public void testUnbalanced1() throws Exception {
    doTestXml("<a><b></a>");
  }

  public void testUnbalanced2() throws Exception {
    doTestXml("<a><b><c></a>");
  }

  public void testUnbalanced3() throws Exception {
    doTestXml("<a><b></bb></a>");
  }

  public void testUnbalanced4() throws Exception {
    doTestXml("<a><b><b/></c></a>");
  }

  public void testDtdUrl1() {
    XmlFile file = (XmlFile)createFile("test.xml", "<!DOCTYPE greeting SYSTEM \"hello.dtd\">");

    XmlDocument document = file.getDocument();
    XmlProlog prolog = document.getProlog();
    XmlDoctype doctype = prolog.getDoctype();
    String url = doctype.getDtdUri();
    assertTrue("testDtdUrl1", "hello.dtd".equals(url));
  }

  public void testContent1() throws Exception {
    XmlFile file = (XmlFile)createFile("test.xml", "<a>   \nxxx   \n</a>");
    checkResult("testContent1", file);
    assertTrue("xxx".equals(file.getDocument().getRootTag().getValue().getTrimmedText()));
  }

  public void testUnopenedTag1() throws Exception {
    doTestXml("</foo>");
  }

  public void testUnclosedTag() throws Exception {
    XmlFile file = (XmlFile)createFile("test.xml", "<a>xxx");
    checkResult("testUnclosedTag", file);
    assertTrue("xxx".equals(file.getDocument().getRootTag().getValue().getText()));
  }

  public void testUnclosedTag2() throws Exception {
    doTestXml("<a");
  }

  public void testProcessingInstruction1() throws Exception {
    doTestXml("<?This is=\"PI\"?>");
  }

  public void testProcessingInstruction2() throws Exception {
    doTestXml("<a><?This is=\"PI\"?></a>");
  }

  public void testProcessingInstruction3() throws Exception {
    doTestXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<?xml-stylesheet type=\"text/xsl\" href=\"document.xsl\"?>" +
           "<!-- @(#) $Id$ -->" + "<!DOCTYPE api SYSTEM \"document.dtd\">" + "<api>" + "</api>");
  }

  public void testCharRef1() throws Exception {
    doTestXml("<a>&#123;</a>");
  }

  public void testCharRef2() throws Exception {
    doTestXml("<a>&#x123;</a>");
  }

  public void testCharRef3() throws Exception {
    doTestXml("&#123;");
  }

  public void testCharRef4() throws Exception {
    doTestXml("&#xaBcD123;");
  }

  public void testCharRef5() throws Exception {
    doTestXml("<a attr=\"abc&#123;\"/>");
  }

  public void testColonName() throws Exception {
    doTestXml("<:foo/>");
  }

  public void testNotation1() throws Exception {
    doTestDtd("<!NOTATION data-sources SYSTEM \"x3\">");
  }

  public void testPrologInDtd() throws Exception {
    doTestDtd("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" + "<!ELEMENT idea-plugin>\n" + "<!ATTLIST idea-plugin\n" +
              "    url CDATA #IMPLIED>");
  }

  public void testEmptyElementsInDtd() throws Exception {
    doTestDtd("<!ELEMENT >\n<!ELEMENT name (,)\n<!ATTLIST > <!ELEMENT zzz (aa?, bb)+> <!ELEMENT xxx(aaa,)>" + "<!ATTLIST %aaa;>");
  }

  public void testElementDeclaration() throws Exception {
    doTestDtd(
      "<!ELEMENT xxx EMPTY>\n<!ELEMENT xxx2 ANY>\n<!ELEMENT %name.para; %content.para; >\n<!ELEMENT xxx3 (#PCDATA | a) >\n<!ELEMENT xxx4 >");
  }

  public void testEntityDeclaration() throws Exception {
    doTestDtd("<!ENTITY xxx > <!ENTITY >");
  }

  public void testEntityDeclaration2() throws Exception {
    String s = "| %pre.qname; | %blockquote.qname; | %address.qname;";
    CompositeElement element = parseManually(s, XmlElementType.XML_ELEMENT_CONTENT_SPEC, XmlEntityDecl.EntityContextType.ELEMENT_CONTENT_SPEC);
    checkResult("testEntityDeclaration2.txt", DebugUtil.treeToString(element, false));
  }

  public void testEntityInAttlistDeclaration() throws Exception {
    doTestDtd("<!ATTLIST %span.qname;      %Common.attrib;>");
  }

  public void testSGMLDtd() throws Exception {
    doTestDtd("<!ELEMENT name - - (%inline;)* +(A)> <!ATTLIST (A|B) >\n" +
              "<!ELEMENT (E|E2) - O (%flow;)*       -- table header cell, table data cell-->\n" +
              "<!ELEMENT BODY O O (%block;|SCRIPT)+ +(INS|DEL) -- document body -->");
  }

  public void testNotation2() throws Exception {
    doTestXml("<!DOCTYPE x3 [<!NOTATION data-sources SYSTEM \"x3\">]>");
  }

  public void testWhitespaceBeforeName() throws Exception {
    doTestXml("<a>< a</a>");
  }

  public void testCustomMimeType() throws Exception {
    final Language language = new MyLanguage();
    addExplicitExtension(LanguageHtmlScriptContentProvider.INSTANCE, language, new HtmlScriptContentProvider() {
      @Override
      public IElementType getScriptElementType() {
        return new IElementType("MyElementType", language);
      }

      @Nullable
      @Override
      public Lexer getHighlightingLexer() {
        return null;
      }
    });
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, HTMLLanguage.INSTANCE, new HTMLParserDefinition());
    addExplicitExtension(LanguageASTFactory.INSTANCE, HTMLLanguage.INSTANCE, new XmlASTFactory());
    registerExtensionPoint(EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME, EmbeddedTokenTypesProvider.class);
    myLanguage = HTMLLanguage.INSTANCE;
    doTest("<script type=\"application/custom\">Custom Script</script>", "test.html");
  }

  public void testKeywordsAsName() throws Exception {
    doTestDtd("<!ELEMENT FIELD ANY>\n" +
              "<!ELEMENT PUBLIC ANY>\n" +
              "<!ELEMENT EMPTY ANY>\n" +
              "<!ELEMENT ANY ANY>\n" +
              "<!ELEMENT AND (FIELD|PUBLIC|EMPTY|ANY)*>");

  }

  static class MyLanguage extends Language implements InjectableLanguage {
    protected MyLanguage() {
      super("MyLanguage", "application/custom");
    }
  }
}
