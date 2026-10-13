// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.allure.Components;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationParserDefinition;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotationFile;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Mikhail Golubev
 */
@Subsystems.CodeInsight
@Components.Parsing
@Layers.Functional
@TestApplication
public class PyFunctionTypeAnnotationParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PythonTestUtil.getTestDataPath() + "/functionTypeComment/parsing", "functionTypeComment", false,
                   new PyFunctionTypeAnnotationParserDefinition(), new PythonParserDefinition());

  private void doCodeTest(@NotNull String typeAnnotation) {
    parsingFixture.get().doCodeTest(typeAnnotation);
  }

  @Nullable
  private PyFunctionTypeAnnotation getParsedAnnotation() {
    final PyFunctionTypeAnnotationFile file = assertInstanceOf(PyFunctionTypeAnnotationFile.class, parsingFixture.get().getFile());
    return file.getAnnotation();
  }

  @Test
  public void testEmpty() {
    doCodeTest("() -> None");

    ReadAction.runBlocking(() -> {
      final PyFunctionTypeAnnotation annotation = getParsedAnnotation();
      assertNotNull(annotation);
      assertTrue(annotation.getParameterTypeList().getParameterTypes().isEmpty());
      final PyExpression returnType = annotation.getReturnType();
      assertNotNull(returnType);
      assertEquals("None", returnType.getText());
    });
  }

  @Test
  public void testSimple() {
    doCodeTest("(int, str) -> None");
  }

  @Test
  public void testVarargs() {
    doCodeTest("(*int, **str) -> None");
  }

  @Test
  public void testEllipsis() {
    doCodeTest("(...) -> None");
    ReadAction.runBlocking(() -> {
      final PyFunctionTypeAnnotation annotation = getParsedAnnotation();
      final List<PyExpression> paramTypes = annotation.getParameterTypeList().getParameterTypes();
      assertEquals(1, paramTypes.size());
      assertInstanceOf(PyEllipsisLiteralExpression.class, paramTypes.get(0));
      final PyExpression returnType = annotation.getReturnType();
      assertNotNull(returnType);
    });
  }

  @Test
  public void testNoReturnType() {
    doCodeTest("(int) -> ");
    ReadAction.runBlocking(() -> {
      final PyFunctionTypeAnnotation annotation = getParsedAnnotation();
      final List<PyExpression> paramTypes = annotation.getParameterTypeList().getParameterTypes();
      assertEquals(1, paramTypes.size());
      assertNull(annotation.getReturnType());
    });
  }

  @Test
  public void testNoArrowAndReturnType() {
    doCodeTest("(int)");
  }

  @Test
  public void testNoClosingParenthesis() {
    doCodeTest("(int");
  }

  @Test
  public void testDanglingComma() {
    doCodeTest("(int,) -> None");
  }

  @Test
  public void testEmptyFunctionType() {
    doCodeTest("");
    assertNull(ReadAction.computeBlocking(() -> getParsedAnnotation()));
  }

  @Test
  public void testNoTypeAfterStar() {
    doCodeTest("(*) -> int");
  }

  @Test
  public void testLambdaAsFirstType() {
    doCodeTest("(lambda: 42");
  }

  @Test
  public void testDefAsFirstType() {
    doCodeTest("(def foo)");
  }
}
