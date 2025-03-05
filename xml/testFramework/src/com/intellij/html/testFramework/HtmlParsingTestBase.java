// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.testFramework;

import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.EmbeddedTokenTypesProvider;
import com.intellij.xml.testFramework.XmlParsingTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.AssumptionViolatedException;

public abstract class HtmlParsingTestBase extends XmlParsingTestBase {

  protected HtmlParsingTestBase(@NotNull String dataPath,
                                @NotNull String fileExt,
                                ParserDefinition @NotNull ... definitions) {
    super(dataPath, fileExt, definitions);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtensionPoint(EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME, EmbeddedTokenTypesProvider.class);
  }

  @Override
  protected final void doTestXml(String text) throws Exception {
    doTestHtml(text);
  }

  protected void doTestHtml(String text) throws Exception {
    doTest(text, "test.html");
  }

  @Override
  public void testDtdUrl1() {
    throw new AssumptionViolatedException("disable");
  }

  @Override
  public void testCustomMimeType() {
    throw new AssumptionViolatedException("disable");
  }

  public void testHtmlDoctype1() throws Exception {
    doTestHtml("<!DOCTYPE html>\n");
  }

  public void testHtmlDoctype2() throws Exception {
    doTestHtml(" <!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">\n");
  }

  public void testHtmlDoctype3() throws Exception {
    doTestHtml(" <!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
  }

  public void testHtmlCharEntityRef() throws Exception {
    doTestHtml("&#xAAff;&#XaaFF;&#x&#X<tag attr='&#xAAff;&#XaaFF;&#x&#X'/>");
  }

  public void testHtmlComments() throws Exception {
    doTestHtml("""
                 <!--Valid comment-->
                 <!--Valid comment<!-->
                 <!--Invalid content <!-- -->
                 <!--Invalid comment starts: --> <!--> <!--->
                 <!--Invalid end <!--->
                 <!--Invalid end --!>
                 """);
  }

  public void testHtmlIEConditionalComments1() throws Exception {
    doTestHtml("""
                 <!--[if IE 6]>
                 <p>You are using Internet Explorer 6.</p>
                 <![endif]-->""");
  }

  public void testHtmlIEConditionalComments2() throws Exception {
    doTestHtml("""
                 <!--[if lte IE 7]>
                 <style type="text/css">
                 /* CSS here */
                 </style>
                 <![endif]-->""");
  }

  public void testHtmlIEConditionalComments3() throws Exception {
    doTestHtml("""
                 <!--[if !IE]>-->
                 <link href="non-ie.css" rel="stylesheet">
                 <!--<![endif]-->""");
  }

  public void testScriptEmbeddingParsing() throws Exception {
    doTestHtml("<script type=\"foo/bar\"><div></div></script>\n" +
               "<script type=\"foo/bar\"><div> </div></script>");
  }

  public void testSpecialTagsParsing() throws Exception {
    doTestHtml("""
                 <head><title>This is my <title>&lt;<!--</title><body>
                 <script type="foo/bar"><div> </div></script>
                 <style type='foo/bar'><my><style></style>
                 <textarea>this {{text}} {area} &nbsp; <is></cool></textarea>""");
  }

  public void testPAutoClose() throws Exception {
    doTestHtml("""
                 <div>
                 <p><br/><div><span><p></p></span></div>
                 <P><table></table>
                 </div>
                 """);
  }

  public void testScriptWithinScript() throws Exception {
    doTestHtml("""
                 <script>
                     document.write("<script>alert('foo')</script\\>")
                 </script>""");
  }

  public void testTemplateWithinP() throws Exception {
    doTestHtml("""
                 <div class="card-body">
                   <p class="card-text">
                     <template>
                       <div>something</div>
                     </template>
                   </p>
                 </div>""");
  }

  public void testCustomTagWithinP() throws Exception {
    doTestHtml("""
                 <p class="quotationAuthor">
                   <span>
                     <nuxt-link>
                       <div>
                         <img>
                         <span> <span class="quotationDash"> - </span> author </span>
                       </div>
                       <span> <span class="quotationDash"> - </span> author </span>
                     </nuxt-link>
                   </span>
                 </p>""");
  }

  public void testTemplateWithinCell() throws Exception {
    doTestHtml("""
                 <table border="1">
                   <tr>
                     <td>
                       1st cell
                       <template>
                         <tr>
                           <td>
                             not yet a cell
                       </template>
                     <td>
                       2nd cell
                 </table>
                 """);
  }

  public void testPAutoClosedByDl() throws Exception {
    doTestHtml("""
                 <p>
                 <dl>
                   <dd></dd>
                 </dl>
                 </p>
                 """);
  }

  public void testNestedCustomTable() throws Exception {
    doTestHtml("""
                 <p-table>
                     <ng-template>
                         <tr>
                             <td>
                                 <p-table>
                                     <ng-template>
                                         <tr>
                                             <td></td>
                                         </tr>
                                     </ng-template>
                                 </p-table>
                                 <p-table>
                                    <tbody>
                                      <td>a</td>
                                      <td>b
                                      <td>c
                                    </tbody>
                                 </p-table>
                             </td>
                         </tr>
                     </ng-template>
                 </p-table>
                 """);
  }

  public void testMenuLi() throws Exception {
    doTestHtml("""
                 <ul>
                    <li>
                        <menu>
                          <li></li>
                        </menu>
                    </li>
                 </ul>
                 <menu>
                    <li>
                        <menu>
                          <li></li>
                        </menu>
                    </li>
                 </menu>
                 <UlUnlisted>
                    <li>
                      <UlUnlisted>
                        <li></li>
                      </UlUnlisted>
                    </li>
                 </UlUnlisted>
                 """);
  }

  public void testTagOmissionP() throws Exception {
    doTestHtml("""
                 <body>
                     <p>Paragraph
                     <blockquote>
                       <p>Quoted text
                       <cite>cite</cite>
                     </blockquote>
                 </body>
                 """);
  }

  public void testTagOmissionUlLi() throws Exception {
    doTestHtml("""
                 <ul>
                   <li>
                     <span>
                       <i></i>
                       <span>Entities</span>
                     </span>
                     <ul>
                       <li>
                         <span>e</span>
                     </ul>
                   </li>
                 </ul>
                 """);
  }

  public void testPMustBeClosedCustomTag() throws Exception {
    doTestHtml("""
                 <my-component>foo<p>bar</my-component>
                 """);
  }

  public void testPMustBeClosedA() throws Exception {
    doTestHtml("""
                 <a href="foo">foo<p>bar</a>
                 """);
  }

  public void testPIsAutoClosed() throws Exception {
    doTestHtml("""
                 <div>foo<p>bar</div>
                 """);
  }

}
