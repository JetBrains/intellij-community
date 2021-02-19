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

  public void testQualifiedNamesAlwaysMatchedByFirstComponentOfQualifierWithEmptyAttributeName() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    List<String> variants = myFixture.getLookupElementStrings();
    assertNotNull(variants);
    assertDoesntContain(variants, "foo.bar.func");
    assertContainsElements(variants, "bar.func", "bar.func1");
  }

  // PY-47281
  public void testVariantsFromInternalThirdPartyModulesExcludedUnlessExported() {
    runWithAdditionalClassEntryInSdkRoots(getTestName(false) + "/site-packages", () -> {
      myFixture.copyDirectoryToProject(getTestName(false) + "/src", "");
      myFixture.configureByFile("main.py");
      myFixture.completeBasic();
      List<String> variants = myFixture.getLookupElementStrings();
      assertNotNull(variants);
      assertDoesntContain(variants, "mypackage._impl.func", "mypackage._vendor.lib.func");
      assertContainsElements(variants, "mypackage.func_exported", "mypackage_util._impl.func");
    });
  }

  // PY-47281
  public void testVariantsFromInternalSkeletonsExcludedUnlessExported() {
    String testName = getTestName(false);
    runWithAdditionalClassEntryInSdkRoots(testName + "/site-packages", () -> {
      runWithAdditionalClassEntryInSdkRoots(testName + "/python_stubs", () -> {
        myFixture.copyDirectoryToProject(testName + "/src", "");
        myFixture.configureByFile("main.py");
        myFixture.completeBasic();
        List<String> variants = myFixture.getLookupElementStrings();
        assertNotNull(variants);
        assertDoesntContain(variants, "mypackage._impl.func");
        assertContainsElements(variants, "mypackage.func_exported", "mypackage_util._impl.func");
      });
    });
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

  public void testImportForModuleFunction() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + "/main.after.py");
  }

  public void testImportForModuleClass() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + "/main.after.py");
  }

  public void testImportForAlias() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + "/main.after.py");
  }

  public void testImportForAliasWithNonEmptyAttribute() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + "/main.after.py");
  }

  public void testUseImportPriorityWhenAddingImport() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + "/main.after.py");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion/notImportedQualifiedName/";
  }
}
