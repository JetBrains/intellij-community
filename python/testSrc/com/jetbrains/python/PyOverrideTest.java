// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.override.PyMethodMember;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


public class PyOverrideTest extends PyTestCase {

  private void doTest() {
    doTest(null);
  }

  private void doTest(@Nullable String subClassName, String @NotNull ... methodName) {
    myFixture.configureByFiles(getTestName(true) + ".py");
    doOverride(subClassName, methodName);
    myFixture.checkResultByFile(getTestName(true) + "_after.py", true);
  }

  private void doOverride(@Nullable String subClassName, String @NotNull ... methodNames) {
    TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile());
    PyClass subClass;
    if (subClassName == null) {
      subClass = ContainerUtil.getLastItem(((PyFile)myFixture.getFile()).getTopLevelClasses());
    }
    else {
      subClass = myFixture.findElementByText(subClassName, PyClass.class);
    }
    assertNotNull(subClass);
    List<PyFunction> methodsToOverride;
    if (methodNames.length == 0) {
      List<PyClass> ancestorClasses = subClass.getAncestorClasses(typeEvalContext);
      assertNotEmpty(ancestorClasses);
      methodsToOverride = Collections.singletonList(ancestorClasses.get(0).getMethods()[0]);
    }
    else {
      methodsToOverride = ContainerUtil.map(methodNames, name -> subClass.findMethodByName(name, true, typeEvalContext));
    }
    methodsToOverride.forEach(TestCase::assertNotNull);
    List<PyFunction> implToOverride = ContainerUtil.map(
      methodsToOverride, method -> ObjectUtils.chooseNotNull(PyiUtil.getImplementation(method, typeEvalContext), method)
    );
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), subClass, ContainerUtil.map(implToOverride, PyMethodMember::new), false);
  }

  /**
   * Ensures loops in class hierarchy does not lead to SO
   */
  public final void testCircle() {
    doTest();
  }

  public void testOldStyleClassInPython2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  public void testClassmethod() {
    doTest();
  }

  public void testStaticMethod() {
    doTest();
  }

  public void testNewStyleClassInPython2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::doTest);
  }

  public void testReturnValue() {  // PY-1537
    doTest();
  }

  public void testClassmethodNewStyle() {  // PY-1811
    doTest();
  }

  public void testIndent() {  // PY-1796
    doTest("B");
  }

  public void testInnerClass() {  // PY-10976
    doTest("Inner");
  }

  public void testInnerFunctionClass() {
    doTest("B");
  }

  public void testQualified() {  // PY-2171
    doTest();
  }

  public void testImplement() {
    doTest("B", "my_method");
  }

  // PY-4418
  public void testProperty() {
    doTest();
  }

  // PY-11127
  public void testOverriddenMethodRaisesNotImplementedError() {
    doTest();
  }

  // PY-11127
  public void testOverriddenMethodRaisesNotImplementedErrorNoInstance() {
    doTest();
  }

  // PY-25906
  public void testImplementationOrder() {
    doTest("Impl", "foo", "bar");
  }

  public void testPy3k() {
    doTest();
  }

  // PY-15629
  public void testStaticMethodPy3k() {
    doTest();
  }

  // PY-15629
  public void testDunderNewPy3k() {
    doTest();
  }

  // PY-15629
  public void testDunderNew() {
    doTest();
  }

  public void testTypeAnnotations() {  // PY-2547
    doTest();
  }

  public void testReturnAnnotation() {  // PY-2690
    doTest();
  }

  // PY-18553
  public void testImportsForTypeAnnotations1() {
    doTestImportsForTypeAnnotations();
  }

  public void testImportsForTypeAnnotations2() {
    doTestImportsForTypeAnnotations();
  }

  public void testImportsForTypeAnnotations3() {
    doTestImportsForTypeAnnotations();
  }

  private void doTestImportsForTypeAnnotations() {
    myFixture.configureByFiles(getTestName(true) + ".py", getTestName(true) + "_import.py");
    doOverride(null);
    myFixture.checkResultByFile(getTestName(true) + "_after.py", true);
  }

  public void testSingleStar() {  // PY-6455
    doTest();
  }

  public void testStarArgs() {  // PY-6455
    doTest();
  }

  public void testKwargs() {  // PY-7401
    doTest();
  }

  public void testDocstring() {
    doTest();
  }

  // PY-10229
  public void testInstanceCheck() {
    doTest("MyType", "__instancecheck__");
  }

  // PY-19312
  public void testAsyncMethod() {
    doTest();
  }

  // PY-30287
  public void testMethodWithOverloadsInTheSameFile() {
    doTest();
  }

  // PY-30287
  public void testMethodWithOverloadsInAnotherFile() {
    myFixture.configureByFiles(getTestName(true) + ".py", getTestName(true) + "_parent.py");
    doOverride("B");
    myFixture.checkResultByFile(getTestName(true) + "_after.py", true);
  }

  // PY-35512
  public void testPositionalOnlyParameters() {
    doTest("B");
  }

  // PY-34493
  public void testAnnotationNotCopiedFromPyiStubs() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.py");
    doOverride(null);
    myFixture.checkResultByFile(getTestName(false) + "/main_after.py", true);
  }

  // PY-34493
  public void testAnnotationsAreCopiedFromPyiStubToPyiStub() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.pyi");
    doOverride(null);
    myFixture.checkResultByFile(getTestName(false) + "/main_after.pyi", true);
  }

  // PY-34493
  public void testAnnotationsAreCopiedFromThirdPartyLibraries() {
    runWithAdditionalClassEntryInSdkRoots(getTestName(false) + "/lib", () -> {
      myFixture.copyDirectoryToProject(getTestName(false) + "/src", "");
      myFixture.configureByFile("main.py");
      doOverride(null);
      myFixture.checkResultByFile(getTestName(false) + "/src/main_after.py", true);
    });
  }

  public void testImportForParameterDefaultValue() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("main.py");
    doOverride(null);
    myFixture.checkResultByFile(getTestName(false) + "/main_after.py", true);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/override";
  }
}
