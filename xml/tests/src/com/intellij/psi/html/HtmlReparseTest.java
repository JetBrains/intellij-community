// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.html;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.psi.AbstractReparseTestCase;
import com.intellij.util.IncorrectOperationException;

public class HtmlReparseTest extends AbstractReparseTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setFileType(HtmlFileType.INSTANCE);
  }

  public void testReparseOnCompletion() throws IncorrectOperationException {
    prepareFile("<html>\n" +
                "  <head>\n" +
                "    <LINK REL=\"StyleSheet\">\n" +
                "  </head>\n" +
                "  <body class=\"","\"></body>\n" +
                                   "</html>");
    insert("Rulezz");

    prepareFile("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Frameset//EN\">\n" +
                "<!-- saved from url=(0029)http://edu.uuu.com.tw/doclib/ -->\n" +
                "<HTML>\n" +
                "    <HEAD>\n" +
                "        <TITLE>Web Document Library</TITLE>\n" +
                "        <META http-equiv=Content-Type content=\"text/html; charset=utf-8\">\n" +
                "        <META content=\"MSHTML 6.00.2800.1458\" name=GENERATOR>\n" +
                "    </HEAD>\n" +
                "    <FRAMESET border=1\n" +
                "        frameSpacing=0 rows=50,* TOPMARGIN=\"0\" LEFTMARGIN=\"0\" MARGINHEIGHT=\"0\"\n" +
                "        MARGINWIDTH=\"0\">\n" +
                "        <FRAME style=\"BORDER-BOTT0M: #00716a 1px solid\" border=0\n" +
                "            name=fraToolbar marginWidth=0 marginHeight=0\n" +
                "            src=\"toolbar.htm\" frameBorder=no noResize\n" +
                "            scrolling=no TOPMARGIN=\"0\" LEFTMARGIN=\"0\">\n" +
                "        <","FRAMESET border=1 name=mainframeset\n" +
                "            frameSpacing=6 frameBorder=1 cols=216,* TOPMARGIN=\"0\" LEFTMARGIN=\"0\"\n" +
                "            MARGINHEIGHT=\"0\" MARGINWIDTH=\"0\">\n" +
                "            <FRAME border=1 name=fraLeftFrame marginWidth=0\n" +
                "                marginHeight=0 src=\"leftframe.htm\" frameBorder=1\n" +
                "                TOPMARGIN=\"0\" LEFTMARGIN=\"0\">\n" +
                "            <FRAME\n" +
                "                style=\"BORDER-TOP: #003366 1px groove; BORDER-LEFT: #003366 1px groove\" border=0\n" +
                "                name=fraContent src=\"main.htm\" frameBorder=no\n" +
                "                scrolling=yes>\n" +
                "        </FRAMESET>\n" +
                "    </FRAMESET>\n" +
                "</HTML>");
    insert("Rulezz");
    
    prepareFile("<html>\n" +
                "<head>\n" +
                "  <script type=\"text/css\">\n" +
                "    function aa() {\n" +
                "\n" +
                "    }\n" +
                "  </script>\n" +
                "</head>\n" +
                "<body ","\n</html>");
    insert("Rulezz");
  }

  public void testReparseOnTyping1() {
    prepareFile(
      "<span jwcid=\"@Border\">\n" +
      "<form jwcid=\"@Form\">\n" +
      "<table class=\"textblock\">\n" +
      "    <tr>\n" +
      "        <td>\n" +
      "            Result\n" +
      "            </"
      ,
      "\n" +
      "                <span>\n" +
      "                    <input value='ognl'/> Weak Positive <br/>\n" +
      "                </span>\n" +
      "            </fieldset>\n" +
      "        </td>\n" +
      "    </tr>\n" +
      "</table>\n" +
      "</form>\n" +
      "</span>");

    insert("aaa");

  }
  public void testReparseOnTypingTiny() {
    prepareFile("<span><td></","></span>");
    insert("aaa");
  }
  public void testReparseOnTyping() {
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

    prepareFile(
      "<p>\n something\n",
      "</p>"
    );
    
    insert("<div");
    insert(" id");
    insert("=");

    insert("\"");
    insert("\"");
    insert("/");
    insert(">");
  }

  public void testReparseOnTyping6() {
    prepareFile(
      "<p>\n something\n",
      "</p>"
    );

    insert("<div");
    insert(" id=\"aaa\"");
    insert("/");
    insert(">");
  }

  public void testReparseOnTyping5() {
    prepareFile("<html>\n" +
            "  <body>\n" +
            "    <", "a></a>\n" +
         "  </body>\n" +
         "</html>");
    insert("!--");
    remove(3);
  }

  public void testReparseOnTyping4() {
    prepareFile(
            "<importbean>" +
            "<droplet>" +
            "<param value=\"bean:/meredith/SiteProperties.usingLightWeightVersion\"",
            "<td bgcolor=\"`request.getParameter(\"promoBGC1\")`\"></td>\n" +
            "</droplet>");
    insert("/>");
  }

  public void testReparseOnTyping3() {
    prepareFile(
      "<span jwcid=\"@Border\">\n" +
      "<form jwcid=\"@Form\">\n" +
      "<table class=\"textblock\">\n" +
      "    <tr>\n" +
      "        <td>\n" +
      "            Result\n" +
      "            <"
      ,
      "\n" +
      "                <span>\n" +
      "                    <input value='ognl'/> Weak Positive <br/>\n" +
      "                </span>\n" +
      "            </fieldset>\n" +
      "        </td>\n" +
      "    </tr>\n" +
      "</table>\n" +
      "</form>\n" +
      "</span>");

    insert("aaa");
  }

  public void testReparseOnTyping2() {
    prepareFile("<ul>\n" + "<li>\n" + "<li>\n" + "</","ul>");
    insert("o");
    remove(1);
  }

  public void testStackOverflowInEditOfErrorElement() throws IncorrectOperationException {
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
    prepareFile(s1,s2);
    remove(errorStr1.length());
  }

  public void testPaste() {
    prepareFile("","");
    insert("<html>\n" +
           "<body>" +
           "<table>\n" +
           "\t<tr>\n" +
           "\t\t<td>\n" +
           "<ol>\n" +
           "<li><b>Where should one turn with questions, concerning this Web-site?</b><br>\n" +
           "For all questions, concerning this Web-site, please contact us by e-mail: <br><br>\n" +
           "\n" +
           "<li><b>I have additional questions, which are not listed above.</b><br>\n" +
           "For all other questions, please contact us by e-mail: </li>\n" +
           "</ol>\n" +
           "\n" +
           "\t\t</td>\n" +
           "\t\t<td>\n" +
           "\t\t<table>\n" +
           "\t\t<form>\n" +
           "\t\t<INPUT>\n" +
           "\t\t</tr></form></table>\n" +
           "\n" +
           "\t\t</td>\n" +
           "\n" +
           "\t</tr>\n" +
           "</table>\n" +
           "</body>\n" +
           "</html>");
  }
  
  public void testEdit() {
    String s = "<html>\n" +
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
    int markerPos;
    String s1;
    String s2;

    String marker = "</b>";
    String marker2 = "</nobr>";
    markerPos = s.indexOf(marker);
    s1 = s.substring(0,markerPos);
    s2 = s.substring(markerPos);
    prepareFile(s1,s2);
    remove(marker.length());

    markerPos = s.indexOf(marker2) + marker2.length();
    s1 = s.substring(0,markerPos);
    s2 = s.substring(markerPos);
    prepareFile(s1,s2);

    remove(marker2.length());
    moveEditPointRight(marker.length());
    insert(marker2);

    markerPos = s.indexOf(marker,markerPos+marker2.length()) + marker.length();
    setEditPoint(markerPos);
    remove(marker.length());
    moveEditPointRight(marker2.length());
    insert(marker);

    markerPos = s.indexOf(marker,markerPos+marker2.length()) + marker.length();
    setEditPoint(markerPos);
    remove(marker.length());
    moveEditPointRight(marker2.length());
    insert(marker);

    String NewMarker = "A-A";
    s = "<html><body><p id=1>AAAAA" + NewMarker + "<p id=3>BBBB</body></html>";
    markerPos = s.indexOf(NewMarker) + NewMarker.length();
    prepareFile(s.substring(0,markerPos),s.substring(markerPos));
    insert("<p id=2>ZZZZ");
  }
}
