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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * User : ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/changeSignature/")
public class PyChangeSignatureTest extends PyTestCase {

  public void testChooseSuperMethod() {
    doChangeSignatureTest("baz", null);
  }

  public void testChangeFunctionName() {
    doChangeSignatureTest("bar", null);
  }

  public void testRemovePositionalParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false)));
  }

  public void testRemoveFirstPositionalParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(1, "b", null, false)));
  }

  public void testRemoveKeyedParam() { //PY-9753
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "tries", null, false),
                                              new PyParameterInfo(2, "delay", "3", true),
                                              new PyParameterInfo(3, "backoff", "1", true),
                                              new PyParameterInfo(4, "exceptions_to_check", "Exception", true),
                                              new PyParameterInfo(5, "retry_for_lambda", "None", true)));
  }

  public void testSwitchPositionalParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(1, "b", null, false), new PyParameterInfo(0, "a", null, false)));
  }

  public void testAddPositionalParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(1, "b", null, false),
                                              new PyParameterInfo(-1, "c", "3", false)));
  }

  public void testAddDefaultParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(1, "b", null, false),
                                              new PyParameterInfo(-1, "c", "3", true)));
  }

  public void testRemoveDefaultFromParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(1, "b", "2", false)));
  }

  public void testAddDefaultParam1() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(-1, "d", "1", true),
                                              new PyParameterInfo(1, "b", "None", true)));
  }

  public void testUpdateDocstring() {
    final PyParameterInfo a = new PyParameterInfo(0, "a", null, false);
    final PyParameterInfo d1 = new PyParameterInfo(1, "d", "1", true);
    d1.setName("d1");
    doChangeSignatureTest(null, Arrays.asList(a, d1));
  }

  public void testFixDocstringRemove() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false)));
  }

  public void testClassMethod() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "self", null, false), new PyParameterInfo(1, "a", null, true),
                                              new PyParameterInfo(-1, "b", "2", false)));
  }

  public void testKeywordParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                              new PyParameterInfo(-1, "b", "2", false)));
  }

  public void testParamAnnotation() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "b", null, false)), LanguageLevel.PYTHON32);
  }

  public void testKwArgs() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "param", null, false),
                                              new PyParameterInfo(1, "**kwargs", null, false)), LanguageLevel.PYTHON32);
  }

  public void testKeywordOnlyParams() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "param", null, false),
                                              new PyParameterInfo(-1, "*", null, false),
                                              new PyParameterInfo(-1, "a", "2", false)), LanguageLevel.PYTHON32);
  }

  public void testKeywordOnlyParamRemoveDefaultValue() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "my", "None", false),
                                              new PyParameterInfo(1, "*", null, false),
                                              new PyParameterInfo(2, "param", null, false)), LanguageLevel.PYTHON32);
  }

  public void testKeywordOnlyParamRemoveDefaultValue1() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "my", "None", false),
                                              new PyParameterInfo(1, "*", null, false),
                                              new PyParameterInfo(2, "param", "1", true)), LanguageLevel.PYTHON32);
  }

  public void testKeywordOnlyParamRemoveDefaultValue2() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "my", "None", true),
                                              new PyParameterInfo(1, "*", null, false),
                                              new PyParameterInfo(2, "param", "1", false)), LanguageLevel.PYTHON32);
  }

  public void testRenameOverriding() {
    doChangeSignatureTest("m1", Arrays.asList(new PyParameterInfo(0, "self", null, false)));
  }

  public void testDuplicateParam() {
    doChangeSignatureTest("some_function_name", Arrays.asList(new PyParameterInfo(0, "argument_1", null, false),
                                                              new PyParameterInfo(2, "opt2", "None", true),
                                                              new PyParameterInfo(3, "**extra_info", null, false)));
  }
  public void testMoveParam() {
    doChangeSignatureTest("f1", Arrays.asList(new PyParameterInfo(1, "b", "2", true),
                                                new PyParameterInfo(0, "a", "1", true)));
  }

  public void testMoveRenameParam() {
    final PyParameterInfo b = new PyParameterInfo(-1, "b", "1", false);
    final PyParameterInfo a = new PyParameterInfo(0, "a", "2", true);
    a.setName("a2");
    doChangeSignatureTest("foo", Arrays.asList(b,
                                               a));
  }

  public void testKeywordOnlyMove() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(2, "param2", null, false),
                                             new PyParameterInfo(0, "*", null, false),
                                             new PyParameterInfo(1, "param1", null, false)), LanguageLevel.PYTHON32);
  }

  public void testRenameAndMoveParam() {
    final PyParameterInfo p2 = new PyParameterInfo(1, "p2", null, false);
    final PyParameterInfo p1 = new PyParameterInfo(0, "p1", null, false);
    p1.setName("p");
    doChangeSignatureTest("f", Arrays.asList(p2,
                                             p1));
  }

  public void testEmptyParameterName() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(-1, "", "2", true)),
                     PyBundle.message("refactoring.change.signature.dialog.validation.parameter.name"));
  }

  public void testNonDefaultAfterDefault() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(-1, "a", "2", false), new PyParameterInfo(1, "b", "2", false)), null);
  }

  public void testNonDefaultAfterDefault1() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(1, "b", "1", true), new PyParameterInfo(-1, "a", "2", false)),
                     PyBundle.message("ANN.non.default.param.after.default"));
  }

  public void doChangeSignatureTest(@Nullable String newName, @Nullable List<PyParameterInfo> parameters) {
    myFixture.configureByFile("refactoring/changeSignature/" + getTestName(true) + ".before.py");
    changeSignature(newName, parameters);
    myFixture.checkResultByFile("refactoring/changeSignature/" + getTestName(true) + ".after.py");
  }

  private void doChangeSignatureTest(@Nullable String newName, @Nullable List<PyParameterInfo> parameters, LanguageLevel level) {
    setLanguageLevel(level);
    try {
      doChangeSignatureTest(newName, parameters);
    }
    finally {
      setLanguageLevel(null);
    }
  }

  public void doValidationTest(@Nullable String newName, @Nullable List<PyParameterInfo> parameters, @Nullable String expected) {
    myFixture.configureByFile("refactoring/changeSignature/" + getTestName(true) + ".py");
    final PyChangeSignatureHandler changeSignatureHandler = new PyChangeSignatureHandler();
    final PyFunction function = (PyFunction)changeSignatureHandler.findTargetMember(myFixture.getFile(), myFixture.getEditor());
    assertNotNull(function);

    final PyMethodDescriptor method = new PyMethodDescriptor(function);
    final TestPyChangeSignatureDialog dialog = new TestPyChangeSignatureDialog(function.getProject(), method);
    try {
      if (newName != null) {
        dialog.setNewName(newName);
      }
      if (parameters != null) {
        dialog.setParameterInfos(parameters);
      }

      final String validationError = dialog.validateAndCommitData();
      assertEquals(expected, validationError);
    }
    finally {
      Disposer.dispose(dialog.getDisposable());
    }
  }

  private void changeSignature(@Nullable String newName, @Nullable List<PyParameterInfo> parameters) {
    final PyChangeSignatureHandler changeSignatureHandler = new PyChangeSignatureHandler();
    final PyFunction function = (PyFunction)changeSignatureHandler.findTargetMember(
      myFixture.getFile(), myFixture.getEditor());
    assertNotNull(function);
    final PyFunction newFunction = PyChangeSignatureHandler.getSuperMethod(function);
    assertNotNull(newFunction);
    final PyMethodDescriptor method = new PyMethodDescriptor(newFunction);
    final TestPyChangeSignatureDialog dialog = new TestPyChangeSignatureDialog(newFunction.getProject(), method);
    try {
      if (newName != null) {
        dialog.setNewName(newName);
      }
      if (parameters != null) {
        dialog.setParameterInfos(parameters);
      }

      final String validationError = dialog.validateAndCommitData();
      assertTrue(validationError, validationError == null);

      final BaseRefactoringProcessor baseRefactoringProcessor = dialog.createRefactoringProcessor();
      assert baseRefactoringProcessor instanceof PyChangeSignatureProcessor;

      final PyChangeSignatureProcessor processor = (PyChangeSignatureProcessor)baseRefactoringProcessor;
      processor.run();
    }
    finally {
      Disposer.dispose(dialog.getDisposable());
    }
  }


  public static class TestPyChangeSignatureDialog extends PyChangeSignatureDialog {

    public TestPyChangeSignatureDialog(Project project, PyMethodDescriptor method) {
      super(project, method);
    }

    public void setNewName(String newName) {
      myNameField.setText(newName);
    }
  }
}
