// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.completion;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.python.reStructuredText.fixtures.RestFixtureTestCase;

/**
 * User : catherine
 */
public class RestDirectiveCompletionTest extends RestFixtureTestCase {

  public void testNote() {
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());
    doTest();
  }

  public void testInline() {
    final String filePath = "/completion/directive/inline.rst";
    myFixture.configureByFiles(filePath);
    final LookupElement[] items = myFixture.completeBasic();
    assertNotNull(items);
    assertEquals(0, items.length);
  }

  public void testMultiple() {
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());
    final String filePath = "/completion/directive/multiple.rst";
    myFixture.configureByFiles(filePath);
    final LookupElement[] items = myFixture.completeBasic();
    assertNotNull(items);
    assertEquals(7, items.length);

    assertEquals("caution::", items[0].getLookupString());
  }

  private void doTest() {
    final String path = "/completion/directive/" + getTestName(true);
    myFixture.configureByFile(path + ".rst");
    myFixture.completeBasic();
    myFixture.checkResultByFile(path + ".after.rst");
  }
}
