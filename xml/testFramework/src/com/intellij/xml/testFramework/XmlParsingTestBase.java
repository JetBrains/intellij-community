// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.testFramework;

import com.intellij.html.embedding.HtmlEmbeddedContentSupport;
import com.intellij.lang.ASTNode;
import com.intellij.lang.HtmlScriptContentProvider;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageHtmlScriptContentProvider;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HTMLParserDefinition;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lexer.EmbeddedTokenTypesProvider;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JUnit 5 base of the XML parser tests. Each concrete subclass needs {@link com.intellij.testFramework.junit5.TestApplication}.
 */
public abstract class XmlParsingTestBase {
  private static final Logger LOG = Logger.getInstance(XmlParsingTestBase.class);

  protected static final TestFixture<Project> projectFixture = projectFixture();

  protected final TestFixture<ParsingTestFixture> parsingFixture;

  protected XmlParsingTestBase(@NotNull String dataPath, @NotNull String fileExt, ParserDefinition @NotNull ... definitions) {
    parsingFixture = parsingFixture(projectFixture, getTestDataPath() + "/" + dataPath, fileExt, definitions);
  }

  protected String getTestDataPath() {
    return getXmlParsingTestDataPath();
  }

  private static String getXmlParsingTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  /**
   * The {@link EmbeddedTokenTypesProvider} extensions of the test. The other extensions of the application are masked.
   */
  protected @NotNull List<EmbeddedTokenTypesProvider> getEmbeddedTokenTypesProviders() {
    return List.of();
  }

  /**
   * The {@link HtmlEmbeddedContentSupport} extensions of the test. The other extensions of the application are masked.
   */
  protected @NotNull List<HtmlEmbeddedContentSupport> getEmbeddedContentSupports() {
    return List.of();
  }

  @BeforeEach
  void setUpXmlParsing() {
    ExtensionTestUtil.maskExtensions(EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME, getEmbeddedTokenTypesProviders(),
                                     getTestRootDisposable());
    ExtensionTestUtil.maskExtensions(HtmlEmbeddedContentSupport.EP_NAME, getEmbeddedContentSupports(), getTestRootDisposable());
  }

  protected final @NotNull ParsingTestFixture getFixture() {
    return parsingFixture.get();
  }

  protected final @NotNull Disposable getTestRootDisposable() {
    return getFixture().getTestRootDisposable();
  }

  protected final void registerParserDefinition(@NotNull ParserDefinition definition) {
    getFixture().registerParserDefinition(definition);
  }

  protected final void configureFromParserDefinition(@NotNull ParserDefinition definition, @NotNull String extension) {
    getFixture().configureFromParserDefinition(definition, extension);
  }

  protected final <T> void addExplicitExtension(@NotNull LanguageExtension<T> collector, @NotNull Language language, @NotNull T object) {
    getFixture().addExplicitExtension(collector, language, object);
  }

  protected final PsiFile createFile(@NotNull String name, @NotNull String text) {
    return getFixture().createFile(name, text);
  }

  protected final PsiFile createPsiFile(@NotNull String name, @NotNull String text) {
    return getFixture().createPsiFile(name, text);
  }

  protected final @NotNull String loadFile(@NotNull String name) {
    return getFixture().loadFile(name);
  }

  protected final void doReparseTest(@NotNull String textBefore, @NotNull String textAfter) {
    getFixture().doReparseTest(textBefore, textAfter);
  }

  protected void checkResult(@NotNull String targetDataName, @NotNull PsiFile file) {
    checkResult(getFixture().getFullDataPath(), targetDataName, file);
  }

  protected void checkResult(@NotNull String fullDataPath, @NotNull String targetDataName, @NotNull PsiFile file) {
    getFixture().checkResult(fullDataPath, targetDataName, file);
  }

  protected void doTestXml(@NonNls String text) throws Exception {
    getFixture().setLanguage(XMLLanguage.INSTANCE);
    doTest(text, "test.xml");
  }

  protected void doTest(@NonNls String text, @NonNls String fileName) throws Exception {
    PsiFile file = createFile(fileName, text);
    checkResult("test" + getFixture().getTestName(), file);
  }

  @Test
  public void testNewParsing1() throws Exception {
    doTestXml("<a></a>");
  }

  @Test
  public void testEmptyCommentParsing() throws Exception {
    doTestXml("<!---->");
  }

  @Test
  public void testNewParsing2() throws Exception {
    doTestXml("<a>   </a>");
  }

  @Test
  public void testNewParsing3() throws Exception {
    doTestXml("<a   </a>");
  }

  @Test
  public void testNewParsing4() throws Exception {
    doTestXml("<a> <b> </a>");
  }

  @Test
  public void testNewParsing5() throws Exception {
    doTestXml("<a> <b/> </a>");
  }

  @Test
  public void testNewParsing6() throws Exception {
    doTestXml("<!DOCTYPE greeting SYSTEM \"hello.dtd\" [<!ELEMENT greeting EMPTY>]> <a> <b/> </a>");
  }

  @Test
  public void testNewParsing7() throws Exception {
    doTestXml("<a> <b> <c> <d> </a>");
  }

  @Test
  public void testNewParsing8() throws Exception {
    doTestXml("<a blah=\"blah\"/>");
  }

  @Test
  public void testNewParsing9() throws Exception {
    doTestXml("<a asd=\"asd\"></a>");
  }

  @Test
  public void testNewParsing10() throws Exception {
    doTestXml("<a asd=\"asd\"> <s> </a>");
  }

  @Test
  public void testNewParsing11() throws Exception {
    doTestXml("<!-- <!-- --> <a asd=\"asd\"> <s> </a>");
  }

  @Test
  public void testNewParsing12() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("<a><a ajsdg<a></a></a>");
  }

  @Test
  public void testNewParsing13() throws Exception {
    doTestXml("""
                <a><b><c>
                xxx\s
                xxxx
                <</b></a>""");
  }

  @Test
  public void testNewParsing14() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("<a><a ajsdg = \"\"></a></a>");
  }

  @Test
  public void testNewParsing15() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("<!DOCTYPE a [<!ELEMENT a (a)>]> <a><a ajsdg = \"\"></a></a>");
  }

  @Test
  public void testNewParsing19() throws Exception {
    doTestXml(
      "<!DOCTYPE aaa [" + "<!ELEMENT a #EMPTY>" + "<!ATTLIST a" + " xx CDATA #IMPLIED" + " yy  #IMPLIED" + " zz CDATA #IMPLIED>]>\n" +
      "<a> <b> </b> </a>");
  }

  @Test
  public void testNewParsing20() throws Exception {
    doTestXml("""
                <!DOCTYPE root [
                <!
                ]>
                <root>
                
                </root>""");
  }

  @Test
  public void testEntityInAttr() throws Exception {
    doTestXml("<a href=\"&n\"/>");
  }

  @Test
  public void testEntityInContent() throws Exception {
    doTestXml("<a> &n </a>");
  }

  @Test
  public void testMultyRoots() throws Exception {
    doTestXml("<a href=\"\"/> <b/>");
  }

  @Test
  public void testMultyRootsWithErrorsBetween() throws Exception {
    doTestXml("<a href=\"\"/> </ss><b/>");
  }

  @Test
  public void testGtInTagContent() throws Exception {
    doTestXml("<a>></a>");
  }

  @Test
  public void testManyErrors() throws Exception {
    doTestXml(loadFile("manyErrors.xml"));
  }

  private static void transformAllChildren(final ASTNode file, long version) {
    for (TreeElement child = ((TreeElement)file).getFirstChildNodeVersioned(version); child != null; child = child.getTreeNextVersioned(version)) {
      transformAllChildren(child, version);
    }
  }

  @Test
  @PerformanceUnitTest
  public void testPerformance1() {
    //noinspection SpellCheckingInspection
    doTestPerformance("pallada.xml");
  }

  @Test
  @PerformanceUnitTest
  public void testPerformance2() {
    doTestPerformance("performance2.xml");
  }

  private void doTestPerformance(String fileName) {
    final String text = ParsingTestFixture.loadFileDefault(getXmlParsingTestDataPath() + "psi/xml", fileName);
    long start = System.nanoTime();
    final PsiFile file = createFile(fileName, text);
    ReadAction.runBlocking(() -> {
      long version1 = InternalPsiVersioning.getCurrentPsiVersion();
      transformAllChildren(file.getNode(), version1);
    });
    LOG.debug("First parsing took " + (System.nanoTime() - start) + "ns");

    var perfTest = Benchmark.newBenchmark("XML Parser Performance on " + fileName, () -> {
      for (int i = 0; i < 10; i++) {
        PsiFile next = createPsiFile("test" + i, text);
        ReadAction.runBlocking(() -> {
          long version2 = InternalPsiVersioning.getCurrentPsiVersion();
          transformAllChildren(next.getNode(), version2);
        });
      }
    });

    perfTest.runAsStressTest()
      .setup(() -> WriteAction.runAndWait(() -> PsiManager.getInstance(getFixture().getProject()).dropPsiCaches()))
      .startAsSubtest(this.getClass().getSimpleName() + " " + perfTest.getLaunchName());

    int count = ReadAction.computeBlocking(() -> {
      LeafElement firstLeaf = TreeUtil.findFirstLeaf(file.getNode());
      int leafCount = 0;
      do {
        leafCount++;
      }
      while ((firstLeaf = TreeUtil.nextLeaf(firstLeaf, null)) != null);
      return leafCount;
    });
    LOG.debug("For " + count + " lexemes");
  }

  @Test
  public void testXmlDecl() throws Exception {
    doTestXml("<?xml version=\"1.0\" encoding='cp1251' ?>");
  }

  @Test
  public void testXmlDecl2() throws Exception {
    doTestXml("<?xml version=\"1.0\" encoding='cp1251' ?> <foo></foo>");
  }

  @Test
  public void testXmlDecl3() throws Exception {
    doTestXml("<?xml version=\"1.0\" encoding='cp1251' > <foo></foo>");
  }

  @Test
  public void testDoctype1() throws Exception {
    doTestXml("<!DOCTYPE greeting SYSTEM \"hello.dtd\">");
  }

  @Test
  public void testDoctype2() throws Exception {
    doTestXml("<!DOCTYPE toc " + "PUBLIC \"-//Sun Microsystems Inc.//DTD JavaHelp TOC Version 1.0//EN\" " +
              "\"http://java.sun.com/products/javahelp/toc_1_0.dtd\">");
  }

  @Test
  public void testDoctype3() throws Exception {
    doTestXml("<!DOCTYPE greeting [" + "<!ELEMENT greeting (#PCDATA)>" + "]>");
  }

  @Test
  public void testDoctype4() throws Exception {
    doTestXml("<!DOCTYPE greeting [" + "<!ELEMENT br EMPTY>" + "]>");
  }

  @Test
  public void testDoctype5() throws Exception {
    doTestXml("<!DOCTYPE greeting [" + "<!ELEMENT p (#PCDATA|a|ul|b|i|em)*>" + "]>");
  }

  @Test
  public void testDoctype6() throws Exception {
    doTestXml("<!DOCTYPE greeting [" + "<!ELEMENT %name.para; %content.para; >" + "]>");
  }

  @Test
  public void testDoctype7() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("<?xml version='1.0' encoding='ISO-8859-1' ?>" + "<!DOCTYPE toc SYSTEM 'dtds/ejb-jar_2_0.dtd'>" + "<test>" + "</test>");
  }

  @Test
  public void testComment1() throws Exception {
    doTestXml("<!-- declarations for <head> & <body> -->");
  }

  @Test
  public void testComment3() throws Exception {
    doTestXml("<a><!--<b>--></a>");
  }

  @Test
  public void testElements1() throws Exception {
    doTestXml("<a></a>");
  }

  @Test
  public void testElements2() throws Exception {
    doTestXml("<a/>");
  }

  @Test
  public void testElements3() throws Exception {
    doTestXml("<a><b><c/><d/></b><e/><f><g/></f></a>");
  }

  @Test
  public void testElements4() throws Exception {
    doTestXml("""
                <project name="IDEA_ZKM">
                <!-- set global properties for this build -->
                <property value="off" />
                </project>
                """);
  }

  @Test
  public void testElements5() throws Exception {
    doTestXml("<ns1:a><ns2:b/></ns1:a>");
  }

  @Test
  public void testElements6() throws Exception {
    doTestXml("<!DOCTYPE project [" + "<!ENTITY targets SYSTEM \"file:../myproject/targets.xml\">" + "]>" + "<project>" + "</project>");
  }

  @Test
  public void testAttributes1() throws Exception {
    doTestXml("<a att1='val1' att2=\"val2\"></a>");
  }

  @Test
  public void testAttributes2() throws Exception {
    doTestXml("<a><b c=\"\" > </b> </a>");
  }

  @Test
  public void testAttributes3() throws Exception {
    doTestXml("<a><b c=\"a, b, c\" > </b> </a>");
  }

  @Test
  public void testAttributes4() throws Exception {
    doTestXml("<a><b c=\"a  b\" > </b> </a>");
  }

  @Test
  public void testAttributes5() throws Exception {
    doTestXml("<a><b c=\"a\"d=\"\" > </b> </a>");
  }

  @Test
  public void testCharacters1() throws Exception {
    doTestXml("<a>someChar\nData</a>");
  }

  @Test
  public void testEditing1() throws Exception {
    doTestXml("<<a></a>");
  }

  @Test
  public void testEditing2() throws Exception {
    doTestXml("<a><b/> < <c/></a>");
  }

  @Test
  public void testEditing3() throws Exception {
    doTestXml("<a><b <c/></a>");
  }

  @Test
  public void testEditing4() throws Exception {
    doTestXml("<a><b>< <c/></a>");
  }

  @Test
  public void testEditing5() throws Exception {
    doTestXml("<a><b></ <c/></a>");
  }

  @Test
  public void testEditing6() throws Exception {
    doTestXml("<one>     <two ,b\"/></one>");
  }

  @Test
  public void testEditing7() throws Exception {
    doTestXml("<one>     <two a,b\"/></one>");
  }

  @Test
  public void testEditing8() throws Exception {
    doTestXml("<one>     <two a b=\"\"/></one>");
  }

  @Test
  public void testEditing9() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("<one> <two a b=\"\"> ashdgjkasgd <aksjhdk></two></one>");
  }


  @Test
  public void testCdata1() throws Exception {
    doTestXml("<a><![CDATA[<greeting>Hello, world!</greeting>]]></a>");
  }

  @Test
  public void testCdata2() throws Exception {
    doTestXml("<a><![CDATA[someData <!--</greeting>--> more data]]></a>");
  }

  @Test
  public void testEntityRef1() throws Exception {
    doTestXml("<a>%types;</a>");
  }

  @Test
  public void testEntityRef4() throws Exception {
    doTestXml("<a b=\"aa &gt; bb\" />");
  }

  @Test
  public void testUnbalanced1() throws Exception {
    doTestXml("<a><b></a>");
  }

  @Test
  public void testUnbalanced2() throws Exception {
    doTestXml("<a><b><c></a>");
  }

  @Test
  public void testUnbalanced3() throws Exception {
    doTestXml("<a><b></bb></a>");
  }

  @Test
  public void testUnbalanced4() throws Exception {
    doTestXml("<a><b><b/></c></a>");
  }

  @Test
  public void testDtdUrl1() {
    XmlFile file = (XmlFile)createFile("test.xml", "<!DOCTYPE greeting SYSTEM \"hello.dtd\">");

    String url = ReadAction.computeBlocking(() -> {
      XmlDocument document = file.getDocument();
      XmlProlog prolog = document.getProlog();
      XmlDoctype doctype = prolog.getDoctype();
      return doctype.getDtdUri();
    });
    assertEquals("hello.dtd", url, "testDtdUrl1");
  }

  @Test
  public void testContent1() {
    XmlFile file = (XmlFile)createFile("test.xml", "<a>   \nxxx   \n</a>");
    checkResult("testContent1", file);
    assertEquals("xxx", ReadAction.computeBlocking(() -> file.getDocument().getRootTag().getValue().getTrimmedText()));
  }

  @Test
  public void testUnopenedTag1() throws Exception {
    doTestXml("</foo>");
  }

  @Test
  public void testUnclosedTag() {
    XmlFile file = (XmlFile)createFile("test.xml", "<a>xxx");
    checkResult("testUnclosedTag", file);
    assertEquals("xxx", ReadAction.computeBlocking(() -> file.getDocument().getRootTag().getValue().getText()));
  }

  @Test
  public void testUnclosedTag2() throws Exception {
    doTestXml("<a");
  }

  @Test
  public void testMissingClosingTagName() throws Exception {
    doTestXml("<a></>");
  }

  @Test
  public void testProcessingInstruction1() throws Exception {
    doTestXml("<?This is=\"PI\"?>");
  }

  @Test
  public void testProcessingInstruction2() throws Exception {
    doTestXml("<a><?This is=\"PI\"?></a>");
  }

  @Test
  public void testProcessingInstruction3() throws Exception {
    doTestXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<?xml-stylesheet type=\"text/xsl\" href=\"document.xsl\"?>" +
              "<!-- @(#) $Id$ -->" + "<!DOCTYPE api SYSTEM \"document.dtd\">" + "<api>" + "</api>");
  }

  @Test
  public void testCharRef1() throws Exception {
    doTestXml("<a>&#123;</a>");
  }

  @Test
  public void testCharRef2() throws Exception {
    doTestXml("<a>&#x123;</a>");
  }

  @Test
  public void testCharRef3() throws Exception {
    doTestXml("&#123;");
  }

  @Test
  public void testCharRef4() throws Exception {
    doTestXml("&#xaBcD123;");
  }

  @Test
  public void testCharRef5() throws Exception {
    doTestXml("<a attr=\"abc&#123;\"/>");
  }

  @Test
  public void testColonName() throws Exception {
    doTestXml("<:foo/>");
  }

  @Test
  public void testNotation2() throws Exception {
    doTestXml("<!DOCTYPE x3 [<!NOTATION data-sources SYSTEM \"x3\">]>");
  }

  @Test
  public void testWhitespaceBeforeName() throws Exception {
    doTestXml("<a>< a</a>");
  }

  @Test
  public void testAllWhitespaces() throws Exception {
    //noinspection SpellCheckingInspection
    doTestXml("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Root><page><content><locatieblok><locatie label="Locatie">EXAMPLE</locatie>\u2029<straat label="Straat">EXAMPLE</straat>\u2029<postcode label="Postcode">EXAMPLE</postcode> <plaats label="Plaats">EXAMPLE</plaats>\u2029\u2029<telomschrijving label="Telefoon omschrijving">T.</telomschrijving> <telefoon label="Telefoon">EXAMPLE</telefoon>\u2029\u2029<internet label="Internet">EXAMPLE</internet></locatieblok><naamblok><aanhefnaam label="Aanhef Naam Achternaam">Aanhef Naam Achternaam</aanhefnaam>\u2029<functie label="Functie">Functie</functie>\u2029<mobielomschr label="Mobiel omschrijving">M.</mobielomschr>\t<mobiel label="Mobiel">EXAMPLE</mobiel>\u2029<emailomschr label="Email omschrijving">E.</emailomschr>\t<email label="Email">EXAMPLE</email></naamblok></content></page></Root>
                """);
  }

  @Test
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
    getFixture().setLanguage(HTMLLanguage.INSTANCE);
    doTest("<script type=\"application/custom\">Custom Script</script>", "test.html");
  }

  static class MyLanguage extends Language implements InjectableLanguage {
    protected MyLanguage() {
      super("MyLanguage", "application/custom");
    }
  }
}
