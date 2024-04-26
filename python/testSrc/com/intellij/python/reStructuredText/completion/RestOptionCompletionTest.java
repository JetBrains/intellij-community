// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.python.reStructuredText.fixtures.RestFixtureTestCase;

/**
 * User : catherine
 */
public class RestOptionCompletionTest extends RestFixtureTestCase {

  public void testFootnote() {
    final String filePath = "/completion/option/footnote.rst";
    myFixture.configureByFiles(filePath);
    final LookupElement[] items = myFixture.completeBasic();
    assertNotNull(items);
    assertEquals(0, items.length);
  }

  public void testImage() {
    doTest();
  }

  public void testOutsideDirective() {
    final String filePath = "/completion/option/outside.rst";
    myFixture.configureByFiles(filePath);
    final LookupElement[] items = myFixture.completeBasic();
    assertNotNull(items);
    assertEquals(0, items.length);
  }

  private void doTest() {
    final String path = "/completion/option/" + getTestName(true);
    myFixture.configureByFile(path + ".rst");
    myFixture.completeBasic();
    myFixture.checkResultByFile(path + ".after.rst");
  }
}
