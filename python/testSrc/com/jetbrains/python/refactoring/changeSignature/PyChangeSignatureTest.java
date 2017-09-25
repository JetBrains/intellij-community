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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User : ktisha
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
@TestDataPath("$CONTENT_ROOT/../testData/")
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

  public void testAddDefaultParamAtEnd() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(1, "b", null, false),
                                              new PyParameterInfo(-1, "c", "3", true)));
  }

  // PY-24607
  public void testAddDefaultParamBeforeAnotherDefault() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(-1, "d", "1", true),
                                              new PyParameterInfo(1, "b", "None", true)));
  }

  public void testRemoveDefaultFromParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(1, "b", "2", false)));
  }

  // PY-15143
  public void testRemoveDefaultFromParamWithoutReplacement() {
    final PyParameterInfo first = new PyParameterInfo(0, "arg", null, false);
    final PyParameterInfo second = new PyParameterInfo(-1, "vvv", "xxx", false);
    doValidationTest(null, Arrays.asList(first, second), PyBundle.message("refactoring.change.signature.dialog.validation.default.missing"));
  }

  public void testUpdateDocstring() {
    getIndentOptions().INDENT_SIZE = 2;
    final PyParameterInfo a = new PyParameterInfo(0, "a", null, false);
    final PyParameterInfo d1 = new PyParameterInfo(1, "d", "1", true);
    d1.setName("d1");
    doChangeSignatureTest(null, Arrays.asList(a, d1));
  }

  public void testFixDocstringRemove() {
    getIndentOptions().INDENT_SIZE = 2;
    runWithDocStringFormat(DocStringFormat.REST,
                           () -> doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false))));
  }

  // PY-9795
  public void testFixGoogleDocStringRemoveMultiple() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                                                                               new PyParameterInfo(3, "d", null, false))));
  }

  // PY-16761
  public void testGoogleDocStringRemoveVarargs() {
    runWithDocStringFormat(DocStringFormat.GOOGLE,
                           () -> doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "x", null, false))));
  }

  public void testFixSphinxDocStringRemoveMultiple() {
    runWithDocStringFormat(DocStringFormat.REST, () -> doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                                                                             new PyParameterInfo(3, "d", null, false))));
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

  // PY-8599
  public void testRenameStarredParameters() {
    final PyParameterInfo argsParam = new PyParameterInfo(1, "*args", null, false);
    argsParam.setName("*foo");
    final PyParameterInfo kwargsParam = new PyParameterInfo(2, "**kwargs", null, false);
    kwargsParam.setName("**bar");
    doChangeSignatureTest("func", Arrays.asList(new PyParameterInfo(0, "arg1", null, false), argsParam, kwargsParam), LanguageLevel.PYTHON32);
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

  public void testDecorator() {
    doChangeSignatureTest("decorator", Arrays.asList(new PyParameterInfo(0, "arg1", null, false)));
  }

  public void testNonDefaultAfterDefault() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(-1, "a", "2", false), new PyParameterInfo(0, "b", "2", false)), null);
  }

  public void testNonDefaultAfterDefault1() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(0, "b", "1", true), new PyParameterInfo(-1, "a", "2", false)),
                     PyBundle.message("ANN.non.default.param.after.default"));
  }

  // PY-14774
  public void testAnnotationsForStarredParametersAreNotShownInDialog() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> {
      myFixture.configureByText(PythonFileType.INSTANCE, "def func(a, b:int, *args: tuple, c:list, d:str='foo', ** kwargs:dict):\n" +
                                                         "    pass");
      final PyFunction function = (PyFunction)new PyChangeSignatureHandler().findTargetMember(myFixture.getFile(), myFixture.getEditor());
      assertNotNull(function);
      final List<String> expected = Arrays.asList("a", "b", "*args", "c", "d", "**kwargs");
      final List<PyParameterInfo> parameters = new PyMethodDescriptor(function).getParameters();
      assertEquals(expected, ContainerUtil.map(parameters, info -> info.getOldName()));
    });
  }

  public void testDuplicateNamesOfStarredParameters() {
    final PyParameterInfo firstParam = new PyParameterInfo(0, "*foo", null, false);
    firstParam.setName("*bar");
    doValidationTest(null, Arrays.asList(firstParam, new PyParameterInfo(1, "**bar", null, false)),
                     PyBundle.message("ANN.duplicate.param.name"));
  }

  public void testMultipleSingleStarredParameters() {
    final PyParameterInfo firstParam = new PyParameterInfo(0, "foo", null, false);
    firstParam.setName("*foo");
    doValidationTest(null, Arrays.asList(firstParam, new PyParameterInfo(1, "*bar", null, false)),
                     PyBundle.message("refactoring.change.signature.dialog.validation.multiple.star"));
  }

  public void testMultipleDoubleStarredParameters() {
    final PyParameterInfo firstParam = new PyParameterInfo(0, "foo", null, false);
    firstParam.setName("**foo");
    doValidationTest(null, Arrays.asList(firstParam, new PyParameterInfo(1, "**bar", null, false)),
                     PyBundle.message("refactoring.change.signature.dialog.validation.multiple.double.star"));
  }

  public void testParameterNameWithMoreThanTwoStars() {
    final PyParameterInfo firstParam = new PyParameterInfo(0, "**kwargs", null, false);
    firstParam.setName("***kwargs");
    doValidationTest(null, Arrays.asList(firstParam), PyBundle.message("refactoring.change.signature.dialog.validation.parameter.name"));
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementationChangeOverload() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doUnchangedSignatureTest);
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementationChangeImplementation() {
    doChangeSignatureTest(null, Collections.emptyList(), LanguageLevel.PYTHON35);
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementationChangeCall() {
    doChangeSignatureTest(null, Collections.emptyList(), LanguageLevel.PYTHON35);
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassChangeOverload() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doUnchangedSignatureTest);
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassChangeImplementation() {
    doChangeSignatureTest(null,
                          Collections.singletonList(new PyParameterInfo(0, "self", null, false)),
                          LanguageLevel.PYTHON35);
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassChangeCall() {
    doChangeSignatureTest(null,
                          Collections.singletonList(new PyParameterInfo(0, "self", null, false)),
                          LanguageLevel.PYTHON35);
  }

  // PY-24288
  public void testKeywordParameterAlreadyExists() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                              new PyParameterInfo(-1, "foo", "None", true),
                                              new PyParameterInfo(1, "**kwargs", null, false)));
  }

  // PY-24480
  public void testAddParameterBeforeVararg() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(-1, "x", "42", false),
                                              new PyParameterInfo(0, "*args", null, false)));
  }

  // PY-24479
  public void testRemoveParameterBeforeVararg() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                              new PyParameterInfo(2, "*args", null, false)));
  }

  // PY-16683
  public void testKeywordOnlyParameter() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> {
      doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                                new PyParameterInfo(1, "*args", null, false),
                                                new PyParameterInfo(-1, "foo", "None", false)));
    });
  }

  // PY-24602
  public void testScatteredKwargsArgsRemoveParam() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                             new PyParameterInfo(2, "**kwargs", null, false)));
  }

  // PY-24602
  public void testScatteredKwargsArgsRenameParam() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                             new PyParameterInfo(1, "bar", null, false),
                                             new PyParameterInfo(2, "**kwargs", null, false)));
  }

  // PY-24602
  public void testScatteredKwargsArgsRemoveParamBefore() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(1, "foo", null, false),
                                             new PyParameterInfo(2, "**kwargs", null, false)));
  }
  
  // PY-24602
  public void testScatteredKwargsArgsAddParamAfter() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                             new PyParameterInfo(1, "foo", null, false),
                                             new PyParameterInfo(-1, "y", "None", false),
                                             new PyParameterInfo(2, "**kwargs", null, false)));
  }
  
  // PY-24602
  public void testScatteredKwargsArgsAddParamAfterWithDefault() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                             new PyParameterInfo(1, "foo", null, false),
                                             new PyParameterInfo(-1, "y", "None", true),
                                             new PyParameterInfo(2, "**kwargs", null, false)));
  }
  
  // PY-24602
  public void testScatteredKwargsArgsSwapParams() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(0, "foo", null, false),
                                             new PyParameterInfo(1, "x", null, false),
                                             new PyParameterInfo(2, "**kwargs", null, false)));
  }

  // PY-24609
  public void testRemoveKeywordFromArgumentBeforeVararg() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(0, "y", null, false),
                                             new PyParameterInfo(2, "x", null, false),
                                             new PyParameterInfo(1, "*args", null, false)));
  }
  
  // PY-24609
  public void testKeepKeywordOfArgumentBeforeEmptyVararg() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(0, "y", null, false),
                                             new PyParameterInfo(2, "x", null, false),
                                             new PyParameterInfo(1, "*args", null, false)));
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

  private void doUnchangedSignatureTest() {
    myFixture.configureByFile("refactoring/changeSignature/" + getTestName(true) + ".before.py");
    assertNull(new PyChangeSignatureHandler().findTargetMember(myFixture.getFile(), myFixture.getEditor()));
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
    final PyFunction function = (PyFunction)changeSignatureHandler.findTargetMember(myFixture.getFile(), myFixture.getEditor());
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
