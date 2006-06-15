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
    String pattern2 = TestUtils.loadFile("pattern2.html");
    
    assertEquals("Simple html find",1,findMatchesCount(content,pattern,false,StdFileTypes.HTML));
    assertEquals("Simple html find",9,findMatchesCount(content,pattern2,false,StdFileTypes.HTML));
  }
  
  public void testJspSearch() throws Exception {
    String content = TestUtils.loadFile("in1.html");
    String pattern = TestUtils.loadFile("pattern1.html");
    String pattern2 = TestUtils.loadFile("pattern2.html");
    
    assertEquals("Simple html find",1,findMatchesCount(content,pattern,false,StdFileTypes.JSP));
    assertEquals("Simple html find",9,findMatchesCount(content,pattern2,false,StdFileTypes.JSP));
  }

  public void testXmlSearch() {
    String s1 = "<aaa><bbb class=\"11\"></bbb></aaa><bbb class=\"11\"></bbb>";
    String s2 = "<bbb></bbb>";
    String s2_2 = "<bbb/>";
    String s2_3 = "<'t:[ regex( aaa ) ] />";
    String s2_4 = "<'_ 't:[ regex( class ) ]=\"'_\" />";
    String s2_5 = "<'_ '_=\"'t:[ regex( 11 ) ]\" />";

    assertEquals("Simple xml find",2,findMatchesCount(s1,s2,false,StdFileTypes.XML));
    assertEquals("Simple xml find with empty tag",2,findMatchesCount(s1,s2_2,false,StdFileTypes.XML));
    assertEquals("Simple xml find with typed var",1,findMatchesCount(s1,s2_3,false,StdFileTypes.XML));

    assertEquals("Simple xml find with typed attr",2,findMatchesCount(s1,s2_4,false,StdFileTypes.HTML));
    assertEquals("Simple xml find with typed attr value",2,findMatchesCount(s1,s2_5,false,StdFileTypes.HTML));

    String s3 = "<a> content </a>\n" +
                "<b> another content </b>\n" +
                "<c>another <aaa>zzz</aaa>content </c>";
    String s4 = "<'_tag>'Content*</'_tag>";
    assertEquals("Content match",6,findMatchesCount(s3,s4,false,StdFileTypes.HTML));
    assertEquals("Content match",6,findMatchesCount(s3,s4,false,StdFileTypes.XML));
  }
}
