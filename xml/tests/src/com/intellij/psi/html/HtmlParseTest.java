package com.intellij.psi.html;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.idea.Bombed;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Calendar;

public class HtmlParseTest extends LightIdeaTestCase {
  private static final String BASE_PATH = "/xml/tests/testData/psi/old/html/";

  public void testSimpleParse() throws Exception {
    String result = getTreeTextByText("<html></html>");
    assertResult("simple.txt",result);

    result = getTreeTextByText("<html><body><hr></body></html>");
    assertResult("SingleTag.txt",result);

    result = getTreeTextByText("<html><body><hr><hr style1=\"a:hover blue\"></body></html>");
    assertResult("TwoSingleTags.txt",result);

    result = getTreeTextByText("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Frameset//EN\"\n" +
                               "        \"http://www.w3.org/TR/html4/frameset.dtd\"><html lang=\"ru\"></html>");
    assertResult("DocumentWithDocType.txt",result);

    result = getTreeTextByText("<!doctype html public \"-//W3C//DTD HTML 4.01 Frameset//EN\"\n" +
                               "        \"http://www.w3.org/TR/html4/frameset.dtd\"><html lang=\"ru\"></html>");
    assertResult("DocumentWithDocType2.txt",result);

    result = getTreeTextByText("<tag>\n" +
                               "    <subtag>\n" +
                               "        Value\n" +
                               "    </subtag>\n" +
                               "    <emptyTag attr=\"\"/>\n" +
                               "</tag>");
    assertResult("Tags.txt",result);

    result = getTreeTextByText("<table>\n" +
                               "    <tr>\n" +
                               "        <td nowrap></td>\n" +
                               "    </tr>\n" +
                               "</table>");
    assertResult("AttributeWithoutValue.txt",result);

    result = getTreeTextByText("<table id=a>\n" +
                               "    <tr>\n" +
                               "        <td nowrap></td>\n" +
                               "    </tr>\n" +
                               "</table>");
    assertResult("AttributeWithoutQuotes.txt",result);

    result = getTreeTextByText("<h1>c<br><span>a</span></h1>");
    assertResult("TagAfterNonClosedTag.txt",result);

    result = getTreeTextByText("<tr><td>AAAA<td>BBB<td>CCC</tr>");
    assertResult("OptionalEndTagInBlockTag.txt",result);

    result = getTreeTextByText("<head><body></body>");
    assertResult("OptionalEndTagEndedByBlockTag.txt",result);

    result = getTreeTextByText("<DL>\n" +
                               "<DT><A HREF=\"aaa\"><B>PROHIBITED</B></A> - \n" +
                               "Static variable in class ssh2.<A HREF=\"a.html\" title=\"class in ssh2.\">ChannelOpenException</A>\n" +
                               "<DD>The administrator does not permit this channel to be opened\n" +
                               "<DT><A HREF=\"A.html\" title=\"class in sshtools.\"><B>QQQ</B></A> - cipher.<A HREF=\"sshtools.QQQ.html\" title=\"class in sshtools.\">WWW</A>.<DD>cipher API.<DT><A HREF=\"QQQ.html#QQQ()\"><B>QQQ()</B></A> - \n" +
                               "Constructor for class sshtools.<A HREF=\"QQ.html\" title=\"class in sshtools\">QQ</A>\n" +
                               "<DD>&nbsp;\n" +
                               "</DL>");
    assertResult("OptionalEndTagEndedByBlockTag2.txt",result);

    result = getTreeTextByText("<body></BODY>");
    assertResult("DifferentCaseInTagStartAndEnd.txt",result);

    result = getTreeTextByText("<DIV><P><P><P></DIV>");
    assertResult("ManyPs.txt",result);

    result = getTreeTextByText("<CENTER>\n" +
                               "<A NAME=\"TOP\"></a>\n" +
                               "<A HREF=\"http://www.jflex.de\"><IMG SRC=\"logo.gif\" BORDER=0 HEIGHT=223 WIDTH=577></a></CENTER>");
    assertResult("ManyAs.txt",result);

    result = getTreeTextByText("<FRAMESET rows=50,* ></FRAMESET>");
    assertResult("ComplexUnquotedAttr.txt",result);

    result = getTreeTextByText("<P><P><P><P><br><br><h2></h2><div></div>");
    assertResult("ComplexPs.txt",result);

    result = getTreeTextByText("<HTML>\n" +
                               "<BODY>\n" +
                               "<P>\n" +
                               "<BR><HR><H4>Footnotes</H4>\n" +
                               "<BR><HR>\n" +
                               "<ADDRESS>\n" +
                               "Mon Apr 12 20:58:12 EST 2004, <a href=\"http://www.doclsf.de\">Gerwin Klein</a>\n" +
                               "</ADDRESS>\n" +
                               "</BODY>\n" +
                               "</HTML>");
    assertResult("BRHR.txt",result);

    result = getTreeTextByText("<UL>\n" +
                               "<LI><A NAME=\"tex2html81\"\n" +
                               "  HREF=\"manual.html#SECTION00020000000000000000\">Introduction</A>\n" +
                               "<UL>\n" +
                               "<LI><A NAME=\"tex2html82\"\n" +
                               "  HREF=\"manual.html#SECTION00021000000000000000\">Design goals</A>\n" +
                               "</UL>\n" +
                               "<LI><A NAME=\"tex2html84\"\n" +
                               "  HREF=\"manual.html#SECTION00030000000000000000\">Installing and Running JFlex</A>\n" +
                               "</UL>");
    assertResult("List.txt",result);

    result = getTreeTextByText("<HTML>\n" +
                               "<BODY>\n" +
                               "<P>\n" +
                               "<CENTER>\n" +
                               "<A NAME=\"TOP\"></a>\n" +
                               "</CENTER>\n" +
                               "<P>\n" +
                               "<DIV><I>Copyright<BR></I></DIV>\n" +
                               "</BODY>\n" +
                               "</HTML>");
    assertResult("WronglyBalancedOutOfChain.txt",result);

    result = getTreeTextByText("<html>\n" +
                               "<body bgcolor=\"#FFFFFF\">\n" +
                               "\n" +
                               "<h2>Changes to build 3075</h2>\n" +
                               "<br><br>\n" +
                               "\n" +
                               "<h3>Import Eclipse projects</h3>\n" +
                               "\n" +
                               "&nbsp;&nbsp;&nbsp;<img src=\"images/3075_eclipse.gif\"><br><br>\n" +
                               "\n" +
                               "<p>Now you can import Eclipse projects into IDEA. </p>\n" +
                               "\n" +
                               "<h3>J2ME Support</h3>\n" +
                               "\n" +
                               "<p>\n" +
                               "    Features enhancing work with CSS:\n" +
                               "    <ul>\n" +
                               "        <li>aaa</li>\n" +
                               "    </ul>\n" +
                               "</p>\n" +
                               "\n" +
                               "<hr>\n" +
                               "</body>\n" +
                               "</html>");
    assertResult("BadBadBR.txt",result);

    @NonNls String s = "<?>\n" +
               "<?style tt = font courier>\n" +
               "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">\n" +
               "<?page break>\n" +
               "<?experiment> ... <?/experiment>\n" +
               "</html>";
    result = getTreeTextByText(s);
    assertResult("ToleratingHtmlPIs.txt",result);

    s = "<html>&nbsp;</html>";
    result = getTreeTextByText(s);
    assertResult("CharEntityRef.txt",result);
    
    s = "<html>&#xA0;&#XA0;</html>";
    result = getTreeTextByText(s);
    assertResult("CharRef.txt",result);

    s = "<style type=text/css />";
    result = getTreeTextByText(s);
    assertResult("SlashInUnquotedAttrValue.txt",result);

    s = "<ul><li>xxx<li>xxx<li>xxx</ul>";
    result = getTreeTextByText(s);
    assertResult("ManyLis.txt",result);
  }

  public void testWellFormedBlockTags() throws Exception {
    @NonNls String s = "<html><h3>\n" + "<center>\n" + "<font color=\"red\">\n" + "There appears to be a problem\n" + "</font>\n" + "</center>\n" + "</h3></html>";
    String result = getTreeTextByText(s);
    assertResult("WellFormedBlockLevelTags.txt",result);
  }

  public void testCustomFile() throws Exception {
    String s = getTreeTextByFile("contact.html");
    assertResult("CustomFile.txt",s);
  }

  public void testConditionalComments() throws Exception {
    String s = getTreeTextByFile("conditional.html");
    assertResult("conditional.txt",s);
  }

  public void testErrorParse() throws Exception {
    String result = getTreeTextByText("<tag>\n" +
                                      "    <subtag>\n" +
                                      "        Value\n" +
                                      "    <!--</subtag>-->\n" +
                                      "    <emptyTag attr=\"\"/>\n" +
                                      "</tag>");
    assertResult("MissingEndTag.txt",result);

    result = getTreeTextByText("<tag>\n" +
                               "<!--    <subtag>-->\n" +
                               "        Value\n" +
                               "    </subtag>\n" +
                               "    <emptyTag attr=\"\"/>\n" +
                               "</tag>");
    assertResult("MissingStartTag.txt",result);

    result = getTreeTextByText("<tag>\n" +
                               "<a href=\"\"\n"+
                               "</tag>");
    assertResult("NoEndTagWithAttribute.txt",result);

    result = getTreeTextByText("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n" +
                               "<html></html>");
    assertResult("DocumentWithDocType3.txt",result);

    @NonNls String s = "<html>\n" +
               "<body>\n" +
               "<H2>\n" +
               "<FONT SIZE=\"-1\">\n" +
               "com.sshtools.cipher</FONT>\n" +
               "<BR>\n" +
               "Class AES128Cbc</H2>\n" +
               "<PRE>\n" +
               "java.lang.Object\n" +
               "  <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\">com.maverick.ssh.cipher.SshCipher\n" +
               "      <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\">com.maverick.ssh.crypto.engines.CbcBlockCipher\n" +
               "          <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\"><B>com.sshtools.cipher.AES128Cbc</B>\n" +
               "</PRE>\n" +
               "<HR>\n" +
               "<DL>\n" +
               "<DT>public class <B>AES128Cbc</B><DT>extends com.maverick.ssh.crypto.engines.CbcBlockCipher</DL>\n" +
               "\n" +
               "<P>\n" +
               "This cipher can optionally be added to the J2SSH Maverick API. To add\n" +
               " the ciphers from this package simply add them to the <A HREF=\"../../../com/maverick/ssh2/Ssh2Context.html\" title=\"class in com.maverick.ssh2\"><CODE>Ssh2Context</CODE></A>\n" +
               " <blockquote><pre>\n" +
               "  import com.sshtools.cipher.*;\n" +
               "\n" +
               " </pre></blockquote>\n" +
               "<P>\n" +
               "\n" +
               "<P>\n" +
               "<DL>\n" +
               "<DT><B>Version:</B></DT>\n" +
               "  <DD>Revision: 1.20</DD>\n" +
               "</DL>\n" +
               "<HR>\n" +
               "</body>\n" +
               "</html>";

    result = getTreeTextByText(s);
    assertResult("WronglyNestedPs.txt",result);

    s = "<?xml version=\"1.0\"?>\n" +
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\"\n" +
        "          \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n" +
        "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\"></html>";
    result = getTreeTextByText(s);
    assertResult("ToleratingPIs.txt",result);

    s = "<html>\n" +
        "<body>\n" +
        "<br>\n" +
        "<br>\n" +
        "<br>\n" +
        "<ul>\n" +
        "<li>JUnit is a regression test framework.</li><br><br>\n" +
        "</ul>\n" +
        "</ul>\n" +
        "</body>\n" +
        "</html>";
    result = getTreeTextByText(s);
    assertResult("ToleratingMissingTagStart.txt",result);

    s = "<pre><code>\n" +
        "<font class=\"detailedtext\">public class</font> SortTest <font class=\"detailedtext\">implements</font> Test {\n" +
        "  <font class=\"detailedtext\">public int</font> countTestCases() {\n" +
        "    <font class=\"detailedtext\">return</font> 0;\n" +
        "  }\n" +
        "\n" +
        "  <font class=\"detailedtext\">public void</font> run(TestResult testResult) {\n" +
        "    File testData = <font class=\"detailedtext\">new</font> File(\".\");\n" +
        "    String[] names = FSUtil.findTests(testData);\n" +
        "    <font class=\"detailedtext\">for</font> (<font class=\"detailedtext\">int</font> i = 0; i < names.length; i++) {\n" +
        "      String name = names[i];\n" +
        "      <font class=\"detailedtext\">new</font> MyTestCase(name, testData){}.run(testResult);\n" +
        "    }\n" +
        "  }\n" +
        "\n" +
        "  <font class=\"detailedtext\">public static</font> Test suite() {\n" +
        "    <font class=\"detailedtext\">return new</font> SortTest();\n" +
        "  }\n" +
        "}\n" +
        "    </code></pre>";
    result = getTreeTextByText(s);
    assertResult("GreaterInTagValue.txt",result);

    s = "<html></html><html></html>";
    result = getTreeTextByText(s);
    assertResult("TwoRootTags.txt",result);

    s = "<html>\n" +
        "<body>\n" +
        "<hr>\n" +
        "<table>\n" +
        "\t<tr>\n" +
        "\t\t<td>\n" +
        "\t\t<br>\n" +
        "\t\t<table>\n" +
        "\t\t\t<tr>\n" +
        "\t\t\t\t<td><nobr>Ctrl + <b>W</nobr></b></td>\n" +
        "\t\t\t</tr>\n" +
        "\t\t</table><br>\n" +
        "\t\t<br>\n" +
        "\t\t<table>\n" +
        "\t\t\t<tr>\n" +
        "\t\t\t\t<td><b><nobr>Ctrl + Numpad/</b></nobr></td>\n" +
        "\t\t\t</tr><tr>\n" +
        "\t\t\t\t<td><b><nobr>Ctrl + Numpad+</b></nobr></td>\n" +
        "\t\t\t</tr>\n" +
        "\t\t</table><br>\n" +
        "\t\t<br>\t\t\n" +
        "\t\t<br>\n" +
        "\t\t</td>\n" +
        "\t</tr>\n" +
        "</table>\n" +
        "<hr>\n" +
        "</body>\n" +
        "</html>";
    result = getTreeTextByText(s);
    assertResult("SeveralMisplacedTags.txt",result);

    s = "<impact:validateErrors/>\n" +
        "<impact:validateForm name=\"myform\"\n" +
        "     method=\"post\"\n" +
        "     action=\"myaction\"\n" +
        "     schema=\"/WEB-INF/Constraints.xsd\">\n" +
        "<impact:validateInput type=\"hidden\" name=\"cReqFuncCd\" editableMode=\"aaa\" />\n" +
        "\n" +
        "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"3\" class=\"tableBorder\" border=\"0\">\n" +
        "<th> Security Profiles </th>\n" +
        "</table>\n" +
        "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"3\" class=\"tableBorder\" border=\"0\">\n" +
        "  <tr valign=\"top\">\n" +
        "    <td width=\"33%\" >\n" +
        "    <impact:validateInput editableMode=\"\" name=\"rbSecurityProfileRadioIndex\" type=\"radio\" disabled=\"\" value=\"\" tabIndex=\"\"/><a href=\"javascript:submitFormToSecurityDetailWindow( ' ', 'U')\">something</a>\n" +
        "    </td>\n" +
        "  </tr>\n" +
        //"  <tr><!-- Delete this line to cause the error. -->\n" +
        "       <td width=\"33%\" >\n" +
        "       &nbsp;\n" +
        "       </td>\n" +
        "  </tr>\n" +
        "</table>\n" +
        "<input type=\"hidden\" name=\"aaa\">\n" +
        "</impact:validateForm>";

    result = getTreeTextByText(s);
    assertResult("BalancingWithCustomTags.txt",result);

    result = getTreeTextByText("<!-- My first empty html document! -->");
    assertResult("EmptyDocument.txt",result);

    result = getTreeTextByText("&nbsp; some text");
    assertResult("JustTextAndEntityRef.txt",result);

    result = getTreeTextByText("<?xml version='1.0'?>\n  ");
    assertResult("JustPI.txt",result);
    
    result = getTreeTextByText("\n<p>");
    assertResult("JustP.txt",result);

    result = getTreeTextByText("<p>\naaa\n<div id=\"aaa\"/\n</p>");
    assertResult("UnclosedTag.txt",result);

    result = getTreeTextByText("<p>\naaa\n<div id=\"aaa\"<\n</p>");
    assertResult("UnclosedTag2.txt",result);

    // slightly incorrect tree due to tag balancing!
    result = getTreeTextByText("<p>\naaa\n<div id=\"\n</p>");
    assertResult("UnclosedTag3.txt",result);

    result = getTreeTextByText("<p><a aaa</p>");
    assertResult("UnclosedTag4.txt",result);
  }

  public void testCustomFile2() throws Exception {
    String result = getTreeTextByFile("IDEADEV-6816.html");
    assertResult("IDEADEV-6816.txt",result);
  }

  public void testCustomFile3() throws Throwable {
    String result = getTreeTextByFile("IDEA-4597.html");
    assertResult("IDEA-4597.txt",result);
  }

  public void testCustomFile4() throws Exception {
    String result = getTreeTextByFile("IDEADEV-13898.html");
    assertResult("IDEADEV-13898.txt",result);
  }

  public void testCustomFile5() throws Exception {
    String result = getTreeTextByFile("WEB-8731.html");
    assertResult("WEB-8731.txt", result);
  }

  public void testCustomFile6() throws Exception {
    String result = getTreeTextByFile("WEB-11115.html");
    assertResult("WEB-11115.txt", result);
  }

  public void testForbiddenEnd() throws Exception {
    String result = getTreeTextByFile("WEB-6626.html");
    assertResult("WEB-6626.txt", result);
  }

  public void testAutoCloseBy() throws Exception {
    String result = getTreeTextByFile("WEB-2411.html");
    assertResult("WEB-2411.txt", result);

    result = getTreeTextByFile("WEB-2414.html");
    assertResult("WEB-2414.txt", result);

    result = getTreeTextByFile("th.html");
    assertResult("th.txt", result);
  }

  public void testWeb11061() throws Exception {
    String result = getTreeTextByFile("WEB-11061.html");
    assertResult("WEB-11061.txt", result);
  }

  public void testWeb11032() throws Exception {
    String result = getTreeTextByFile("WEB-11032.html");
    assertResult("WEB-11032.txt", result);
  }

  public void testWeb14928() throws Exception {
    String result = getTreeTextByFile("WEB-14928.html");
    assertResult("WEB-14928.txt", result);
  }

  public void testWeb15932() throws Exception {
    String result = getTreeTextByFile("WEB-15932.html");
    assertResult("WEB-15932.txt", result);
  }

  public void testWeb17192() throws Exception {
    String result = getTreeTextByFile("WEB-17192.html");
    assertResult("WEB-17192.txt", result);
  }

  public void testWeb17998() throws Exception {
    String result = getTreeTextByFile("WEB-17998.html");
    assertResult("WEB-17998.txt", result);
  }

  public void testWeb19451() throws Exception {
    String result = getTreeTextByFile("WEB-19451.html");
    assertResult("WEB-19451.txt", result);
  }

  public void testWeb27570() throws Exception {
    String result = getTreeTextByFile("WEB-27570.html");
    assertResult("WEB-27570.txt", result);
  }

  public void testWeb28136() throws Exception {
    String result = getTreeTextByFile("WEB-28136.html");
    assertResult("WEB-28136.txt", result);
  }

  public void testAngular2Attributes() throws Exception {
    String result = getTreeTextByFile("Angular2.html");
    assertResult("Angular2.txt", result);
  }

  public void testParsePerformance() throws Exception {
    final Ref<String> result = Ref.create();
    PlatformTestUtil.startPerformanceTest("Parsing", 500, () -> result.set(getTreeTextByFile("index-all.html"))).assertTiming();
    assertResult("Performance.txt", result.get());
  }

  private String getTreeTextByFile(@NonNls String filename) throws Exception {
    return getTreeTextByText(loadFile(filename));
  }

  private String getTreeTextByText(@NonNls String text) throws IncorrectOperationException {
    PsiFile fileFromText = PsiFileFactory.getInstance(getProject()).createFileFromText("test.html", HtmlFileType.INSTANCE, text);
    assertEquals("trees should be equals",text,fileFromText.getText());
    return DebugUtil.psiTreeToString(fileFromText, false).trim();
  }
  
  private static void assertResult(@NonNls String targetDataName, final String text) throws Exception {
    try{
      String expectedText = loadFile(targetDataName);
      assertEquals(expectedText, text);
    }
    catch(FileNotFoundException e){
      String fullName = fullName(targetDataName);
      FileUtil.writeToFile(new File(fullName), text);
      fail("No output file found. Created "+fullName);
    }
  }

  protected static String loadFile(String name) throws Exception {
    String fullName = fullName(name);
    String text = FileUtil.loadFile(new File(fullName)).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }

  @NotNull
  private static String fullName(String name) {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + BASE_PATH + name;
  }

  private void getTreeTextAndAssertResult() throws Exception {
    assertResult(getTestName(false) + ".txt",  getTreeTextByFile(getTestName(false)+".html"));
  }

  public void testCommentText() throws Exception{
    getTreeTextAndAssertResult();
  }

  public void testEntityInComments() throws Exception{
    getTreeTextAndAssertResult();
  }


  public void testTagsInComment()throws Exception{
    getTreeTextAndAssertResult();
  }

  public void testSpaceAfterAttrName()throws Exception{
    getTreeTextAndAssertResult();
  }

  public void testClosedParagraphWithTagInside() throws Throwable {
    getTreeTextAndAssertResult();
  }

  public void testParserStackOverflow() throws Throwable {
    getTreeTextByFile(getTestName(false) + ".html");
  }

  public void testParserStackOverflow2() {

    Runnable runnable = () -> ApplicationManager.getApplication().runReadAction(() -> {
      try {
        getTreeTextByFile(getTestName(false) + ".html");
      } catch (Exception ex) { throw new RuntimeException(ex); }
    });

    for(int i = 0; i < 300; ++i) {
      final Runnable runnable1 = runnable;
      runnable = () -> ApplicationManager.getApplication().runReadAction(() -> {
        @SuppressWarnings("UnusedDeclaration") int a = 1;
        final Runnable run = runnable1; // ensure stack allocated for frame
        run.run();
      });
    }

    runnable.run();
  }

  public void testParserStackOverflow3() throws Throwable {
    getTreeTextByFile(getTestName(false) + ".html");
  }

  public void testScriptTag() throws Exception {
    getTreeTextAndAssertResult();
  }

  public void testDoNotSplitTextOnEntity() throws Throwable {
    getTreeTextAndAssertResult();
  }

  //public void testParseAllJDKJavadocs() throws Throwable {
  //  VirtualFile docRoot =
  //    VirtualFileManager.getInstance().findFileByUrl("jar:///System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Home/docs.jar!/");
  //
  //  assertNotNull(docRoot);
  //
  //  parseHtmls(docRoot);
  //}
  //
  //private void parseHtmls(final VirtualFile root) {
  //  if (root.isDirectory()) {
  //    for (VirtualFile child : root.getChildren()) {
  //      parseHtmls(child);
  //    }
  //  }
  //  else {
  //    final FileType ft = root.getFileType();
  //    if (ft == StdFileTypes.HTML) {
  //      System.out.println("Parsing: " + root.getPath());
  //      try {
  //        final PsiFile html = PsiManager.getInstance(getProject()).findFile(root);
  //        html.accept(new PsiRecursiveElementVisitor() {
  //        });
  //      }
  //      catch (StackOverflowError e) {
  //        System.out.println("Stack overflow on: ==================================");
  //        final CharSequence sequence = LoadTextUtil.loadText(root);
  //        System.out.println(sequence.toString());
  //        System.out.println("=================== done ===================");
  //      }
  //    }
  //  }
  //}
}
