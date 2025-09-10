// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.testFramework;

import com.intellij.lang.*;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HTMLParserDefinition;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lang.xml.XmlASTFactory;
import com.intellij.lang.xml.XmlSyntaxDefinitionExtension;
import com.intellij.lexer.EmbeddedTokenTypesProvider;
import com.intellij.lexer.Lexer;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static com.intellij.xml.testFramework.XmlElementTypeServiceHelper.registerXmlElementTypeServices;

@RunWith(JUnit38AssumeSupportRunner.class)
public abstract class XmlParsingTestBase extends ParsingTestCase {

  protected XmlParsingTestBase(@NotNull String dataPath, @NotNull String fileExt, ParserDefinition @NotNull ... definitions) {
    super(dataPath, fileExt, definitions);
  }

  @Override
  protected String getTestDataPath() {
    return getXmlParsingTestDataPath();
  }

  private static String getXmlParsingTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerXmlElementTypeServices(getApplication(), getTestRootDisposable());
    addExplicitExtension(LanguageASTFactory.INSTANCE, XMLLanguage.INSTANCE, new XmlASTFactory());
    registerExtensionPoint(StartTagEndTokenProvider.EP_NAME, StartTagEndTokenProvider.class);
    addExplicitExtension(LanguageSyntaxDefinitions.getINSTANCE(), XMLLanguage.INSTANCE, new XmlSyntaxDefinitionExtension());
    addExplicitExtension(ElementTypeConverters.getInstance(), XMLLanguage.INSTANCE, new XmlElementTypeConverterExtension());
  }

  protected void doTestXml(@NonNls String text) throws Exception {
    myLanguage = XMLLanguage.INSTANCE;
    doTest(text, "test.xml");
  }

  protected void doTest(@NonNls String text, @NonNls String fileName) throws Exception {
    PsiFile file = createFile(fileName, text);
    checkResult("test" + getTestName(false), file);
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
    //noinspection SpellCheckingInspection
    doTestXml("<a><a ajsdg<a></a></a>");
  }

  public void testNewParsing13() throws Exception {
    doTestXml("""
                <a><b><c>
                xxx\s
                xxxx
                <</b></a>""");
  }

  public void testNewParsing14() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("<a><a ajsdg = \"\"></a></a>");
  }

  public void testNewParsing15() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("<!DOCTYPE a [<!ELEMENT a (a)>]> <a><a ajsdg = \"\"></a></a>");
  }

  public void testNewParsing19() throws Exception {
    doTestXml(
      "<!DOCTYPE aaa [" + "<!ELEMENT a #EMPTY>" + "<!ATTLIST a" + " xx CDATA #IMPLIED" + " yy  #IMPLIED" + " zz CDATA #IMPLIED>]>\n" +
      "<a> <b> </b> </a>");
  }

  public void testNewParsing20() throws Exception {
    doTestXml("""
                <!DOCTYPE root [
                <!
                ]>
                <root>
                
                </root>""");
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

  private static void transformAllChildren(final ASTNode file) {
    for (ASTNode child = file.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      transformAllChildren(child);
    }
  }

  public void testPerformance1() throws Exception {
    //noinspection SpellCheckingInspection
    doTestPerformance("pallada.xml");
  }

  public void testPerformance2() throws Exception {
    doTestPerformance("performance2.xml");
  }

  private void doTestPerformance(String fileName) throws IOException {
    final String text = loadFileDefault(getXmlParsingTestDataPath() + "psi/xml", fileName);
    long start = System.nanoTime();
    final PsiFile file = createFile(fileName, text);
    transformAllChildren(file.getNode());
    LOG.debug("First parsing took " + (System.nanoTime() - start) + "ns");

    var perfTest = Benchmark.newBenchmark("XML Parser Performance on " + fileName, () -> {
      for (int i = 0; i < 10; i++) {
        PsiFile next = createPsiFile("test" + i, text);
        transformAllChildren(next.getNode());
      }
    });

    perfTest.setup(() -> PsiManager.getInstance(getProject()).dropPsiCaches())
      .startAsSubtest(this.getClass().getSimpleName() + " " + perfTest.getLaunchName());

    LeafElement firstLeaf = TreeUtil.findFirstLeaf(file.getNode());
    int count = 0;
    do {
      count++;
    }
    while ((firstLeaf = TreeUtil.nextLeaf(firstLeaf, null)) != null);
    LOG.debug("For " + count + " lexemes");
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
    //noinspection SpellCheckingInspection
    doTestXml("<?xml version='1.0' encoding='ISO-8859-1' ?>" + "<!DOCTYPE toc SYSTEM 'dtds/ejb-jar_2_0.dtd'>" + "<test>" + "</test>");
  }

  public void testComment1() throws Exception {
    doTestXml("<!-- declarations for <head> & <body> -->");
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
    doTestXml("""
                <project name="IDEA_ZKM">
                <!-- set global properties for this build -->
                <property value="off" />
                </project>
                """);
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
    //noinspection SpellCheckingInspection
    doTestXml("<one> <two a b=\"\"> ashdgjkasgd <aksjhdk></two></one>");
  }


  public void testCdata1() throws Exception {
    doTestXml("<a><![CDATA[<greeting>Hello, world!</greeting>]]></a>");
  }

  public void testCdata2() throws Exception {
    doTestXml("<a><![CDATA[someData <!--</greeting>--> more data]]></a>");
  }

  public void testEntityRef1() throws Exception {
    doTestXml("<a>%types;</a>");
  }

  public void testEntityRef4() throws Exception {
    doTestXml("<a b=\"aa &gt; bb\" />");
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
    assertEquals("testDtdUrl1", "hello.dtd", url);
  }

  public void testContent1() throws Exception {
    XmlFile file = (XmlFile)createFile("test.xml", "<a>   \nxxx   \n</a>");
    checkResult("testContent1", file);
    assertEquals("xxx", file.getDocument().getRootTag().getValue().getTrimmedText());
  }

  public void testUnopenedTag1() throws Exception {
    doTestXml("</foo>");
  }

  public void testUnclosedTag() throws Exception {
    XmlFile file = (XmlFile)createFile("test.xml", "<a>xxx");
    checkResult("testUnclosedTag", file);
    assertEquals("xxx", file.getDocument().getRootTag().getValue().getText());
  }

  public void testUnclosedTag2() throws Exception {
    doTestXml("<a");
  }

  public void testMissingClosingTagName() throws Exception {
    doTestXml("<a></>");
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

  public void testNotation2() throws Exception {
    doTestXml("<!DOCTYPE x3 [<!NOTATION data-sources SYSTEM \"x3\">]>");
  }

  public void testWhitespaceBeforeName() throws Exception {
    doTestXml("<a>< a</a>");
  }

  public void testAllWhitespaces() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Root><page><content><locatieblok><locatie label="Locatie">EXAMPLE</locatie>\u2029<straat label="Straat">EXAMPLE</straat>\u2029<postcode label="Postcode">EXAMPLE</postcode> <plaats label="Plaats">EXAMPLE</plaats>\u2029\u2029<telomschrijving label="Telefoon omschrijving">T.</telomschrijving> <telefoon label="Telefoon">EXAMPLE</telefoon>\u2029\u2029<internet label="Internet">EXAMPLE</internet></locatieblok><naamblok><aanhefnaam label="Aanhef Naam Achternaam">Aanhef Naam Achternaam</aanhefnaam>\u2029<functie label="Functie">Functie</functie>\u2029<mobielomschr label="Mobiel omschrijving">M.</mobielomschr>\t<mobiel label="Mobiel">EXAMPLE</mobiel>\u2029<emailomschr label="Email omschrijving">E.</emailomschr>\t<email label="Email">EXAMPLE</email></naamblok></content></page></Root>
                """);
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
    registerParserDefinition(new HTMLParserDefinition());
    addExplicitExtension(LanguageASTFactory.INSTANCE, HTMLLanguage.INSTANCE, new XmlASTFactory());
    registerExtensionPoint(EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME, EmbeddedTokenTypesProvider.class);
    myLanguage = HTMLLanguage.INSTANCE;
    doTest("<script type=\"application/custom\">Custom Script</script>", "test.html");
  }

  static class MyLanguage extends Language implements InjectableLanguage {
    protected MyLanguage() {
      super("MyLanguage", "application/custom");
    }
  }
}
