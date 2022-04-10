// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.stubs.PyQualifiedNameCompletionMatcher.QualifiedNameMatcher;
import org.jetbrains.annotations.Nullable;

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
    assertFalse(matcher.prefixMatches("nonfoo.foo.bar.baz"));
  }

  public void testImportForModuleFunction() {
    doTestBasicCompletion();
  }

  public void testImportForModuleClass() {
    doTestBasicCompletion();
  }

  public void testImportForAlias() {
    doTestBasicCompletion();
  }

  public void testImportForAliasWithNonEmptyAttribute() {
    doTestBasicCompletion();
  }

  public void testUseImportPriorityWhenAddingImport() {
    doTestBasicCompletion();
  }

  // PY-47304
  public void testDirectModuleAttributesSuggestedForNonEmptyAttributePrefix() {
    doTestBasicCompletion();
  }

  //PY-47247
  public void testNoImportForSubpackages() {
    doTestBasicCompletion();
  }

  //PY-47247
  public void testNoImportForSubmodules() {
    doTestBasicCompletion();
  }

  //PY-47247
  public void testShouldNotSuggestSubmodulesForAliases() {
    doTestBasicCompletion();
  }

  //PY-47253
  public void testShowOnlyImmediateAttributesForAliases() {
    doTestBasicCompletion();
  }

  //PY-47253
  public void testFuzzyResultsShouldBeAddedToAliasAttributesCompletion() {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    List<String> variants = myFixture.getLookupElementStrings();
    assertEquals(2, variants.size());
    assertContainsElements(variants, "np.invert", "nt_parser.input");
  }

  //PY-47253
  public void testAliasAttributesShouldNotBeDuplicated() {
    doTestBasicCompletion();
  }

  // PY-48220
  public void testAttributesFromPackageStubSuggested() {
    assertContainsElements(doBasicCompletion(), "pkg.foo");
  }

  // PY-48220
  public void testAttributesFromModuleStubSuggested() {
    assertContainsElements(doBasicCompletion(), "mod.foo");
  }

  // PY-48219
  public void testAttributesNotLimitedByDunderAll() {
    assertContainsElements(doBasicCompletion(), "mod.foo");
  }

  // PY-48219
  public void testAliasAttributesNotLimitedByDunderAll() {
    doTestBasicCompletion();
  }

  // PY-48198
  public void testSubpackagesAndSubmodulesOfNamespacePackages() {
    assertContainsElements(doBasicCompletion(), "nspkg.submod", "nspkg.subpkg");
  }

  // PY-47941
  public void testAttributeReExportedWithAlias() {
    assertContainsElements(doBasicCompletion(), "pytest.mark", "pytest.param");
  }

  // PY-47254
  public void testAlreadyImportedModulesNotSuggestedTwice() {
    List<String> variants = doBasicCompletion();
    assertEquals(1, ContainerUtil.count(variants, "foo"::equals));
  }

  // PY-47962
  public void testNonImportedModulesSuggestedLast() {
    doBasicCompletion();
    myFixture.assertPreferredCompletionItems(0, "configuration=");
    assertContainsElements(myFixture.getLookupElementStrings(), "config", "contrib");
  }

  // PY-47962
  public void testModuleNamesWithMiddleMatchNotSuggested() {
    List<String> variants = doBasicCompletion();
    assertDoesntContain(variants, "mod_foo_bar", "mod_fb", "foobar");
    assertContainsElements(variants, "fb", "fb_util", "foo_bar");
  }

  // PY-47962
  public void testAttributesOfModulesWithMiddleMatchNotSuggested() {
    List<String> variants = doBasicCompletion();
    assertDoesntContain(variants, "mod_foo_bar.var", "mod_fb.var", "foobar.var");
    assertContainsElements(variants, "fb.var", "fb_util.var", "foo_bar.var");
  }

  @Nullable
  private List<String> doBasicCompletion() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.py");
    myFixture.completeBasic();
    return myFixture.getLookupElementStrings();
  }

  private void doTestBasicCompletion() {
    doBasicCompletion();
    myFixture.checkResultByFile(getTestName(false) + "/main.after.py");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion/notImportedQualifiedName/";
  }
}
