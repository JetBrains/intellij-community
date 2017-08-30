/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyUnresolvedReferencesInspectionTest extends PyInspectionTestCase {

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

  public void testSlotsAndUnlistedAttrAssign() {
    doTest();
  }

  public void testSlotsSuperclass() {
    doTest();
  }

  public void testSlotsWithDict() {
    doTest();
  }

  // PY-10397
  public void testSlotsAndListedAttrAccess() {
    doTest();
  }

  // PY-18422
  public void testSlotsAndClassAttr() {
    doTest();
  }

  public void testSlotsSubclass() {  // PY-5939
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
    runWithLanguageLevel(LanguageLevel.PYTHON33, this::doTest);
  }

  // PY-7389
  public void testComprehensionScope27() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  // PY-7389
  public void testComprehensionScope33() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, this::doTest);
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
    final IntentionAction intentionAction = myFixture.findSingleIntention("Ignore unresolved reference '" + attrQualifiedName + "'");
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
    myFixture.copyDirectoryToProject("typing", "");
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  // PY-22620
  public void testTupleTypeCommentsUseImportsFromTyping() {
    myFixture.copyDirectoryToProject("typing", "");
    doTest();
  }

  // PY-13734
  public void testDunderClass() {
    doTest();
  }

  // PY-20071
  public void testNonexistentLoggerMethod() {
    doMultiFileTest();
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
    assertNotParsed((PyFile)fooPsiFile);
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
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
  }

  // PY-23623
  public void testCachedOperatorInRecursivelyTypeInference() {
    doTest();
  }

  // PY-25118
  public void testInnerClassAsNamedTupleDefinitionMember() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
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

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyUnresolvedReferencesInspection.class;
  }
}