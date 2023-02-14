// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyUnresolvedReferencesInspectionTest extends PyInspectionTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  public void testSelfReference() {
    doTest();
  }

  public void testUnresolvedImport() {
    doTest();
  }

  public void testStaticMethodParameter() {  // PY-663
    doTest();
  }

  public void testOverridesGetAttr() {  // PY-574
    doTest();
  }

  public void testUndeclaredAttrAssign() {  // PY-906
    doTest();
  }

  // PY-10397
  public void testOwnSlots() {
    doTest();
  }

  // PY-5939
  // PY-29229
  public void testSlotsAndInheritance() {
    doTest();
  }

  public void testSlotsWithDict() {
    doTest();
  }

  // PY-18422
  public void testSlotsAndClassAttr() {
    doTest();
  }

  public void testImportExceptImportError() {
    doTest();
  }

  public void testMro() {  // PY-3989
    doTest();
  }

  public void testConditionalImports() { // PY-983
    doMultiFileTest("a.py");
  }

  public void testHasattrGuard() { // PY-2309
    doTest();
  }

  public void testOperators() {
    doTest();
  }

  // PY-2308
  public void testTypeAssertions() {
    doTest();
  }

  public void testUnresolvedImportedModule() {  // PY-2075
    doTest();
  }

  public void testSuperType() {  // PY-2320
    doTest();
  }

  public void testImportFunction() {  // PY-1896
    doTest();
  }

  public void testSuperclassAsLocal() {  // PY-5427
    doTest();
  }

  public void testImportToContainingFile() {  // PY-4372
    doMultiFileTest("p1/m1.py");
  }

  public void testFromImportToContainingFile() {  // PY-4371
    doMultiFileTest("p1/m1.py");
  }

  public void testFromImportToContainingFile2() {  // PY-5945
    doMultiFileTest("p1/m1.py");
  }

  public void testPropertyType() {  // PY-5915
    doTest();
  }

  // PY-6316
  public void testNestedComprehensions() {
    doTest();
  }

  public void testCompoundDunderAll() {  // PY-6370
    doTest();
  }

  public void testFromPackageImportBuiltin() {
    doMultiFileTest("a.py");
  }

  // PY-2813
  public void testNamespacePackageAttributes() {
    doMultiFileTest("a.py");
  }

  // PY-6548
  public void testDocstring() {
    doTest();
  }

  public void testIvarInDocstring() {
    doTest();
  }

  // PY-6634
  public void testModuleAttribute() {
    doTest();
  }

  // PY-4748
  public void testStubAssignment() {
    doMultiFileTest("a.py");
  }

  // PY-7022
  public void testReturnedQualifiedReferenceUnionType() {
    doMultiFileTest("a.py");
  }

  // PY-2668
  public void testUnusedImportsInPackage() {
    doMultiFileTest("p1/__init__.py");
  }

  // PY-7032
  public void testDocstringArgsAndKwargs() {
    doTest();
  }

  // PY-7136
  public void testUnusedImportWithClassAttributeReassignment() {
    doTest();
  }

  public void testGetattrAttribute() {
    doTest();
  }

  // PY-7173
  public void testDecoratedFunction() {
    doTest();
  }

  // PY-7173
  public void testDecoratedClass() {
    doTest();
  }

  // PY-7043
  public void testDunderPackage() {
    doTest();
  }

  // PY-7214
  public void testBuiltinDerivedClassAttribute() {
    doTest();
  }

  // PY-7244
  public void testAttributesOfGenerics() {
    doTest();
  }

  // PY-5995
  public void testClassInClassBody() {
    doTest();
  }

  // PY-7315
  public void testImportUsedInDocString() {
    doTest();
  }

  // PY-6745
  public void testQualNameAttribute() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  // PY-7389
  public void testComprehensionScope27() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  // PY-7389
  public void testComprehensionScope33() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  // PY-7516
  public void testComprehensionInParameterValue() {
    doTest();
  }

  // PY-6617
  public void testAugAssignmentDefinedInOuterScope() {
    doTest();
  }

  // PY-7301
  public void testUnresolvedBaseClass() {
    doTest();
  }

  // PY-5427
  public void testBaseClassAssignment() {
    doTest();
  }

  // PY-4600
  public void testDynamicAttrsAnnotation() {
    doTest();
  }

  // PY-8063
  public void testAddForListComprehension() {
    doTest();
  }

  // PY-7805
  public void testUnderscoredBuiltin() {
    doTest();
  }

  // PY-9493
  public void testSuperObjectNew() {
    doTest();
  }

  // PY-7823
  public void testUnresolvedTopLevelInit() {
    doTest();
  }

  // PY-7694
  public void testNegativeAssertType() {
    doTest();
  }

  public void testNegativeIf() {
    doTest();
  }

  // PY-7614
  public void testNoseToolsDynamicMembers() {
    doMultiFileTest("a.py");
  }

  public void testDateTodayReturnType() {
    doMultiFileTest("a.py");
  }

  public void testObjectNewAttributes() {
    doTest();
  }

  // PY-10006
  public void testUnresolvedUnreachable() {
    doTest();
  }

  public void testNullReferenceInIncompleteImport() {
    doMultiFileTest("a.py");
  }

  // PY-10893
  public void testCustomNewReturnInAnotherModule() {
    doMultiFileTest("a.py");
  }

  public void testBytesIOMethods() {
    doTest();
  }

  // PY-18322
  public void testFileMethods() {
    doTest();
  }

  // PY-10977
  public void testContextManagerSubclass() {
    doTest();
  }

  // PY-11413
  public void testReturnSelfInSuperClass() {
    doTest();
  }

  // PY-6955
  public void testUnusedUnresolvedModuleImported() {
    doTest();
  }

  // PY-6955
  public void testUnusedUnresolvedNameImported() {
    doMultiFileTest();
  }

  // PY-6955
  public void testUnusedUnresolvedNameImportedSeveralTimes() {
    doMultiFileTest();
  }

  // PY-6955
  public void testUsedUnresolvedNameImportedSeveralTimes() {
    doMultiFileTest();
  }

  // PY-6955
  public void testUnusedUnresolvedPackageImported() {
    doTest();
  }

  // PY-13418
  public void testOneUnsedOneMarked() {
    doMultiFileTest();
  }

  // PY-13140
  public void testPrivateModuleNames() {
    doMultiFileTest();
  }

  // PY-9342, PY-13791
  public void testMethodSpecialAttributes() {
    doTest();
  }

  // PY-11472
  public void testUnusedImportBeforeStarImport() {
    doMultiFileTest();
  }

  // PY-13585
  public void testUnusedImportBeforeStarDunderAll() {
    doMultiFileTest();
  }

  // PY-12738
  public void testNamespacePackageNameDoesntMatchFileName() {
    doMultiFileTest();
  }

  // PY-13259
  public void testNestedNamespacePackageName() {
    doMultiFileTest();
  }

  // PY-11956
  public void testIgnoredUnresolvedReferenceInUnionType() {
    final String testName = getTestName(true);
    final String inspectionName = getInspectionClass().getSimpleName();
    myFixture.configureByFile("inspections/" + inspectionName + "/" + testName + ".py");
    myFixture.enableInspections(getInspectionClass());
    final String attrQualifiedName = "inspections." + inspectionName + "." + testName + ".A.foo";
    String quickFixName = PyBundle.message("QFIX.ignore.unresolved.reference.0", attrQualifiedName);
    final IntentionAction intentionAction = myFixture.findSingleIntention(quickFixName);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
  }

  protected VirtualFile prepareFile() {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    return myFixture.configureByFile(getTestDirectoryPath() + "/" + getTestName(true) + ".py").getVirtualFile();
  }

  protected void doEvaluateExpressionTest(@NotNull VirtualFile mainFile, @NotNull String expression, int lineNumber) {
    PsiElement element = PyDebuggerEditorsProvider.getContextElement(myFixture.getProject(),
                                                                     XSourcePositionImpl.create(mainFile, lineNumber));
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(myFixture.getProject(), "fragment.py", expression, true);
    fragment.setContext(element);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.enableInspections(getInspectionClass());
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
  }

  public void testEvaluateExpressionBuiltins() {
    VirtualFile mainFile = prepareFile();
    doEvaluateExpressionTest(mainFile, "len(a)", 2);
    doEvaluateExpressionTest(mainFile, "a", 2);
  }

  public void testEvaluateExpressionInsideFunction() {
    VirtualFile mainFile = prepareFile();
    doEvaluateExpressionTest(mainFile, "a", 3);
    doEvaluateExpressionTest(mainFile, "s", 3);
  }

  // PY-14309
  public void testEvaluateExpressionClass() {
    VirtualFile mainFile = prepareFile();
    doEvaluateExpressionTest(mainFile, "s", 4);
    doEvaluateExpressionTest(mainFile, "self", 4);
    doEvaluateExpressionTest(mainFile, "self.a", 4);
  }

  // PY-13554
  public void testDocstringTypeFromSubModule() {
    doMultiFileTest();
  }

  // PY-13969
  public void testStubsOfNestedClasses() {
    doMultiFileTest();
  }

  // PY-14359, PY-14158
  public void testInspectionSettingsSerializable() {
    final PyUnresolvedReferencesInspection inspection = new PyUnresolvedReferencesInspection();
    inspection.ignoredIdentifiers.add("foo.Bar.*");
    final Element serialized = new Element("tmp");
    inspection.writeSettings(serialized);
    assertTrue(JDOMUtil.writeElement(serialized).contains("foo.Bar.*"));
  }

  public void testMetaClassMembers() {
    doTest();
  }

  // PY-14398
  public void testImportToContainingFileInPackage() {
    doMultiFileTest("p1/__init__.py");
  }

  // PY-11401
  public void testNoUnresolvedReferencesForClassesWithBadMRO() {
    doTest();
  }

  // PY-11401
  public void testFallbackToOldStyleMROIfUnresolvedAncestorsAndC3Fails() {
    doTest();
  }

  // PY-11401
  public void testOverriddenMRO() {
    doTest();
  }

  // PY-11401
  public void testOverriddenMROInAncestors() {
    doTest();
  }

  // PY-15002
  public void testIncorrectFileLevelMetaclass() {
    doTest();
  }

  // PY-11541
  public void testBaseStringCheck() {
    doTest();
  }

  // PY-16146
  public void testUnresolvedSubscriptionOnClass() {
    doTest();
  }

  public void testBuiltinListGetItem() {
    doTest();
  }

  // PY-13395
  public void testPropertyNotListedInSlots() {
    doTest();
  }

  // PY-18751
  public void testStringWithFormatSyntax() {
    doTest();
  }

  // PY-18751
  public void testStringWithPercentSyntax() {
    doTest();
  }

  // PY-18254
  public void testVarargsAnnotatedWithFunctionComment() {
    doTest();
  }

  // PY-18521
  public void testFunctionTypeCommentUsesImportsFromTyping() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  // PY-22620
  public void testTupleTypeCommentsUseImportsFromTyping() {
    doTest();
  }

  // PY-13734
  public void testDunderClass() {
    doTest();
  }

  // PY-21224
  public void testSixWithMetaclass() {
    doTest();
  }

  // PY-21651
  public void testInstanceAttributeCreatedThroughWithStatement() {
    doTest();
  }

  // PY-21651
  public void testInstanceAttributeCreatedThroughWithStatementInAnotherFile() {
    doMultiFileTest();

    final VirtualFile fooVFile = myFixture.getFile().getVirtualFile().getParent().getChildren()[1];
    assertEquals("foo.py", fooVFile.getName());

    final PsiFile fooPsiFile = PsiManager.getInstance(myFixture.getProject()).findFile(fooVFile);
    assertNotParsed(fooPsiFile);
  }

  // PY-23164
  public void testInstanceAttributeCreatedInsideWithStatement() {
    doTest();
  }

  // PY-22828
  public void testNoProtectedBuiltinNames() {
    doTest();
  }

  // PY-22741, PY-22808
  public void testListIndexedByUnknownType() {
    doTest();
  }

  // PY-23540
  public void testMemberFromMetaclassWhenSuperclassMetaclassIsABCMeta() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  // PY-23623
  public void testCachedOperatorInRecursivelyTypeInference() {
    doTest();
  }

  // PY-25118
  public void testInnerClassAsNamedTupleDefinitionMember() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-5500
  public void testUnknownElementInDunderAll() {
    doTest();
  }

  // PY-15071
  public void testImportedPrivateNameListedInDunderAll() {
    doMultiFileTest();
  }

  // PY-25695
  public void testDynamicDunderAll() {
    doMultiFileTest();
  }

  public void testDunderAll() {
    doMultiFileTest();
  }

  // PY-25794
  public void testStubOnlyReExportedModule() {
    doMultiFileTest();
  }

  // PY-24637
  public void testPy2TrueInDocTest() {
    doTest();
  }

  // PY-20889
  public void testTypeAssertionInBooleanOperations() {
    doTest();
  }

  // PY-22312
  public void testUnionContainingUnknownType() {
    doTest();
  }

  // PY-26368
  public void testForwardReferencesInClassBody() {
    doTest();
  }

  // PY-7251
  public void testImportHighlightLevel() {
    doMultiFileTest();
  }

  // PY-26243
  public void testNotImportedModuleInDunderAll() {
    doMultiFileTest("pkg/__init__.py");
  }

  // PY-26243
  public void testNotImportedPackageInDunderAll() {
    doMultiFileTest("pkg/__init__.py");
  }

  // PY-27146
  public void testPrivateMemberOwnerResolvedToStub() {
    doMultiFileTest();
  }

  // PY-28017
  public void testModuleWithGetAttr() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, this::doMultiFileTest);
  }

  // PY-22868
  public void testStubWithGetAttr() {
    doMultiFileTest();
  }

  // PY-27913
  public void testDunderClassGetItem() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, this::doTest);
  }


  // PY-28332
  public void testIndirectFromImport() {
    doMultiFileTest();
  }

  // PY-18629
  public void testPreferImportedModuleOverNamespacePackage() {
    doMultiFileTest();
  }

  // PY-22221
  public void testFunctionInIgnoredIdentifiers() {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    final PsiFile currentFile = myFixture.configureFromTempProjectFile("a.py");

    final PyUnresolvedReferencesInspection inspection = new PyUnresolvedReferencesInspection();
    inspection.ignoredIdentifiers.add("mock.patch.*");

    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());

    assertProjectFilesNotParsed(currentFile);
    assertSdkRootsNotParsed(currentFile);
  }

  // PY-23632
  public void testMockPatchObject() {
    runWithAdditionalClassEntryInSdkRoots(
      getTestDirectoryPath() + "/lib",
      () -> {
        final PsiFile file = myFixture.configureByFile(getTestDirectoryPath() + "/a.py");
        configureInspection();
        assertSdkRootsNotParsed(file);
      }
    );
  }

  // PY-20197
  public void testClassLevelImportUsedInsideMethod() {
    doTestByText("""
                   class DateParser:
                       from datetime import datetime
                       def __init__(self):
                           self.value = self.datetime(2016, 1, 1)""");
  }

  // PY-19599
  public void testDefinedInParameterDefaultAndBody() {
    doTestByText("""
                   def f(p=(x for x in [])):
                       x = 1
                       return x""");
  }

  // PY-20530
  public void testSelfInAnnotationAndTypeComment() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTestByText("""
                           class A:
                               def f1(self) -> <error descr="Unresolved reference 'self'">self</error>.B:
                                   pass

                               def f2(self):
                                   # type: () -> <warning descr="Unresolved reference 'self'">self</warning>.B
                                   pass

                               def f3(self):
                                   v3: self.B
                                   v4 = None  # type: self.B

                               v1: <error descr="Unresolved reference 'self'">self</error>.B
                               v2 = None  # type: <warning descr="Unresolved reference 'self'">self</warning>.B

                               class B:
                                   pass""")
    );
  }

  // PY-30383
  public void testLambdaMember() {
    doTestByText("""
                   class SomeClass:
                       def __init__(self):
                           self.one = lambda x: True
                          \s
                       def some_method(self):
                           self.one.<warning descr="Cannot find reference 'abc' in 'function'">abc</warning>""");
  }

  public void testNamedTupleFunction() {
    doTest();
  }

  // PY-22508
  public void testFakesFromTypeshed() {
    doTestByText("print(<error descr=\"Unresolved reference 'function'\">function</error>)\n" +
                 "print(<error descr=\"Unresolved reference 'module'\">module</error>)");
  }

  // PY-29929
  public void testAttrsSpecialAttribute() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> doTestByText(
        """
          import attr

          @attr.s
          class C:
              a = attr.ib()

          print(C.__attrs_attrs__)
          print(C(1).__attrs_attrs__)"""
      )
    );
  }

  // PY-32927
  public void testPrefixExpressionOnClassHavingSkeletons() {
    doMultiFileTest();
  }

  // PY-35531
  public void testAttributeDefinedInOverloadedDunderInit() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> doTestByText("""
                           from typing import overload
                           class Example:
                               @overload
                               def __init__(self, **kwargs): ...
                               def __init__(self, *args, **kwargs):
                                   self.__data = None
                               def test(self):
                                   return self.__data""")
    );
  }

  // PY-36008
  public void testTypedDict() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("""
                           from typing import TypedDict
                           class X(TypedDict):
                               x: str
                           x = X(x='str')
                           x.clear()
                           x['x'] = 'rts'
                           x.<warning descr="Unresolved attribute reference 'clea' for class 'X'">clea</warning>()
                           x.<warning descr="Unresolved attribute reference 'x' for class 'X'">x</warning>()
                           x1: X = {'x1': 'str'}
                           x1['x1'] = 'rts'
                           x1.clear()
                           x1.<warning descr="Unresolved attribute reference 'clea' for class 'X'">clea</warning>()
                           x1.<warning descr="Unresolved attribute reference 'x' for class 'X'">x</warning>()""")
    );
  }

  // PY-31517
  public void testNameDefinedAndUsedInsideDocstring() {
    doTestByText("""
                   ""\"
                   >>> def foo(bar):
                   ...     print(bar)

                   >>> foo("Hello")
                   Hello
                   ""\"""");
  }

  // PY-37755 PY-2700
  public void testGlobalResolveAttribute() {
    doTest();
  }

  // PY-39078
  public void testNoneAttribute() {
    doTestByText("a = None\n" +
                 "a.<warning descr=\"Cannot find reference 'append' in 'None'\">append</warning>(10)");
  }

  // PY-39682
  public void testWildcardIgnorePatternReferenceForNestedBinaryModule() {
    runWithAdditionalClassEntryInSdkRoots(getTestDirectoryPath() + "/site-packages", () -> {
      runWithAdditionalClassEntryInSdkRoots(getTestDirectoryPath() + "/python_stubs", () -> {
        myFixture.configureByFile(getTestDirectoryPath() + "/a.py");
        final PyUnresolvedReferencesInspection inspection = new PyUnresolvedReferencesInspection();
        inspection.ignoredIdentifiers.add("pkg.*");
        myFixture.enableInspections(inspection);
        myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
        assertSdkRootsNotParsed(myFixture.getFile());
        assertProjectFilesNotParsed(myFixture.getFile());
      });
    });
  }

  // PY-44918
  public void testResolvePathImportToUserFile() {
    doMultiFileTest("resolvePathImportToUserFile.py");
  }

  // PY-48166
  public void testDisabledNumpyPyiStubs() {
    doMultiFileTest();
  }

  // PY-48166
  public void testEnabledNumpyPyiStubs() {
    if (!Registry.is("enable.numpy.pyi.stubs", false)) {
      Registry.get("enable.numpy.pyi.stubs").setValue(true, getTestRootDisposable());
    }
    doMultiFileTest();
  }

  // PY-48012
  public void testUnresolvedKeywordPattern() {
    runWithLanguageLevel(LanguageLevel.PYTHON310, this::doTest);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyUnresolvedReferencesInspection.class;
  }
}