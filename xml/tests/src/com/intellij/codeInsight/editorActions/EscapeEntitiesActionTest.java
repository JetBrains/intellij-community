// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Dennis.Ushakov
 */
@TestApplication
public class EscapeEntitiesActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String N_DASH = new String(new byte[]{-30, -128, -109}, StandardCharsets.UTF_8);
  private static final String COPY = new String(new byte[]{-62, -82}, StandardCharsets.UTF_8);

  @Test
  public void testSimpleHtml() {
    doTest("<<<", "html", "&lt;&lt;&lt;");
  }

  @Test
  public void testSimpleXml() {
    doTest(">>>", "xml", "&gt;&gt;&gt;");
  }

  @Test
  public void testVeryWide() {
    doTest(N_DASH, "html", "&ndash;");
  }

  @Test
  public void testWide() {
    doTest(COPY, "html", "&reg;");
  }

  @Test
  public void testAttributeValue() {
    doTest("<a alt='" + N_DASH + "'></a>", "html", "<a alt='&ndash;'></a>");
  }

  @Test
  public void testTag() {
    doTest("<a><</a>", "html", "<a>&lt;</a>");
  }

  @Test
  public void testXmlStart() {
    doTest("<<<", "xml", "&lt;&lt;&lt;");
  }

  @Test
  public void testDoctypeSystemPublic() {
    doTest("""
             <!DOCTYPE html
                     PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                     "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">""", "html",
           """
             <!DOCTYPE html
                     PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                     "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">""");
  }

  @Test
  public void testXmlAmp() {
    doTest("""
             <component>
               amp  &    U+0026 (38) XML 1.0 ampersand
             </component>""", "xml",
           """
             <component>
               amp  &amp;    U+0026 (38) XML 1.0 ampersand
             </component>""");
  }

  @Test
  public void testXmlLt() {
    doTest("""
             <component>
               lt   <    U+003C (60) XML 1.0 less-than sign
             </component>""", "xml",
           """
             <component>
               lt   &lt;    U+003C (60) XML 1.0 less-than sign
             </component>""");
  }

  @Test
  public void testMultiCaret() {
    doTest("""
             <a><selection><</selection></a>
             <a><selection><</selection></a>
             <a><selection><</selection></a>
             """, "html",
           """
             <a>&lt;</a>
             <a>&lt;</a>
             <a>&lt;</a>
             """);
  }

  private void doTest(String text, final String extension, final String expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String finalText = !text.contains("<selection>") ? "<selection>" + text + "</selection>" : text;
    PlatformTestUtil.withEncoding(StandardCharsets.UTF_8, () -> {
      myFixture.configureByText(myFixture.getTestName(true) + "." + extension, finalText);
      myFixture.performEditorAction("EscapeEntities");
      myFixture.checkResult(expected);
    });
  }
}
