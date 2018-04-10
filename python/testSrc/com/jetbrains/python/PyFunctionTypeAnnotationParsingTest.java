/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.testFramework.ParsingTestCase;
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect;
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationParserDefinition;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotationFile;
import com.jetbrains.python.documentation.doctest.PyDocstringTokenSetContributor;
import com.jetbrains.python.inspections.PythonVisitorFilter;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyFunctionTypeAnnotationParsingTest extends ParsingTestCase {
  public PyFunctionTypeAnnotationParsingTest() {
    super("functionTypeComment/parsing", "functionTypeComment", new PyFunctionTypeAnnotationParserDefinition(), new PythonParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor.class);
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PyDocstringTokenSetContributor());
    PythonDialectsTokenSetProvider.reset();
  }

  @Override
  protected void tearDown() throws Exception {
    // clear cached extensions
    PythonVisitorFilter.INSTANCE.removeExplicitExtension(PythonLanguage.INSTANCE, (visitorClass, file) -> false);
    PythonVisitorFilter.INSTANCE.removeExplicitExtension(PyFunctionTypeAnnotationDialect.INSTANCE, (visitorClass, file) -> false);
    super.tearDown();
  }

  protected void doCodeTest(@NotNull String typeAnnotation) {
    try {
      super.doCodeTest(typeAnnotation);
    }
    catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Nullable
  private PyFunctionTypeAnnotation getParsedAnnotation() {
    final PyFunctionTypeAnnotationFile file = assertInstanceOf(myFile, PyFunctionTypeAnnotationFile.class);
    return file.getAnnotation();
  }

  public void testEmpty() {
    doCodeTest("() -> None");

    final PyFunctionTypeAnnotation annotation = getParsedAnnotation();
    assertNotNull(annotation);
    assertEmpty(annotation.getParameterTypeList().getParameterTypes());
    final PyExpression returnType = annotation.getReturnType();
    assertNotNull(returnType);
    assertEquals("None", returnType.getText());
  }

  public void testSimple() {
    doCodeTest("(int, str) -> None");
  }

  public void testVarargs() {
    doCodeTest("(*int, **str) -> None");
  }

  public void testEllipsis() {
    doCodeTest("(...) -> None");
    final PyFunctionTypeAnnotation annotation = getParsedAnnotation();
    final List<PyExpression> paramTypes = annotation.getParameterTypeList().getParameterTypes();
    assertSize(1, paramTypes);
    assertInstanceOf(paramTypes.get(0), PyNoneLiteralExpression.class);
    final PyExpression returnType = annotation.getReturnType();
    assertNotNull(returnType);
  }

  public void testNoReturnType() {
    doCodeTest("(int) -> ");
    final PyFunctionTypeAnnotation annotation = getParsedAnnotation();
    final List<PyExpression> paramTypes = annotation.getParameterTypeList().getParameterTypes();
    assertSize(1, paramTypes);
    assertNull(annotation.getReturnType());
  }

  public void testNoArrowAndReturnType() {
    doCodeTest("(int)");
  }

  public void testNoClosingParenthesis() {
    doCodeTest("(int");
  }

  public void testDanglingComma() {
    doCodeTest("(int,) -> None");
  }

  public void testEmptyFunctionType() {
    doCodeTest("");
    assertNull(getParsedAnnotation());
  }

  public void testNoTypeAfterStar() {
    doCodeTest("(*) -> int");
  }

  public void testLambdaAsFirstType() {
    doCodeTest("(lambda: 42");
  }

  public void testDefAsFirstType() {
    doCodeTest("(def foo)");
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }
}
