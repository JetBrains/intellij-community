// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.override.PyMethodMember;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public class PyOverrideTest extends PyTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  private void doTest() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFunction toOverride = getTopLevelClass(0).getMethods() [0];
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), getTopLevelClass(1),
                                            Collections.singletonList(new PyMethodMember(toOverride)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  private void doTest3k() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  private PyClass getTopLevelClass(int index) {
    PyFile file = (PyFile) myFixture.getFile();
    return file.getTopLevelClasses().get(index);
  }

  /**
   * Ensures loops in class hierarchy does not lead to SO
   */
  public final void testCircle() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }

  public void testClassmethod() {
    doTest();
  }

  public void testStaticMethod() {
    doTest();
  }

  public void testNewStyle() {
    doTest();
  }

  public void testReturnValue() {  // PY-1537
    doTest();
  }

  public void testClassmethodNewStyle() {  // PY-1811
    doTest();
  }

  public void testIndent() {  // PY-1796
    doTest();
  }

  public void testInnerClass() {  // PY-10976
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFunction toOverride = getTopLevelClass(0).getMethods()[0];
    PyClass pyClass = getTopLevelClass(1).getNestedClasses()[0];
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), pyClass,
                                            Collections.singletonList(new PyMethodMember(toOverride)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  public void testInnerFunctionClass() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFunction toOverride = getTopLevelClass(0).getMethods()[0];
    final PsiElement element = myFixture.getElementAtCaret();
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), PsiTreeUtil.getParentOfType(element, PyClass.class, false),
                                            Collections.singletonList(new PyMethodMember(toOverride)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  public void testQualified() {  // PY-2171
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyClass cls = PyClassNameIndex.findClass("turtle.TurtleScreenBase", myFixture.getProject());
    assertNotNull(cls);
    PyFunction initMethod = cls.findMethodByName(PyNames.INIT, false, null);
    assertNotNull(initMethod);
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), getTopLevelClass(0),
                                            Collections.singletonList(new PyMethodMember(initMethod)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  public void testImplement() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFunction toImplement = getTopLevelClass(0).getMethods()[1];
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), getTopLevelClass(1),
                                            Collections.singletonList(new PyMethodMember(toImplement)), true);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  // PY-4418
  public void testProperty() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    PyFunction toImplement = getTopLevelClass(0).getMethods()[0];
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), getTopLevelClass(1),
                                            Collections.singletonList(new PyMethodMember(toImplement)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
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
    myFixture.configureByFile("override/" + getTestName(true) + ".py");

    final PyFunction[] toImplement = getTopLevelClass(0).getMethods();
    assertEquals(Arrays.asList("foo", "bar"), ContainerUtil.map(toImplement, PyFunction::getName));

    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(),
                                            getTopLevelClass(1),
                                            ContainerUtil.map(toImplement, PyMethodMember::new),
                                            true);

    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  public void testPy3k() {
    doTest3k();
  }

  // PY-15629
  public void testStaticMethodPy3k() {
    doTest3k();
  }

  // PY-15629
  public void testDunderNewPy3k() {
    doTest3k();
  }

  // PY-15629
  public void testDunderNew() {
    doTest();
  }

  public void testTypeAnnotations() {  // PY-2547
    doTest3k();
  }

  public void testReturnAnnotation() {  // PY-2690
    doTest3k();
  }

  // PY-18553
  public void testImportsForTypeAnnotations1() {
    testImportsForTypeAnnotations(getTestName(true), 0);
  }

  public void testImportsForTypeAnnotations2() {
    testImportsForTypeAnnotations(getTestName(true), 0);
  }

  public void testImportsForTypeAnnotations3() {
    testImportsForTypeAnnotations(getTestName(true), 2);
  }

  private void testImportsForTypeAnnotations(String testName, int orderOfClassToOverride) {

    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> {
      final String initialFilePath = String.format("override/%s.py", testName);
      final String importFilePath = String.format("override/%s_import.py", testName);
      final String resultFilePath = String.format("override/%s_after.py", testName);

      List<PyFile> pyFiles = ContainerUtil.map(myFixture.configureByFiles(initialFilePath, importFilePath), PyFile.class::cast);

      PyFunction toOverride = pyFiles.get(1).getTopLevelClasses().get(orderOfClassToOverride).getMethods()[0];
      PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), getTopLevelClass(0),
                                              Collections.singletonList(new PyMethodMember(toOverride)), false);
      myFixture.checkResultByFile(resultFilePath, true);
    });

  }

  public void testSingleStar() {  // PY-6455
    doTest3k();
  }

  public void testStarArgs() {  // PY-6455
    doTest3k();
  }

  public void testKwargs() {  // PY-7401
    doTest3k();
  }

  public void testDocstring() {
    doTest();
  }

  // PY-10229
  public void testInstanceCheck() {
    myFixture.configureByFile("override/" + getTestName(true) + ".py");
    final PyClass cls = getTopLevelClass(0);
    final PyFunction method = cls.findMethodByName("__instancecheck__", true, null);
    assertNotNull(method);
    PyOverrideImplementUtil.overrideMethods(myFixture.getEditor(), cls, Collections.singletonList(new PyMethodMember(method)), false);
    myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
  }

  // PY-19312
  public void testAsyncMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest());
  }

  // PY-30287
  public void testMethodWithOverloadsInTheSameFile() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        myFixture.configureByFile("override/" + getTestName(true) + ".py");

        PyOverrideImplementUtil.overrideMethods(
          myFixture.getEditor(),
          getTopLevelClass(1),
          Collections.singletonList(new PyMethodMember(getTopLevelClass(0).getMethods()[2])),
          false
        );

        myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
      }
    );
  }

  // PY-30287
  public void testMethodWithOverloadsInAnotherFile() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PsiFile[] files = myFixture.configureByFiles(
          "override/" + getTestName(true) + ".py",
          "override/" + getTestName(true) + "_parent.py"
        );

        PyOverrideImplementUtil.overrideMethods(
          myFixture.getEditor(),
          getTopLevelClass(0),
          Collections.singletonList(new PyMethodMember(((PyFile)files[1]).getTopLevelClasses().get(0).getMethods()[2])),
          false
        );

        myFixture.checkResultByFile("override/" + getTestName(true) + "_after.py", true);
      }
    );
  }

  // PY-35512
  public void testPositionalOnlyParameters() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, this::doTest);
  }
}
