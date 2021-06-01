// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.TestLookupElementPresentation;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.completion.PyModuleNameCompletionContributor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyMethodParametersInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.types.PyNamedTupleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Ignore;

import java.util.Arrays;
import java.util.List;


@TestDataPath("$CONTENT_ROOT/../testData/completion")
public class Py3CompletionTest extends PyTestCase {

  public void testPropertyDecorator() {
    doTest();
  }

  public void testPropertyAfterAccessor() {  // PY-5951
    doTest();
  }

  public void testNamedTuple() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    final List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertTrue(strings.contains("lat"));
    assertTrue(strings.contains("long"));
  }

  public void testNamedTupleBaseClass() {
    doTest();
  }

  // PY-13157
  public void testMetaClass() {
    doTestByText("class C(meta<caret>):\n" +
                 "    pass\n");
    myFixture.checkResult("class C(metaclass=):\n" +
                          "    pass\n");
  }

  private void doTest() {
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void doNegativeTest() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    LookupElement[] variants = myFixture.completeBasic();
    assertNotNull("Expected no completion variants, but one item was auto-completed", variants);
    assertEmpty(variants);
  }

  private void doMultiFileTest() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(true) + "/a.after.py");
  }


  protected void doMultiFileTest(@NotNull List<String> sourceRoots) {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    final Module module = myFixture.getModule();
    for (String root : sourceRoots) {
      PsiTestUtil.addSourceRoot(module, myFixture.findFileInTempDir(root));
    }
    try {
      myFixture.configureByFile("a.py");
      myFixture.completeBasic();
      myFixture.checkResultByFile(getTestName(true) + "/a.after.py");
    }
    finally {
      for (String root : sourceRoots) {
        PsiTestUtil.removeSourceRoot(module, myFixture.findFileInTempDir(root));
      }
    }
  }

  @Nullable
  private List<String> doTestByText(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.completeBasic();
    return myFixture.getLookupElementStrings();
  }

  // PY-4073
  public void testSpecialFunctionAttributesPy3() {
    List<String> suggested = doTestByText("def func(): pass; func.func_<caret>");
    assertNotNull(suggested);
    assertEmpty(suggested);

    suggested = doTestByText("def func(): pass; func.__<caret>");
    assertNotNull(suggested);
    assertContainsElements(suggested, "__defaults__", "__globals__", "__closure__",
                           "__code__", "__name__", "__doc__", "__dict__", "__module__");
    assertContainsElements(suggested, "__annotations__", "__kwdefaults__");
  }

  // PY-7375
  public void testImportNamespacePackage() {
    doMultiFileTest();
  }

  // PY-5422
  public void testImportQualifiedNamespacePackage() {
    doMultiFileTest();
  }

  // PY-6477
  public void testFromQualifiedNamespacePackageImport() {
    doMultiFileTest();
  }

  public void testImportNestedQualifiedNamespacePackage() {
    doMultiFileTest();
  }

  // PY-7376
  public void testRelativeFromImportInNamespacePackage() {
    doMultiFileTestInsideNamespacePackage();
  }

  // PY-7376
  public void testRelativeFromImportInNamespacePackage2() {
    doMultiFileTestInsideNamespacePackage();
  }

  private void doMultiFileTestInsideNamespacePackage() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("nspkg1/a.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(true) + "/nspkg1/a.after.py");
  }

  // PY-14385
  public void testNotImportedSubmodulesOfNamespacePackage() {
    doMultiFileTest();
  }

  // PY-15390
  public void testMatMul() {
    doTest();
  }

  // PY-11214
  public void testDunderNext() {
    doTest();
  }

  public void testAsync() {
    PyModuleNameCompletionContributor.ENABLED = false;
    doTest();
  }

  public void testAwait() {
    doTest();
  }

  // PY-17828
  public void testDunderPrepare() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasicAllCarets(null);
    myFixture.checkResultByFile(testName + ".after.py");
  }

  // PY-17828
  // TODO: do we need this?
  @Ignore
  public void ignore_testDunderPrepareHonourInspectionSettings() {
    myFixture.enableInspections(PyMethodParametersInspection.class);

    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasicAllCarets(null);
    myFixture.checkResultByFile(testName + ".after.py");
  }

  // PY-20279
  public void testImplicitDunderClass() {
    doTestByText("class First:\n" +
                 "    def foo(self):\n" +
                 "        print(__cl<caret>)");
    myFixture.checkResult("class First:\n" +
                          "    def foo(self):\n" +
                          "        print(__class__)");

    doTestByText("class First:\n" +
                 "    @staticmethod\n" +
                 "    def foo():\n" +
                 "        print(__cl<caret>)");
    myFixture.checkResult("class First:\n" +
                          "    @staticmethod\n" +
                          "    def foo():\n" +
                          "        print(__class__)");

    doTestByText("class First:\n" +
                 "    print(__cl<caret>)");
    myFixture.checkResult("class First:\n" +
                          "    print(__cl)");

    doTestByText("def abc():\n" +
                 "    print(__cl<caret>)");
    myFixture.checkResult("def abc():\n" +
                          "    print(__cl)");
  }

  // PY-11208
  public void testMockPatchObject1() {
    final String testName = getTestName(true);

    runWithAdditionalClassEntryInSdkRoots(
      testName + "/lib",
      () -> {
        myFixture.configureByFile(testName + "/a.py");
        myFixture.completeBasic();
        myFixture.checkResultByFile(testName + "/a.after.py");
      }
    );
  }

  // PY-11208
  public void testMockPatchObject2() {
    final String testName = getTestName(true);

    runWithAdditionalClassEntryInSdkRoots(
      testName + "/lib",
      () -> {
        myFixture.configureByFile(testName + "/a.py");
        myFixture.completeBasic();
        myFixture.checkResultByFile(testName + "/a.after.py");
      }
    );
  }

  // PY-21060
  public void testGenericTypeInheritor() {
    doTest();
  }

  // PY-19702
  public void testMetaclassAttributeOnDefinition() {
    final List<String> suggested = doTestByText("class Meta(type):\n" +
                                                "    def __init__(self, what, bases, dict):\n" +
                                                "        self.meta_attr = \"attr\"\n" +
                                                "        super().__init__(what, bases, dict)\n" +
                                                "class A(metaclass=Meta):\n" +
                                                "    pass\n" +
                                                "print(A.<caret>)");

    assertNotNull(suggested);
    assertContainsElements(suggested, "meta_attr");
  }

  // PY-19702
  public void testMetaclassAttributeOnInstance() {
    final List<String> suggested = doTestByText("class Meta(type):\n" +
                                                "    def __init__(self, what, bases, dict):\n" +
                                                "        self.meta_attr = \"attr\"\n" +
                                                "        super().__init__(what, bases, dict)\n" +
                                                "class A(metaclass=Meta):\n" +
                                                "    pass\n" +
                                                "print(A().<caret>)");

    assertNotNull(suggested);
    assertContainsElements(suggested, "meta_attr");
  }

  public void testMetaclassMethodOnDefinition() {
    final List<String> suggested = doTestByText("class Meta(type):\n" +
                                                "    def meta_method(cls):\n" +
                                                "        pass\n" +
                                                "class A(metaclass=Meta):\n" +
                                                "    pass\n" +
                                                "print(A.<caret>)");

    assertNotNull(suggested);
    assertContainsElements(suggested, "meta_method");
  }

  public void testMetaclassMethodOnInstance() {
    final List<String> suggested = doTestByText("class Meta(type):\n" +
                                                "    def meta_method(cls):\n" +
                                                "        pass\n" +
                                                "class A(metaclass=Meta):\n" +
                                                "    pass\n" +
                                                "print(A().<caret>)");

    assertNotNull(suggested);
    assertDoesntContain(suggested, "meta_method");
  }

  // PY-27398
  public void testDataclassPostInit() {
    doMultiFileTest();
  }

  // PY-27398
  public void testDataclassWithInitVarPostInit() {
    doMultiFileTest();
  }

  // PY-27398
  public void testDataclassPostInitNoInit() {
    doMultiFileTest();
  }

  // PY-26354
  public void testAttrsPostInit() {
    doTestByText("import attr\n" +
                 "\n" +
                 "@attr.s\n" +
                 "class C:\n" +
                 "    x = attr.ib()\n" +
                 "    y = attr.ib(init=False)\n" +
                 "\n" +
                 "    def __attrs_<caret>");

    myFixture.checkResult("import attr\n" +
                          "\n" +
                          "@attr.s\n" +
                          "class C:\n" +
                          "    x = attr.ib()\n" +
                          "    y = attr.ib(init=False)\n" +
                          "\n" +
                          "    def __attrs_post_init__(self):");
  }

  // PY-26354
  public void testAttrsPostInitNoInit() {
    assertEmpty(
      doTestByText("import attr\n" +
                   "\n" +
                   "@attr.s(init=False)\n" +
                   "class C:\n" +
                   "    x = attr.ib()\n" +
                   "    y = attr.ib(init=False)\n" +
                   "\n" +
                   "    def __attrs_<caret>")
    );
  }

  // PY-26354
  public void testAttrsValidatorParameters() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasicAllCarets(null);
    myFixture.checkResultByFile(testName + ".after.py");
  }

  //PY-28332
  public void testImportNamespacePackageInMultipleRoots() {
    doMultiFileTest(Arrays.asList("root1/src", "root2/src"));
  }

  //PY-28332
  public void testImportNamespacePackageInMultipleRoots2() {
    doMultiFileTest(Arrays.asList("root1/src", "root2/src"));
  }

  // PY-27148
  public void testNamedTupleSpecial() {
    final List<String> suggested = doTestByText("from collections import namedtuple\n" +
                                                "class Cat1(namedtuple(\"Cat\", \"name age\")):\n" +
                                                "    pass\n" +
                                                "c1 = Cat1(\"name\", 5)\n" +
                                                "c1.<caret>");
    assertNotNull(suggested);
    assertContainsElements(suggested, PyNamedTupleType.NAMEDTUPLE_SPECIAL_ATTRIBUTES);
  }

  // PY-33254, PY-12339, PY-40834
  public void testTypedParameterStringPath() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasicAllCarets(null);
    myFixture.checkResultByFile(getTestName(true) + "/a.after.py");
  }

  // PY-42700
  public void testFStringLikeCompletionInOrdinaryStringLiterals() {
    doTest();
  }

  // PY-42700
  public void testFStringLikeCompletionPreservesParenthesesForCallables() {
    doTest();
  }

  // PY-42700
  public void testFStringLikeCompletionNotAvailableBefore36() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doNegativeTest);
  }

  // PY-42700
  public void testFStringLikeCompletionNotAvailableInByteLiterals() {
    doNegativeTest();
  }

  // PY-42700
  public void testFStringLikeCompletionNotAvailableInUnicodeLiterals() {
    doNegativeTest();
  }

  // PY-42700
  public void testFStringLikeCompletionNotAvailableInStrFormatCalls() {
    doNegativeTest();
  }

  // PY-42700
  public void testFStringLikeCompletionNotAvailableAfterEscapedOpeningBrace() {
    doNegativeTest();
  }

  // PY-42700
  public void testFStringLikeCompletionAvailableAfterOpeningBraceFollowingEscapedOne() {
    doTest();
  }

  // PY-42700
  public void testFStringLikeCompletionDoesNotDuplicateClosingBrace() {
    doTest();
  }

  // PY-42700
  public void testFStringLikeCompletionOnMultipleCaretsDoesNotDuplicatePrefix() {
    doTest();
  }

  // PY-42700
  public void testFStringLikeCompletionDoesNotWorkInStringWithInjections() {
    doNegativeTest();
  }

  // EA-232631
  public void testFStringLikeCompletionNotAvailableInStringElementsInSyntacticallyIllegalPosition() {
    doNegativeTest();
  }

  // PY-45459
  public void testFStringLikeCompletionAvailableRightAfterOpeningBrace() {
    myFixture.configureByFile(getTestName(true) + ".py");
    LookupElement[] variants = myFixture.completeBasic();
    assertNotNull(variants);
    assertTrue(variants.length > 0);
    assertTrue(ContainerUtil.exists(variants, v -> v.getLookupString().equals("my_expr")));
  }

  // PY-46178
  public void testFStringLikeCompletionInsideUrl() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "city");
  }

  // PY-46056
  public void testImportCompletionHintForSameDirectoryModuleInOrdinaryPackage() {
    doTestVariantTailText("ordinaryPackage/sample.py", "logging", null);
  }

  // PY-46056
  public void testImportCompletionHintForSameDirectoryModuleInPlainDirectory() {
    doTestVariantTailText("plainDirectory/sample.py", "logging", " (plainDirectory)");
  }

  // PY-46056
  public void testFromImportCompletionHintForSameDirectoryModuleInOrdinaryPackage() {
    doTestVariantTailText("ordinaryPackage/sample.py", "logging", null);
  }

  // PY-46056
  public void testFromImportCompletionHintForSameDirectoryModuleInPlainDirectory() {
    doTestVariantTailText("plainDirectory/sample.py", "logging", " (plainDirectory)");
  }

  private void doTestVariantTailText(@NotNull String entryFilePath, @NotNull String variantName, @Nullable String tailText) {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile(entryFilePath);
    LookupElement[] variants = myFixture.completeBasic();
    assertNotNull(variants);
    LookupElement lookupElement = ContainerUtil.find(variants, v -> v.getLookupString().equals(variantName));
    assertNotNull(lookupElement);
    assertEquals(tailText, TestLookupElementPresentation.renderElement(lookupElement).getTailText());
  }

  // PY-46054
  public void testFromImportFromSameDirectoryModule() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("foo_bar/sample.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(true) + "/foo_bar/sample.after.py");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion";
  }
}
