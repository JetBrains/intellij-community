/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyUnresolvedReferencesInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnresolvedReferencesInspection/";

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

  public void testSlots() {
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
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  // PY-7389
  public void testComprehensionScope27() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  // PY-7389
  public void testComprehensionScope33() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
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

  // PY-7708
  public void testXReadLinesForOpen() {
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

  public void testBytesIORead() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  private void doMultiFileTest(@NotNull String filename) {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject(TEST_DIRECTORY + testName, "");
    myFixture.configureFromTempProjectFile(filename);
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
