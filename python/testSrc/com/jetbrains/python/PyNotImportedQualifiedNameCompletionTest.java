// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher.QualifiedNameMatcher;

import java.util.List;

public class PyNotImportedQualifiedNameCompletionTest extends PyTestCase {
  public void testQualifiedNamesAlwaysMatchedByFirstComponentOfQualifier() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    List<String> variants = myFixture.getLookupElementStrings();
    assertNotNull(variants);
    assertDoesntContain(variants, "foo.bar.func");
    assertContainsElements(variants, "bar.func", "baz.quux.func");
  }

  public void testQualifiedNameMatcherTest() {
    QualifiedNameMatcher matcher = new QualifiedNameMatcher(QualifiedName.fromDottedString("foo.bar.baz"));
    assertTrue(matcher.prefixMatches("foo.bar.baz"));
    assertTrue(matcher.prefixMatches("foo.bar.baz"));
    assertTrue(matcher.prefixMatches("fooExtra.bar.baz"));
    assertTrue(matcher.prefixMatches("foo.barExtra.baz"));
    assertTrue(matcher.prefixMatches("foo.bar.bazExtra"));
    assertFalse(matcher.prefixMatches(""));
    assertFalse(matcher.prefixMatches("baz"));
    assertFalse(matcher.prefixMatches("foo.bar"));
    assertFalse(matcher.prefixMatches("bar.baz"));
    assertTrue(matcher.prefixMatches("foo.xxx.bar.baz"));
    assertTrue(matcher.prefixMatches("foo.bar.xxx.baz"));
    assertFalse(matcher.prefixMatches("foo.bar.baz.xxx"));
    assertFalse(matcher.prefixMatches("xxx.foo.bar.baz"));
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion/notImportedQualifiedName/";
  }
}
