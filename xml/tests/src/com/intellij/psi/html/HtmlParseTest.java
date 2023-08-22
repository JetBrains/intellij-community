package com.intellij.psi.html;

import com.intellij.html.embedding.HtmlEmbeddedContentSupport;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;

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

    result = getTreeTextByText("""
                                 <tag>
                                     <subtag>
                                         Value
                                     </subtag>
                                     <emptyTag attr=""/>
                                 </tag>""");
    assertResult("Tags.txt",result);

    result = getTreeTextByText("""
                                 <table>
                                     <tr>
                                         <td nowrap></td>
                                     </tr>
                                 </table>""");
    assertResult("AttributeWithoutValue.txt",result);

    result = getTreeTextByText("""
                                 <table id=a>
                                     <tr>
                                         <td nowrap></td>
                                     </tr>
                                 </table>""");
    assertResult("AttributeWithoutQuotes.txt",result);

    result = getTreeTextByText("<h1>c<br><span>a</span></h1>");
    assertResult("TagAfterNonClosedTag.txt",result);

    result = getTreeTextByText("<tr><td>AAAA<td>BBB<td>CCC</tr>");
    assertResult("OptionalEndTagInBlockTag.txt",result);

    result = getTreeTextByText("<head><body></body>");
    assertResult("OptionalEndTagEndedByBlockTag.txt",result);

    result = getTreeTextByText("""
                                 <DL>
                                 <DT><A HREF="aaa"><B>PROHIBITED</B></A> -\s
                                 Static variable in class ssh2.<A HREF="a.html" title="class in ssh2.">ChannelOpenException</A>
                                 <DD>The administrator does not permit this channel to be opened
                                 <DT><A HREF="A.html" title="class in sshtools."><B>QQQ</B></A> - cipher.<A HREF="sshtools.QQQ.html" title="class in sshtools.">WWW</A>.<DD>cipher API.<DT><A HREF="QQQ.html#QQQ()"><B>QQQ()</B></A> -\s
                                 Constructor for class sshtools.<A HREF="QQ.html" title="class in sshtools">QQ</A>
                                 <DD>&nbsp;
                                 </DL>""");
    assertResult("OptionalEndTagEndedByBlockTag2.txt",result);

    result = getTreeTextByText("<body></BODY>");
    assertResult("DifferentCaseInTagStartAndEnd.txt",result);

    result = getTreeTextByText("<DIV><P><P><P></DIV>");
    assertResult("ManyPs.txt",result);

    result = getTreeTextByText("""
                                 <CENTER>
                                 <A NAME="TOP"></a>
                                 <A HREF="http://www.jflex.de"><IMG SRC="logo.gif" BORDER=0 HEIGHT=223 WIDTH=577></a></CENTER>""");
    assertResult("ManyAs.txt",result);

    result = getTreeTextByText("<FRAMESET rows=50,* ></FRAMESET>");
    assertResult("ComplexUnquotedAttr.txt",result);

    result = getTreeTextByText("<P><P><P><P><br><br><h2></h2><div></div>");
    assertResult("ComplexPs.txt",result);

    result = getTreeTextByText("""
                                 <HTML>
                                 <BODY>
                                 <P>
                                 <BR><HR><H4>Footnotes</H4>
                                 <BR><HR>
                                 <ADDRESS>
                                 Mon Apr 12 20:58:12 EST 2004, <a href="http://www.doclsf.de">Gerwin Klein</a>
                                 </ADDRESS>
                                 </BODY>
                                 </HTML>""");
    assertResult("BRHR.txt",result);

    result = getTreeTextByText("""
                                 <UL>
                                 <LI><A NAME="tex2html81"
                                   HREF="manual.html#SECTION00020000000000000000">Introduction</A>
                                 <UL>
                                 <LI><A NAME="tex2html82"
                                   HREF="manual.html#SECTION00021000000000000000">Design goals</A>
                                 </UL>
                                 <LI><A NAME="tex2html84"
                                   HREF="manual.html#SECTION00030000000000000000">Installing and Running JFlex</A>
                                 </UL>""");
    assertResult("List.txt",result);

    result = getTreeTextByText("""
                                 <HTML>
                                 <BODY>
                                 <P>
                                 <CENTER>
                                 <A NAME="TOP"></a>
                                 </CENTER>
                                 <P>
                                 <DIV><I>Copyright<BR></I></DIV>
                                 </BODY>
                                 </HTML>""");
    assertResult("WronglyBalancedOutOfChain.txt",result);

    result = getTreeTextByText("""
                                 <html>
                                 <body bgcolor="#FFFFFF">

                                 <h2>Changes to build 3075</h2>
                                 <br><br>

                                 <h3>Import Eclipse projects</h3>

                                 &nbsp;&nbsp;&nbsp;<img src="images/3075_eclipse.gif"><br><br>

                                 <p>Now you can import Eclipse projects into IDEA. </p>

                                 <h3>J2ME Support</h3>

                                 <p>
                                     Features enhancing work with CSS:
                                     <ul>
                                         <li>aaa</li>
                                     </ul>
                                 </p>

                                 <hr>
                                 </body>
                                 </html>""");
    assertResult("BadBadBR.txt",result);

    @NonNls String s = """
      <?>
      <?style tt = font courier>
      <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
      <?page break>
      <?experiment> ... <?/experiment>
      </html>""";
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
    @NonNls String s = """
      <html><h3>
      <center>
      <font color="red">
      There appears to be a problem
      </font>
      </center>
      </h3></html>""";
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
    String result = getTreeTextByText("""
                                        <tag>
                                            <subtag>
                                                Value
                                            <!--</subtag>-->
                                            <emptyTag attr=""/>
                                        </tag>""");
    assertResult("MissingEndTag.txt",result);

    result = getTreeTextByText("""
                                 <tag>
                                 <!--    <subtag>-->
                                         Value
                                     </subtag>
                                     <emptyTag attr=""/>
                                 </tag>""");
    assertResult("MissingStartTag.txt",result);

    result = getTreeTextByText("""
                                 <tag>
                                 <a href=""
                                 </tag>""");
    assertResult("NoEndTagWithAttribute.txt",result);

    result = getTreeTextByText("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n" +
                               "<html></html>");
    assertResult("DocumentWithDocType3.txt",result);

    @NonNls String s = """
      <html>
      <body>
      <H2>
      <FONT SIZE="-1">
      com.sshtools.cipher</FONT>
      <BR>
      Class AES128Cbc</H2>
      <PRE>
      java.lang.Object
        <IMG SRC="../../../resources/inherit.gif" ALT="extended by">com.maverick.ssh.cipher.SshCipher
            <IMG SRC="../../../resources/inherit.gif" ALT="extended by">com.maverick.ssh.crypto.engines.CbcBlockCipher
                <IMG SRC="../../../resources/inherit.gif" ALT="extended by"><B>com.sshtools.cipher.AES128Cbc</B>
      </PRE>
      <HR>
      <DL>
      <DT>public class <B>AES128Cbc</B><DT>extends com.maverick.ssh.crypto.engines.CbcBlockCipher</DL>

      <P>
      This cipher can optionally be added to the J2SSH Maverick API. To add
       the ciphers from this package simply add them to the <A HREF="../../../com/maverick/ssh2/Ssh2Context.html" title="class in com.maverick.ssh2"><CODE>Ssh2Context</CODE></A>
       <blockquote><pre>
        import com.sshtools.cipher.*;

       </pre></blockquote>
      <P>

      <P>
      <DL>
      <DT><B>Version:</B></DT>
        <DD>Revision: 1.20</DD>
      </DL>
      <HR>
      </body>
      </html>""";

    result = getTreeTextByText(s);
    assertResult("WronglyNestedPs.txt",result);

    s = """
      <?xml version="1.0"?>
      <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
                "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
      <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en"></html>""";
    result = getTreeTextByText(s);
    assertResult("ToleratingPIs.txt",result);

    s = """
      <html>
      <body>
      <br>
      <br>
      <br>
      <ul>
      <li>JUnit is a regression test framework.</li><br><br>
      </ul>
      </ul>
      </body>
      </html>""";
    result = getTreeTextByText(s);
    assertResult("ToleratingMissingTagStart.txt",result);

    s = """
      <pre><code>
      <font class="detailedtext">public class</font> SortTest <font class="detailedtext">implements</font> Test {
        <font class="detailedtext">public int</font> countTestCases() {
          <font class="detailedtext">return</font> 0;
        }

        <font class="detailedtext">public void</font> run(TestResult testResult) {
          File testData = <font class="detailedtext">new</font> File(".");
          String[] names = FSUtil.findTests(testData);
          <font class="detailedtext">for</font> (<font class="detailedtext">int</font> i = 0; i < names.length; i++) {
            String name = names[i];
            <font class="detailedtext">new</font> MyTestCase(name, testData){}.run(testResult);
          }
        }

        <font class="detailedtext">public static</font> Test suite() {
          <font class="detailedtext">return new</font> SortTest();
        }
      }
          </code></pre>""";
    result = getTreeTextByText(s);
    assertResult("GreaterInTagValue.txt",result);

    s = "<html></html><html></html>";
    result = getTreeTextByText(s);
    assertResult("TwoRootTags.txt",result);

    s = """
      <html>
      <body>
      <hr>
      <table>
      \t<tr>
      \t\t<td>
      \t\t<br>
      \t\t<table>
      \t\t\t<tr>
      \t\t\t\t<td><nobr>Ctrl + <b>W</nobr></b></td>
      \t\t\t</tr>
      \t\t</table><br>
      \t\t<br>
      \t\t<table>
      \t\t\t<tr>
      \t\t\t\t<td><b><nobr>Ctrl + Numpad/</b></nobr></td>
      \t\t\t</tr><tr>
      \t\t\t\t<td><b><nobr>Ctrl + Numpad+</b></nobr></td>
      \t\t\t</tr>
      \t\t</table><br>
      \t\t<br>\t\t
      \t\t<br>
      \t\t</td>
      \t</tr>
      </table>
      <hr>
      </body>
      </html>""";
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
    ExtensionTestUtil.maskExtensions(HtmlEmbeddedContentSupport.EP_NAME,
                                     ContainerUtil.emptyList(), getTestRootDisposable());
    ExtensionTestUtil.maskExtensions(ExtensionPointName.create("com.intellij.html.scriptContentProvider"),
                                     ContainerUtil.emptyList(), getTestRootDisposable());
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
    return DebugUtil.psiTreeToString(fileFromText, true).trim();
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

  public void testCaptionTag() throws Throwable {
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
