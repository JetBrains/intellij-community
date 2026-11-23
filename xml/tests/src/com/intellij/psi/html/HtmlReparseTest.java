// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.html;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.psi.ReparseFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.ReparseFixtureKt.reparseFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class HtmlReparseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<ReparseFixture> reparseFixture = reparseFixture(projectFixture, HtmlFileType.INSTANCE);

  @Test
  public void testReparseOnCompletion() throws IncorrectOperationException {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile("""
                  <html>
                    <head>
                      <LINK REL="StyleSheet">
                    </head>
                    <body class=\"""", "\"></body>\n" +
                                       "</html>");
    reparse.insert("Rulezz");

    reparse.prepareFile("""
                  <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Frameset//EN">
                  <!-- saved from url=(0029)http://edu.uuu.com.tw/doclib/ -->
                  <HTML>
                      <HEAD>
                          <TITLE>Web Document Library</TITLE>
                          <META http-equiv=Content-Type content="text/html; charset=utf-8">
                          <META content="MSHTML 6.00.2800.1458" name=GENERATOR>
                      </HEAD>
                      <FRAMESET border=1
                          frameSpacing=0 rows=50,* TOPMARGIN="0" LEFTMARGIN="0" MARGINHEIGHT="0"
                          MARGINWIDTH="0">
                          <FRAME style="BORDER-BOTT0M: #00716a 1px solid" border=0
                              name=fraToolbar marginWidth=0 marginHeight=0
                              src="toolbar.htm" frameBorder=no noResize
                              scrolling=no TOPMARGIN="0" LEFTMARGIN="0">
                          <""", """
                  FRAMESET border=1 name=mainframeset
                              frameSpacing=6 frameBorder=1 cols=216,* TOPMARGIN="0" LEFTMARGIN="0"
                              MARGINHEIGHT="0" MARGINWIDTH="0">
                              <FRAME border=1 name=fraLeftFrame marginWidth=0
                                  marginHeight=0 src="leftframe.htm" frameBorder=1
                                  TOPMARGIN="0" LEFTMARGIN="0">
                              <FRAME
                                  style="BORDER-TOP: #003366 1px groove; BORDER-LEFT: #003366 1px groove" border=0
                                  name=fraContent src="main.htm" frameBorder=no
                                  scrolling=yes>
                          </FRAMESET>
                      </FRAMESET>
                  </HTML>""");
    reparse.insert("Rulezz");
    
    reparse.prepareFile("""
                  <html>
                  <head>
                    <script type="text/css">
                      function aa() {

                      }
                    </script>
                  </head>
                  <body\s""", "\n</html>");
    reparse.insert("Rulezz");
  }

  @Test
  public void testReparseOnTyping1() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile(
      """
        <span jwcid="@Border">
        <form jwcid="@Form">
        <table class="textblock">
            <tr>
                <td>
                    Result
                    </"""
      ,
      """

                        <span>
                            <input value='ognl'/> Weak Positive <br/>
                        </span>
                    </fieldset>
                </td>
            </tr>
        </table>
        </form>
        </span>""");

    reparse.insert("aaa");

  }
  @Test
  public void testReparseOnTypingTiny() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile("<span><td></","></span>");
    reparse.insert("aaa");
  }
  @Test
  public void testReparseOnTyping() {
    ReparseFixture reparse = reparseFixture.get();
    //prepareFile(
    //        "<html>\n" +
    //        "<body>\n" +
    //        "<div>\n" +
    //        "   <div id=\"top\"",
    //        ">\n" +
    //       "        <div id=\"nav\"><ul>\n" +
    //       "\t\t\t<li><a href=\"../devnet/\">Developers</a></li>\n" +
    //       "            <li><a href=\"../company/index.html\">Company</a></li>\n" +
    //       "        </ul></div>\n" +
    //       "    </div>\n" +
    //       "  </div>\n" +
    //       "</body>\n" +
    //       "</html>");
    //remove(1);
    //insert("\"");

    reparse.prepareFile(
      "<p>\n something\n",
      "</p>"
    );
    
    reparse.insert("<div");
    reparse.insert(" id");
    reparse.insert("=");

    reparse.insert("\"");
    reparse.insert("\"");
    reparse.insert("/");
    reparse.insert(">");
  }

  @Test
  public void testReparseOnTyping6() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile(
      "<p>\n something\n",
      "</p>"
    );

    reparse.insert("<div");
    reparse.insert(" id=\"aaa\"");
    reparse.insert("/");
    reparse.insert(">");
  }

  @Test
  public void testReparseOnTyping5() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile("""
                  <html>
                    <body>
                      <""", """
                  a></a>
                    </body>
                  </html>""");
    reparse.insert("!--");
    reparse.remove(3);
  }

  @Test
  public void testReparseOnTyping4() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile(
            "<importbean>" +
            "<droplet>" +
            "<param value=\"bean:/meredith/SiteProperties.usingLightWeightVersion\"",
            "<td bgcolor=\"`request.getParameter(\"promoBGC1\")`\"></td>\n" +
            "</droplet>");
    reparse.insert("/>");
  }

  @Test
  public void testReparseOnTyping3() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile(
      """
        <span jwcid="@Border">
        <form jwcid="@Form">
        <table class="textblock">
            <tr>
                <td>
                    Result
                    <"""
      ,
      """

                        <span>
                            <input value='ognl'/> Weak Positive <br/>
                        </span>
                    </fieldset>
                </td>
            </tr>
        </table>
        </form>
        </span>""");

    reparse.insert("aaa");
  }

  @Test
  public void testReparseOnTyping2() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile("""
                  <ul>
                  <li>
                  <li>
                  </""", "ul>");
    reparse.insert("o");
    reparse.remove(1);
  }

  @Test
  public void testStackOverflowInEditOfErrorElement() throws IncorrectOperationException {
    ReparseFixture reparse = reparseFixture.get();
    String errorStr1 = "<!-- My Comment In Error -->";
    String s = "<html>\n" +
               "<body>\n" +
               "<hr><font>Ctrl + Navigation keys:</font><br>\n" +
               "\t\t<a name=\"ctrlSymbol\"></a><br>\n" +
               "\t\t<table>\n" +
               "\t\t\t<tr>\n" +
               "\t\t\t\t<td><b><nobr>Ctrl + Numpad/</b></nobr></td>\n" +
               "\t\t\t</tr><tr>\n" +
               "\t\t\t\t<td><b><nobr>Ctrl + Numpad+</b></nobr></td>\n" +
               "\t\t\t</tr>\n" +
               "\t\t</table><br><br>\n" +
               "\t\t[&nbsp;<a href=\"#TOP\">TOP</a>&nbsp;]\n" +
               "\t\t</td>\n" +
               "\t</tr>\n" +
               "</table>\n" +
               "<hr>\n" +
               "<hr>\n" +
               "</td></tr></table>\n" +
               errorStr1 +
               "</body>\n" +
               "</html>\n" +
               "<!--SpellChecked on June 21, 2004-->";
    final int errorMarkerPos = s.indexOf(errorStr1);
    String s1 = s.substring(0,errorMarkerPos);
    String s2 = s.substring(errorMarkerPos);
    reparse.prepareFile(s1,s2);
    reparse.remove(errorStr1.length());
  }

  @Test
  public void testPaste() {
    ReparseFixture reparse = reparseFixture.get();
    reparse.prepareFile("","");
    reparse.insert("""
             <html>
             <body><table>
             \t<tr>
             \t\t<td>
             <ol>
             <li><b>Where should one turn with questions, concerning this Web-site?</b><br>
             For all questions, concerning this Web-site, please contact us by e-mail: <br><br>

             <li><b>I have additional questions, which are not listed above.</b><br>
             For all other questions, please contact us by e-mail: </li>
             </ol>

             \t\t</td>
             \t\t<td>
             \t\t<table>
             \t\t<form>
             \t\t<INPUT>
             \t\t</tr></form></table>

             \t\t</td>

             \t</tr>
             </table>
             </body>
             </html>""");
  }
  
  @Test
  public void testEdit() {
    ReparseFixture reparse = reparseFixture.get();
    String s = """
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
    int markerPos;
    String s1;
    String s2;

    String marker = "</b>";
    String marker2 = "</nobr>";
    markerPos = s.indexOf(marker);
    s1 = s.substring(0,markerPos);
    s2 = s.substring(markerPos);
    reparse.prepareFile(s1,s2);
    reparse.remove(marker.length());

    markerPos = s.indexOf(marker2) + marker2.length();
    s1 = s.substring(0,markerPos);
    s2 = s.substring(markerPos);
    reparse.prepareFile(s1,s2);

    reparse.remove(marker2.length());
    reparse.moveEditPointRight(marker.length());
    reparse.insert(marker2);

    markerPos = s.indexOf(marker,markerPos+marker2.length()) + marker.length();
    reparse.setEditPoint(markerPos);
    reparse.remove(marker.length());
    reparse.moveEditPointRight(marker2.length());
    reparse.insert(marker);

    markerPos = s.indexOf(marker,markerPos+marker2.length()) + marker.length();
    reparse.setEditPoint(markerPos);
    reparse.remove(marker.length());
    reparse.moveEditPointRight(marker2.length());
    reparse.insert(marker);

    String NewMarker = "A-A";
    s = "<html><body><p id=1>AAAAA" + NewMarker + "<p id=3>BBBB</body></html>";
    markerPos = s.indexOf(NewMarker) + NewMarker.length();
    reparse.prepareFile(s.substring(0,markerPos),s.substring(markerPos));
    reparse.insert("<p id=2>ZZZZ");
  }
}
