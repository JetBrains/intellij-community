// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.types.PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY;

public final class PyTypeParameterMappingTest extends PyTestCase {
  private static final Pattern TYPE_VAR_NAME = Pattern.compile("[A-Z_][A-Z0-9_]*");
  private static final Pattern TYPE_VAR_TUPLE_NAME = Pattern.compile("[A-Z_][A-Z0-9_]*s");

  public void testCompleteMatchOfNonVariadicTypes() {
    doTestShapeMapping("int, T", "T2, str",
                       """
             int -> T2
             T -> str
             """);
  }

  public void testMatchingNoTypesToSomeTypes() {
    doNegativeShapeMappingTest("", "int");
  }

  public void testMatchingSomeTypesToNoTypes() {
    doNegativeShapeMappingTest("int", "");
  }

  public void testMatchingTypeVarTupleAtTheBeginning() {
    doTestShapeMapping("*Ts, T", "int, T2, str", """
      *Ts -> *tuple[int, T2]
      T -> str
      """);
  }

  public void testMatchingTypeVarTupleAtTheEnd() {
    doTestShapeMapping("T, *Ts", "int, T2, str", """
      T -> int
      *Ts -> *tuple[T2, str]
      """);
  }

  public void testMatchingTypeVarTupleInTheMiddle() {
    doTestShapeMapping("T, *Ts, int", "int, T2, str, int", """
      T -> int
      *Ts -> *tuple[T2, str]
      int -> int
      """);
  }

  public void testExtractingElementsFromUnboundTupleType() {
    doTestShapeMapping("T1, T2, str", "*tuple[int, ...], str", """
      T1 -> int
      T2 -> int
      str -> str
      """);
  }

  public void testFlatteningActualTypes() {
    doTestShapeMapping("T1, T2, T3", "*tuple[int, *tuple[str, *tuple[bool]]]", """
      T1 -> int
      T2 -> str
      T3 -> bool
      """);
  }

  public void testFlatteningExpectedTypes() {
    doTestShapeMapping("T1, T2, T3", "*tuple[int, *tuple[str, *tuple[bool]]]", """
      T1 -> int
      T2 -> str
      T3 -> bool
      """);
  }

  public void testSplittingTypeVarTupleIntoNonVariadicTypes() {
    doNegativeShapeMappingTest("T1, T2", "*Ts", MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY);
  }

  public void testSplittingTypeVarTupleIntoNonVariadicAndVariadicTypes() {
    doNegativeShapeMappingTest("T1, *Es", "*Ts", MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY);
  }

  public void testActualTypeVarTupleMatchedToNothing() {
    doTestShapeMapping("T1, T2", "int, *Ts, str", """
      T1 -> int
      T2 -> str
      """);
  }

  public void testActualUnboundUnpackedTupleMatchedToNothing() {
    doTestShapeMapping("T1, T2", "int, *tuple[int, ...], str", """
      T1 -> int
      T2 -> str
      """);
  }

  public void testMultipleExpectedTypeVarTuples() {
    doNegativeShapeMappingTest("int, *T1s, *T2s", "int, str, str");
  }

  public void testMultipleExpectedUnboundUnpackedTuples() {
    doNegativeShapeMappingTest("*tuple[int, ...], *tuple[str, ...]", "int, str");
  }

  public void testBothTypeVarTupleAndUnboundUnpackedTupleExpected() {
    doNegativeShapeMappingTest("*T1s, *tuple[int, ...]", "str, int");
  }

  public void testExpectedTypeVarTupleMappedToAnotherTypeVarTuple() {
    doTestShapeMapping("T1, *Ts, T2", "int, *Vs, str", """
      T1 -> int
      *Ts -> *Vs
      T2 -> str
      """);
  }

  public void testExpectedTypeVarTupleMappedToUnpackedTuple() {
    doTestShapeMapping("T1, *Ts, T2", "int, *tuple[bool, ...], str", """
      T1 -> int
      *Ts -> *tuple[bool, ...]
      T2 -> str
      """);
  }

  public void testExpectedTypeVarTupleMappedToSingleRemainingType() {
    doTestShapeMapping("T1, *Ts, T2", "int, bool, str", """
      T1 -> int
      *Ts -> *tuple[bool]
      T2 -> str
      """);
  }

  public void testExpectedTypeVarTupleMappedToEmptyUnpackedTuple() {
    doTestShapeMapping("T1, *Ts, T2", "int, str", """
      T1 -> int
      *Ts -> *tuple[]
      T2 -> str
      """);
  }

  public void testExpectedUnboundUnpackedTupleMappedToEmptyUnpackedTuple() {
    doTestShapeMapping("T1, *tuple[bool, ...], T2", "int, str", """
      T1 -> int
      *tuple[bool, ...] -> *tuple[]
      T2 -> str
      """);
  }

  public void testSingleUnmatchedExpectedTypeIsMappedToAny() {
    doTestShapeMapping("int, T", "int", """
      int -> int
      T -> Any
      """, MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY);
  }

  public void testTwoUnmatchedExpectedTypeAreMappedToAny() {
    doTestShapeMapping("int, T1, T2", "int", """
      int -> int
      T1 -> Any
      T2 -> Any
      """, MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY);
  }

  public void testExtractingElementsFromUnboundTupleTypeFollowedByExpectedVariadic() {
    doTestShapeMapping("T, *Ts", "*tuple[float, ...]", """
      T -> float
      *Ts -> *tuple[float, ...]
      """);
  }

  public void testNonTrivialVariadicMatch() {
    doTestShapeMapping("int, T, *Ts, T1", "int, *tuple[float, ...], int, str", """
      int -> int
      T -> float
      *Ts -> *tuple[*tuple[float, ...], int]
      T1 -> str
      """);
  }

  public void testMappingNonVariadicTypesToSameNumberOfPositionalParameters() {
    doTestParameterListMapping("int, T", "x: int, y: str", """
      int -> int
      T -> str
      """);
  }

  public void testMappingNonVariadicTypesToMoreRequiredPositionalParameters() {
    doNegativeParameterListMappingTest("int, T", "x: int, y: str, z: bool");
  }

  public void testMappingNonVariadicTypesToFewerPossiblePositionalParameters() {
    doNegativeParameterListMappingTest("int, T", "x: int");
  }

  public void testMappingNonVariadicTypesToNoPositionalParameters() {
    doNegativeParameterListMappingTest("int, T", "");
  }

  public void testMappingNoTypesToSomeRequiredPositionalParameters() {
    doNegativeParameterListMappingTest("", "x: int, y: str");
  }

  public void testMappingNonVariadicTypesIgnoringExtraDefaultParameter() {
    doTestParameterListMapping("int, T", "x: int, y: str, z: bool = True", """
      int -> int
      T -> str
      """);
  }

  public void testMappingNonVariadicTypesIgnoringPositionalVarargParameter() {
    doTestParameterListMapping("int, T", "x: int, y: str, *args: str", """
      int -> int
      T -> str
      """);
  }

  public void testMappingNonVariadicTypesIgnoringExtraKeywordOnlyParameters() {
    doTestParameterListMapping("int, T", "x: int, y: str, *, z: bool = True", """
      int -> int
      T -> str
      """);
  }

  public void testMappingNonVariadicTypesIgnoringKeywordVarargsParameter() {
    doTestParameterListMapping("int, T", "x: int, y: str, **kwargs: str", """
      int -> int
      T -> str
      """);
  }

  public void testMappingNonVariadicTypesWithPaddingFromPositionalVarargsParameter() {
    doTestParameterListMapping("int, T, str", "x: int, *args: str", """
      int -> int
      T -> str
      str -> str
      """);
  }

  public void testMappingVariadicTypeToOnePositionalParameter() {
    doTestParameterListMapping("int, *Ts, str", "x: int, y: str, z: bool", """
      int -> int
      *Ts -> *tuple[str]
      str -> bool
      """);
  }

  public void testMappingVariadicTypeToNoPositionalParameters() {
    doTestParameterListMapping("int, *Ts, str", "x: int, z: bool", """
      int -> int
      *Ts -> *tuple[]
      str -> bool
      """);
  }

  public void testMappingVariadicTypeToFewPositionalParameters() {
    doTestParameterListMapping("int, *Ts", "x: int, y: str, z: bool", """
      int -> int
      *Ts -> *tuple[str, bool]
      """);
  }

  public void testMappingTypeListToRequiredKeywordParameter() {
    doNegativeParameterListMappingTest("int, *Ts", "x: int, *, y: str");
  }

  public void testMappingVariadicTypeToPositionalVarargParameter() {
    doTestParameterListMapping("int, *Ts", "x: int, y: str = 'a', *args: bool, z: int = 42", """
      int -> int
      *Ts -> *tuple[str, *tuple[bool, ...]]
      """);
  }

  public void testNonVariadicTypesToFixedSizePositionalVararg() {
    doTestParameterListMapping("int, str, bool", "x: int, *args: *tuple[str, bool]", """
      int -> int
      str -> str
      bool -> bool
      """);
  }

  public void testVariadicTypesToFixedSizePositionalVarargPrecededWithDefaults() {
    doTestParameterListMapping("int, *Ts, bool", "x: int, y: str = 'a', *args: *tuple[str, bool]", """
      int -> int
      *Ts -> *tuple[str, str]
      bool -> bool
      """);
  }

  private void doTestParameterListMapping(@NotNull String expectedTypeList,
                                          @NotNull String functionParameterList,
                                          @NotNull String expectedMapping) {
    String testData = generateTypingDeclarations(expectedTypeList, functionParameterList) +
                      "expected: tuple[Sentinel, " + expectedTypeList + "] = ...\n" +
                      "def actual(" + functionParameterList + "): ...\n";

    TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    myFixture.configureByText("a.py", testData);

    PyTargetExpression expectedTuple = myFixture.findElementByText("expected", PyTargetExpression.class);
    PyTupleType expectedTupleType = assertInstanceOf(context.getType(expectedTuple), PyTupleType.class);

    PyFunction actualFunction = myFixture.findElementByText("actual", PyFunction.class);
    PyFunctionType actualFunctionType = assertInstanceOf(context.getType(actualFunction), PyFunctionType.class);

    PyTypeParameterMapping mapping =
      PyTypeParameterMapping.mapWithParameterList(ContainerUtil.subList(expectedTupleType.getElementTypes(), 1),
                                                  actualFunctionType.getParameters(context), context);

    assertTypeMapping(expectedMapping, mapping, context);
  }

  private void doNegativeParameterListMappingTest(@NotNull String expectedTypeList,
                                                  @NotNull String functionParameterList) {
    doTestParameterListMapping(expectedTypeList, functionParameterList, "");
  }

  private void doTestShapeMapping(@NotNull String expectedTypeList,
                                  @NotNull String actualTypeList,
                                  @NotNull String expectedMapping,
                                  PyTypeParameterMapping.Option @NotNull ... options) {

    String testData = generateTypingDeclarations(expectedTypeList, actualTypeList) +
                      // Add an artificial first type to handle empty parameter list cases
                      "expected: tuple[Sentinel, " + expectedTypeList + "] = ...\n" +
                      "actual: tuple[Sentinel, " + actualTypeList + "] = ...\n";

    TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    myFixture.configureByText("a.py", testData);

    PyTargetExpression expectedTuple = myFixture.findElementByText("expected", PyTargetExpression.class);
    PyTupleType expectedTupleType = assertInstanceOf(context.getType(expectedTuple), PyTupleType.class);

    PyTargetExpression actualTuple = myFixture.findElementByText("actual", PyTargetExpression.class);
    PyTupleType actualTupleType = assertInstanceOf(context.getType(actualTuple), PyTupleType.class);

    PyTypeParameterMapping mapping = PyTypeParameterMapping.mapByShape(ContainerUtil.subList(expectedTupleType.getElementTypes(), 1),
                                                                       ContainerUtil.subList(actualTupleType.getElementTypes(), 1),
                                                                       options);
    assertTypeMapping(expectedMapping, mapping, context);
  }

  private static void assertTypeMapping(@NotNull String expectedMappingDump,
                                        @Nullable PyTypeParameterMapping actualMapping,
                                        @NotNull TypeEvalContext context) {
    if (expectedMappingDump.isEmpty()) {
      assertNull(actualMapping);
    }
    else {
      assertNotNull("Expected a successful match, got no match", actualMapping);
      StringBuilder actualMappingDump = new StringBuilder();
      for (Couple<PyType> typePair : actualMapping.getMappedTypes()) {
        actualMappingDump.append(PythonDocumentationProvider.getTypeName(typePair.getFirst(), context));
        actualMappingDump.append(" -> ");
        actualMappingDump.append(PythonDocumentationProvider.getTypeName(typePair.getSecond(), context));
        actualMappingDump.append("\n");
      }
      assertSameLines(expectedMappingDump, actualMappingDump.toString());
    }
  }

  private static @NotNull String generateTypingDeclarations(@NotNull String expectedTypeList, @NotNull String actualTypeList) {
    Set<String> allTypeVarName = new LinkedHashSet<>();
    allTypeVarName.addAll(StringUtil.findMatches(expectedTypeList, TYPE_VAR_NAME, 0));
    allTypeVarName.addAll(StringUtil.findMatches(actualTypeList, TYPE_VAR_NAME, 0));

    Set<String> allTypeVarTupleName = new LinkedHashSet<>();
    allTypeVarTupleName.addAll(StringUtil.findMatches(expectedTypeList, TYPE_VAR_TUPLE_NAME, 0));
    allTypeVarTupleName.addAll(StringUtil.findMatches(actualTypeList, TYPE_VAR_TUPLE_NAME, 0));

    StringBuilder testData = new StringBuilder(
      """
        from typing import TypeVar, TypeVarTuple
        """);

    for (String typeVarName : allTypeVarName) {
      testData.append(typeVarName).append(" = TypeVar('").append(typeVarName).append("')\n");
    }
    for (String typeVarTupleName : allTypeVarTupleName) {
      testData.append(typeVarTupleName).append(" = TypeVarTuple('").append(typeVarTupleName).append("')\n");
    }
    return testData.toString();
  }

  public void doNegativeShapeMappingTest(@NotNull String expectedTypeList, @NotNull String actualTypeList, PyTypeParameterMapping.Option @NotNull ... options) {
    doTestShapeMapping(expectedTypeList, actualTypeList, "", options);
  }
}
