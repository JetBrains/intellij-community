package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 3, 2004
 * Time: 5:45:17 PM
 * To change this template use File | Settings | File Templates.
 */
@SuppressWarnings({"ALL"})
public class XmlStructuralSearchTest extends StructuralSearchTestCase {
  
  public void testHtmlSearch() throws Exception {
    String content = TestUtils.loadFile("in1.html");
    String pattern = TestUtils.loadFile("pattern1.html");
    String pattern2 = TestUtils.loadFile("pattern1_2.html");
    
    assertEquals("Simple html find",1,findMatchesCount(content,pattern,false,StdFileTypes.HTML));
    assertEquals("Simple html find",9,findMatchesCount(content,pattern2,false,StdFileTypes.HTML));
  }

  public void testHtmlSearch2() throws Exception {
    String content = TestUtils.loadFile("in4.html");
    String pattern = TestUtils.loadFile("pattern4.html");

    assertEquals("Simple html find",1,findMatchesCount(content,pattern,false,StdFileTypes.HTML));

    pattern = TestUtils.loadFile("pattern4_2.html");
    assertEquals("Simple html find",1,findMatchesCount(content,pattern,false,StdFileTypes.HTML));
  }

  public void testHtmlSearchCaseInsensitive() {
    String html = "<HTML><HEAD><TITLE>Hello Worlds</TITLE></HEAD><body><img src='test.gif'><body></HTML>";
    String pattern = "<title>'_a</title>";

    options.setCaseSensitiveMatch(false);
    assertEquals("case insensitive search", 1, findMatchesCount(html, pattern, false, StdFileTypes.HTML));

    String pattern2 = "<'t SRC=\"'_v\"/>";
    assertEquals("case insensitive attribute", 1, findMatchesCount(html, pattern2, false, StdFileTypes.HTML));

    String pattern3 = "<'t '_a=\"TEST.gif\">";
    assertEquals("case insensitive attribute value", 1, findMatchesCount(html, pattern3, false, StdFileTypes.HTML));
  }

  public void testHtmlSearchCaseSensitive() {
    String html = "<HTML><HEAD><TITLE>Hello Worlds</TITLE></HEAD><body><img src='test.gif'><body></HTML>";
    String pattern = "<title>'_a</title>";

    options.setCaseSensitiveMatch(true);
    assertEquals("case sensitive search", 0, findMatchesCount(html, pattern, false, StdFileTypes.HTML));
  }
  
  public void testJspSearch() throws Exception {
    String content = TestUtils.loadFile("in1.html");
    String pattern = TestUtils.loadFile("pattern1.html");
    String pattern2 = TestUtils.loadFile("pattern1_2.html");
    
    assertEquals("Simple html find",1,findMatchesCount(content,pattern,false,StdFileTypes.JSP));
    assertEquals("Simple html find",9,findMatchesCount(content,pattern2,false,StdFileTypes.JSP));
  }

  public void testXmlSearch() {
    String s1 = "<aaa><bbb class=\"11\"></bbb></aaa><bbb class=\"22\"></bbb>";
    String s2 = "<bbb></bbb>";
    String s2_2 = "<bbb/>";
    String s2_3 = "<'t:[ regex( aaa ) ] />";
    String s2_4 = "<'_ 't:[ regex( class ) ]=\"'_\" />";
    String s2_5 = "<'_ '_=\"'t:[ regex( 11 ) ]\" />";

    assertEquals("Simple xml find",2,findMatchesCount(s1,s2,false,StdFileTypes.XML));
    assertEquals("Simple xml find with empty tag",2,findMatchesCount(s1,s2_2,false,StdFileTypes.XML));
    assertEquals("Simple xml find with typed var",1,findMatchesCount(s1,s2_3,false,StdFileTypes.XML));

    assertEquals("Simple xml find with typed attr",2,findMatchesCount(s1,s2_4,false,StdFileTypes.HTML));
    assertEquals("Simple xml find with typed attr value",1,findMatchesCount(s1,s2_5,false,StdFileTypes.HTML));

    String s3 = "<a> content </a>\n" +
                "<b> another content </b>\n" +
                "<c>another <aaa>zzz</aaa>content </c>";
    String s4 = "<'_tag>'Content*</'_tag>";
    assertEquals("Content match",6,findMatchesCount(s3,s4,false,StdFileTypes.HTML));
    assertEquals("Content match",6,findMatchesCount(s3,s4,false,StdFileTypes.XML));
  }

  public void testXhtmlJspxSearch() {
    String source = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n" +
               "        \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
               "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
               "<head><title>Title of document</title></head>\n" +
               "<body>\n" +
               "<b><i>This text is bold and italic</i></b>\n" +
               "<p>This is a paragraph</p>\n" +
               "<p>This is another paragraph</p>\n" +
               "<img src=\"happy.gif\" alt=\"Happy face\"/>\n" +
               "</body>\n" +
               "</html>";
    String pattern = "<p>$A$</p>";
    assertEquals("xhtml", 2, findMatchesCount(source, pattern, false, StdFileTypes.XHTML));
    assertEquals("jspx", 2, findMatchesCount(source, pattern, false, StdFileTypes.JSPX));
  }

  //public void testXmlSearch2() {
  //  String s1 = "<body><p class=\"11\"> AAA </p><p class=\"22\"></p> <p> ZZZ </p> <p/> <p/> <p/> </body>";
  //  String s2 = "<p '_a?=\"'_t:[ regex( 11 ) ]\"> 'content? </p>";
  //
  //  assertEquals(5,findMatchesCount(s1,s2,false,StdFileTypes.XML));
  //}
}
