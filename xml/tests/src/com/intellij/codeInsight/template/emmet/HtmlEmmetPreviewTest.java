// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

public class HtmlEmmetPreviewTest extends EmmetPreviewTestBase {

  public void testPrimitiveAbbreviation() {
    myFixture.configureByText("test.html", "di<caret>");
    myFixture.type("v");
    assertNull(getPreview());
  }

  public void testPrimitiveAbbreviationWithEmptyClass() {
    myFixture.configureByText("test.html", "div<caret>");
    myFixture.type(".");
    assertNull(getPreview());
  }

  public void testAbbreviationWithNonEmptyClass() {
    myFixture.configureByText("test.html", "div.<caret>");
    myFixture.type("c");
    assertPreview("<div class=\"c\"></div>");
  }

  public void testAbbreviationWithNesting() {
    myFixture.configureByText("test.html", "div>di<caret>");
    myFixture.type("v");
    assertPreview("""
                    <div>
                        <div></div>
                    </div>""");
  }

  public void testAbbreviationWithFilter() {
    myFixture.configureByText("test.html", "div#id>div.class<caret>");
    myFixture.type("|c");
    assertPreview("""
                    <div id="id">
                        <div class="class"></div>
                        <!-- /.class -->
                    </div>
                    <!-- /#id -->""");
    myFixture.type("|s");
    assertPreview("""
                    <div id="id">
                        <div class="class"></div>
                        <!-- /.class -->
                    </div>
                    <!-- /#id -->""");
  }
  
  public void testPreviewXhtml() {
    myFixture.configureByText("test.xhtml", "div>b<caret>");
    myFixture.type("r");
    assertPreview("<div><br/></div>");
  }
}
