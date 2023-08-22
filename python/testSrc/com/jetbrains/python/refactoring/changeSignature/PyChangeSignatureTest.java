// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER;

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
                                              new PyParameterInfo(NEW_PARAMETER, "c", "3", false)));
  }

  public void testAddDefaultParamAtEnd() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(1, "b", null, false),
                                              new PyParameterInfo(NEW_PARAMETER, "c", "3", true)));
  }

  // PY-24607
  public void testNewParameterWithSignatureDefaultMakesSubsequentExistingParametersUseKeywordArguments() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                              new PyParameterInfo(NEW_PARAMETER, "d", "1", true),
                                              new PyParameterInfo(1, "b", "None", true)));
  }

  // PY-26715
  public void testMovedExistingParameterUsingSignatureDefaultMakesSubsequentExistingParametersUseKeywordArguments() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(1, "b", "None", true),
                                              new PyParameterInfo(0, "a", "None", true)));
  }

  public void testRemoveDefaultFromParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false), new PyParameterInfo(1, "b", "2", false)));
  }

  // PY-15143
  public void testRemoveDefaultFromParamWithoutReplacement() {
    final PyParameterInfo first = new PyParameterInfo(0, "arg", null, false);
    final PyParameterInfo second = new PyParameterInfo(NEW_PARAMETER, "vvv", "xxx", false);
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

  public void testOrdinaryMethod() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "self", null, false), new PyParameterInfo(1, "a", null, true),
                                              new PyParameterInfo(NEW_PARAMETER, "b", "2", false)));
  }

  // PY-33487
  public void testClassMethod() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "cls", null, false),
                                               new PyParameterInfo(2, "arg2", null, false),
                                               new PyParameterInfo(1, "arg1", null, false)));
  }

  // PY-30874
  public void testNonStandardSelfParameterName() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "s", null, false),
                                              new PyParameterInfo(2, "y", null, false),
                                              new PyParameterInfo(1, "x", null, false)));
  }

  public void testKeywordParam() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                              new PyParameterInfo(NEW_PARAMETER, "b", "2", false)));
  }

  public void testParamAnnotation() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "b", null, false)));
  }

  public void testKwArgs() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "param", null, false),
                                              new PyParameterInfo(1, "**kwargs", null, false)));
  }

  public void testKeywordOnlyParams() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "param", null, false),
                                              new PyParameterInfo(NEW_PARAMETER, PySingleStarParameter.TEXT, null, false),
                                              new PyParameterInfo(NEW_PARAMETER, "a", "2", false)));
  }

  public void testKeywordOnlyParamRemoveDefaultValue() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "my", "None", false),
                                              new PyParameterInfo(1, PySingleStarParameter.TEXT, null, false),
                                              new PyParameterInfo(2, "param", null, false)));
  }

  public void testKeywordOnlyParamRemoveDefaultValue1() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "my", "None", false),
                                              new PyParameterInfo(1, PySingleStarParameter.TEXT, null, false),
                                              new PyParameterInfo(2, "param", "1", true)));
  }

  public void testKeywordOnlyParamRemoveDefaultValue2() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "my", "None", true),
                                              new PyParameterInfo(1, PySingleStarParameter.TEXT, null, false),
                                              new PyParameterInfo(2, "param", "1", false)));
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
    final PyParameterInfo b = new PyParameterInfo(NEW_PARAMETER, "b", "1", false);
    final PyParameterInfo a = new PyParameterInfo(0, "a", "2", true);
    a.setName("a2");
    doChangeSignatureTest("foo", Arrays.asList(b,
                                               a));
  }

  public void testKeywordOnlyMove() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(2, "param2", null, false),
                                             new PyParameterInfo(0, PySingleStarParameter.TEXT, null, false),
                                             new PyParameterInfo(1, "param1", null, false)));
  }

  // PY-8599
  public void testRenameStarredParameters() {
    final PyParameterInfo argsParam = new PyParameterInfo(1, "*args", null, false);
    argsParam.setName("*foo");
    final PyParameterInfo kwargsParam = new PyParameterInfo(2, "**kwargs", null, false);
    kwargsParam.setName("**bar");
    doChangeSignatureTest("func", Arrays.asList(new PyParameterInfo(0, "arg1", null, false), argsParam, kwargsParam));
  }

  public void testRenameAndMoveParam() {
    final PyParameterInfo p2 = new PyParameterInfo(1, "p2", null, false);
    final PyParameterInfo p1 = new PyParameterInfo(0, "p1", null, false);
    p1.setName("p");
    doChangeSignatureTest("f", Arrays.asList(p2,
                                             p1));
  }

  public void testEmptyParameterName() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(NEW_PARAMETER, "", "2", true)),
                     PyBundle.message("refactoring.change.signature.dialog.validation.parameter.name"));
  }

  public void testDecorator() {
    doChangeSignatureTest("decorator", Arrays.asList(new PyParameterInfo(0, "arg1", null, false)));
  }

  // PY-8098
  public void testNewParameterWithCallDefaultBeforeExistingWithoutDefault() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(NEW_PARAMETER, "a", "2", false),
                                              new PyParameterInfo(0, "b", null, false)));
  }

  // PY-8096
  public void testNewParameterWithoutSignatureDefaultAfterExistingWithSignatureDefault() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(0, "b", "1", true),
                                         new PyParameterInfo(NEW_PARAMETER, "a", "2", false)),
                     PyPsiBundle.message("ANN.non.default.param.after.default"));
  }

  public void testNewParameterWithSignatureDefaultBeforeExistingWithoutSignatureDefault() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(NEW_PARAMETER, "a", "2", true),
                                         new PyParameterInfo(0, "b", null, false)),
                     PyPsiBundle.message("ANN.non.default.param.after.default"));
  }

  public void testNewParameterWithSignatureDefaultBeforeNewWithoutSignatureDefault() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(NEW_PARAMETER, "a", "2", true),
                                         new PyParameterInfo(NEW_PARAMETER, "b", null, false)),
                     PyPsiBundle.message("ANN.non.default.param.after.default"));
  }

  public void testMovingExistingParameterWithSignatureDefaultBeforeExistingWithoutSignatureDefault() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(1, "b", "1", true),
                                         new PyParameterInfo(0, "a", null, false)),
                     PyPsiBundle.message("ANN.non.default.param.after.default"));
  }

  // PY-14774
  public void testAnnotationsForStarredParametersAreNotShownInDialog() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, () -> {
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
                     PyPsiBundle.message("ANN.duplicate.param.name"));
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
    doChangeSignatureTest(null, Collections.emptyList());
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementationChangeCall() {
    doChangeSignatureTest(null, Collections.emptyList());
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassChangeOverload() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doUnchangedSignatureTest);
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassChangeImplementation() {
    doChangeSignatureTest(null, Collections.singletonList(new PyParameterInfo(0, "self", null, false)));
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassChangeCall() {
    doChangeSignatureTest(null, Collections.singletonList(new PyParameterInfo(0, "self", null, false)));
  }

  // PY-24288
  public void testKeywordParameterAlreadyExists() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                              new PyParameterInfo(NEW_PARAMETER, "foo", "None", true),
                                              new PyParameterInfo(1, "**kwargs", null, false)));
  }

  // PY-24480
  public void testAddParameterBeforeVararg() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(NEW_PARAMETER, "x", "42", false),
                                              new PyParameterInfo(0, "*args", null, false)));
  }

  // PY-24479
  public void testRemoveParameterBeforeVararg() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                              new PyParameterInfo(2, "*args", null, false)));
  }

  // PY-16683
  public void testKeywordOnlyParameter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () ->
        doChangeSignatureTest(null,
                              Arrays.asList(
                                new PyParameterInfo(0, "x", null, false),
                                new PyParameterInfo(1, "*args", null, false),
                                new PyParameterInfo(NEW_PARAMETER, "foo", "None", false)
                              )
        )
    );
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
                                             new PyParameterInfo(NEW_PARAMETER, "y", "None", false),
                                             new PyParameterInfo(2, "**kwargs", null, false)));
  }

  // PY-24602
  public void testScatteredKwargsArgsAddParamAfterWithDefault() {
    doChangeSignatureTest("f", Arrays.asList(new PyParameterInfo(0, "x", null, false),
                                             new PyParameterInfo(1, "foo", null, false),
                                             new PyParameterInfo(NEW_PARAMETER, "y", "None", true),
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

  // PY-38076
  public void testUsedDictionaryUnpackingPreserved() {
    doChangeSignatureTest("func", Arrays.asList(new PyParameterInfo(0, "foo", null, false),
                                                new PyParameterInfo(1, "bar", null, false),
                                                new PyParameterInfo(NEW_PARAMETER, "baz", "42", true),
                                                new PyParameterInfo(2, "**kwargs", null, false)));
  }

  // PY-38076
  public void testUnusedDictionaryUnpackingRemoved() {
    doChangeSignatureTest("func", Arrays.asList(new PyParameterInfo(0, "foo", null, false),
                                                new PyParameterInfo(1, "bar", null, false)));
  }

  // PY-22023
  public void testOtherDunderInitInHierarchyNotModified() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "self", null, false),
                                              new PyParameterInfo(1, "foo", null, false),
                                              new PyParameterInfo(NEW_PARAMETER, "bar", "42", false)));
  }

  // PY-22023
  public void testOtherDunderNewInHierarchyNotModified() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "cls", null, false),
                                              new PyParameterInfo(1, "foo", null, false),
                                              new PyParameterInfo(NEW_PARAMETER, "bar", "42", false)));
  }

  // PY-41230
  public void testPositionalOnlyMarkerTurnsKeywordArgumentIntoPositional() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                              new PyParameterInfo(NEW_PARAMETER, PySlashParameter.TEXT, null, false),
                                              new PyParameterInfo(1, "b", null, false)));
  }

  // PY-41230
  public void testPositionalOnlyMarkerPropagatesExistingParameterSignatureDefaultToCall() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", "None", true),
                                              new PyParameterInfo(1, "b", "None", true),
                                              new PyParameterInfo(NEW_PARAMETER, PySlashParameter.TEXT, null, false)));
  }

  // PY-41230
  public void testPositionalOnlyMarkerPropagatesNewParameterSignatureDefaultToCall() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(NEW_PARAMETER, "a", "None", true),
                                              new PyParameterInfo(0, "b", "None", true),
                                              new PyParameterInfo(1, PySlashParameter.TEXT, null, false)));
  }

  // PY-42682
  public void testAddPositionalVarargToKeywordVararg() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(NEW_PARAMETER, "*args", null, false),
                                              new PyParameterInfo(0, "**kwargs", null, false)));
  }

  // PY-42682
  public void testAddKeywordVarargAsLastParameter() {
    doChangeSignatureTest(null, Arrays.asList(new PyParameterInfo(0, "a", "1", true),
                                              new PyParameterInfo(NEW_PARAMETER, "**kwargs", null, false)));
  }

  public void testPositionalOnlyMarkerAsFirstParameter() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(NEW_PARAMETER, PySlashParameter.TEXT, null, false),
                                         new PyParameterInfo(0, "a", null, false)),
                     PyPsiBundle.message("ANN.named.parameters.before.slash"));
  }

  public void testDuplicatedPositionalOnlyMarker() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                         new PyParameterInfo(1, PySlashParameter.TEXT, null, false),
                                         new PyParameterInfo(2, "b", null, false),
                                         new PyParameterInfo(NEW_PARAMETER, PySlashParameter.TEXT, null, false)),
                     PyPsiBundle.message("ANN.multiple.slash"));
  }

  public void testPositionalOnlyMarkerAfterPositionalVararg() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(0, "*args", null, false),
                                         new PyParameterInfo(NEW_PARAMETER, PySlashParameter.TEXT, null, false)),
                     PyPsiBundle.message("ANN.slash.param.after.vararg"));
  }

  public void testPositionalOnlyMarkerAfterKeywordOnlyMarker() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                         new PyParameterInfo(1, PySingleStarParameter.TEXT, null, false),
                                         new PyParameterInfo(2, "b", null, false),
                                         new PyParameterInfo(NEW_PARAMETER, PySlashParameter.TEXT, null, false)),
                     PyPsiBundle.message("ANN.slash.param.after.vararg"));
  }

  public void testPositionalOnlyMarkerAfterKeywordVararg() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(0, "**kwargs", null, false),
                                         new PyParameterInfo(NEW_PARAMETER, PySlashParameter.TEXT, null, false)),
                     PyPsiBundle.message("ANN.slash.param.after.keyword"));
  }

  public void testDuplicatedKeywordOnlyMarker() {
    doValidationTest(null, Arrays.asList(new PyParameterInfo(0, "a", null, false),
                                         new PyParameterInfo(1, PySingleStarParameter.TEXT, null, false),
                                         new PyParameterInfo(2, "b", null, false),
                                         new PyParameterInfo(NEW_PARAMETER, PySingleStarParameter.TEXT, null, false)),
                     PyBundle.message("refactoring.change.signature.dialog.validation.multiple.star"));
  }

  public void doChangeSignatureTest(@Nullable String newName, @Nullable List<PyParameterInfo> parameters) {
    myFixture.configureByFile("refactoring/changeSignature/" + getTestName(true) + ".before.py");
    changeSignature(newName, parameters);
    myFixture.checkResultByFile("refactoring/changeSignature/" + getTestName(true) + ".after.py");
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
    final PyFunction newFunction;
    // Accept modifying the base method
    final TestDialog oldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK);
    try {
      newFunction = PyChangeSignatureHandler.getSuperMethod(function);
    }
    finally {
      TestDialogManager.setTestDialog(oldTestDialog);
    }
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
      assertNull(validationError);

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
