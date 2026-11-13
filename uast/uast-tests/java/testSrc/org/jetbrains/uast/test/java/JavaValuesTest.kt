// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java

import com.intellij.platform.uast.testFramework.common.ValuesTestBase
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.evaluation.SimpleEvaluatorExtension
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUFile
import org.jetbrains.uast.values.UBooleanConstant
import org.jetbrains.uast.values.UValue
import org.junit.jupiter.api.Test

@TestApplication
class JavaValuesTest : ValuesTestBase {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture) { testName, file -> check(testName, file) }

  private var _evaluatorExtension: UEvaluatorExtension? = null

  override fun getTestDataPath(): String = uast.fixture.testDataPath

  override fun getEvaluatorExtension(): UEvaluatorExtension? = _evaluatorExtension

  override fun createCopyToCheck(file: UFile): UFile? {
    // TODO: when JavaUFile (and/or its constructor) becomes `internal`, this should move into JavaUFile.
    return if (file is JavaUFile) JavaUFile(file.sourcePsi, file.languagePlugin) else null
  }

  private fun doTest(name: String, extension: UEvaluatorExtension) {
    _evaluatorExtension = extension
    try {
      uast.doTest(name)
    }
    finally {
      _evaluatorExtension = null
    }
  }

  @Test
  fun testAliveThenElse(): Unit = uast.doTest("Simple/AliveThenElse.java")

  @Test
  fun testAnonymous(): Unit = uast.doTest("Simple/Anonymous.java")

  @Test
  fun testBitwise(): Unit = uast.doTest("Simple/Bitwise.java")

  @Test
  fun testByteShort(): Unit = uast.doTest("Simple/ByteShort.java")

  @Test
  fun testCascadeIf(): Unit = uast.doTest("Simple/CascadeIf.java")

  @Test
  fun testCharacters(): Unit = uast.doTest("Simple/Characters.java")

  @Test
  fun testClassLiteral(): Unit = uast.doTest("Simple/ClassLiteral.java")

  @Test
  fun testDeadElse(): Unit = uast.doTest("Simple/DeadElse.java")

  @Test
  fun testDeadFor(): Unit = uast.doTest("Simple/DeadFor.java")

  @Test
  fun testDeadIfComparison(): Unit = uast.doTest("Simple/DeadIfComparison.java")

  @Test
  fun testDeadSwitchEntries(): Unit = uast.doTest("Simple/DeadSwitchEntries.java")

  @Test
  fun testDeadSwitchEntriesWithoutBreaks(): Unit = uast.doTest("Simple/DeadSwitchEntriesWithoutBreaks.java")

  @Test
  fun testDeadThen(): Unit = uast.doTest("Simple/DeadThen.java")

  @Test
  fun testDependents(): Unit = uast.doTest("Simple/Dependents.java")

  @Test
  fun testDoWhile(): Unit = uast.doTest("Simple/DoWhile.java")

  @Test
  fun testDoWhileInfinite(): Unit = uast.doTest("Simple/DoWhileInfinite.java")

  @Test
  fun testDoWhileWithReturn(): Unit = uast.doTest("Simple/DoWhileWithReturn.java")

  @Test
  fun testEnumChoice(): Unit = uast.doTest("Simple/EnumChoice.java")

  @Test
  fun testEnumSwitch(): Unit = uast.doTest("Simple/EnumSwitch.java")

  @Test
  fun testEnhancedSwitch(): Unit = uast.doTest("Simple/EnhancedSwitch.java")

  @Test
  fun testEnhancedSwitchComplexEvaluations(): Unit = uast.doTest("Simple/EnhancedSwitchComplexEvaluations.java")

  @Test
  fun testEnumSwitchConditionalBreak(): Unit = uast.doTest("Simple/EnumSwitchConditionalBreak.java")

  @Test
  fun testEnumSwitchWithoutBreaks(): Unit = uast.doTest("Simple/EnumSwitchWithoutBreaks.java")

  @Test
  fun testExternal(): Unit = uast.doTest("Simple/External.java")

  @Test
  fun testFieldRef(): Unit = uast.doTest("Simple/FieldRef.java")

  @Test
  fun testFloatDouble(): Unit = uast.doTest("Simple/FloatDouble.java")

  @Test
  fun testFor(): Unit = uast.doTest("Simple/For.java")

  @Test
  fun testForEach(): Unit = uast.doTest("Simple/ForEach.java")

  @Test
  fun testForEachMutableIterable(): Unit = uast.doTest("Simple/ForEachMutableIterable.java")

  @Test
  fun testIdentityEquals(): Unit = uast.doTest("Simple/IdentityEquals.java")

  @Test
  fun testImmutableField(): Unit = uast.doTest("Simple/ImmutableField.java")

  @Test
  fun testIncDec(): Unit = uast.doTest("Simple/IncDec.java")

  @Test
  fun testIntLong(): Unit = uast.doTest("Simple/IntLong.java")

  @Test
  fun testLabeled(): Unit = uast.doTest("Simple/Labeled.java")

  @Test
  fun testLabeledOuter(): Unit = uast.doTest("Simple/LabeledOuter.java")

  @Test
  fun testLambda(): Unit = uast.doTest("Simple/Lambda.java")

  @Test
  fun testLogicals(): Unit = uast.doTest("Simple/Logicals.java")

  @Test
  fun testMethodReference(): Unit = uast.doTest("Simple/MethodReference.java")

  @Test
  fun testModification(): Unit = uast.doTest("Simple/Modification.java")

  @Test
  fun testMutableField(): Unit = uast.doTest("Simple/MutableField.java")

  @Test
  fun testNotANumber(): Unit = uast.doTest("Simple/NotANumber.java")

  @Test
  fun testReturnMinusX(): Unit = uast.doTest("Simple/ReturnMinusX.java")

  @Test
  fun testReturnSum(): Unit = uast.doTest("Simple/ReturnSum.java")

  @Test
  fun testReturnX(): Unit = uast.doTest("Simple/ReturnX.java")

  @Test
  fun testShift(): Unit = uast.doTest("Simple/Shift.java")

  @Test
  fun testStrings(): Unit = uast.doTest("Simple/Strings.java")

  @Test
  fun testTernary(): Unit = uast.doTest("Simple/Ternary.java")

  @Test
  fun testTryCatch(): Unit = uast.doTest("Simple/TryCatch.java")

  @Test
  fun testWhile(): Unit = uast.doTest("Simple/While.java")

  @Test
  fun testWhileWithContinue(): Unit = uast.doTest("Simple/WhileWithContinue.java")

  @Test
  fun testWhileWithIncrement(): Unit = uast.doTest("Simple/WhileWithIncrement.java")

  @Test
  fun testWhileWithMutableCondition(): Unit = uast.doTest("Simple/WhileWithMutableCondition.java")

  @Test
  fun testWhileWithReturn(): Unit = uast.doTest("Simple/WhileWithReturn.java")

  @PerformanceUnitTest
  @Test
  fun testPerformanceLoopWithReassignment(): Unit = uast.doTest("Performance/LoopWithReassignment.java")

  @Test
  fun testEvaluatorExtension(): Unit = doTest("Simple/EvaluatorExtension.java", object : SimpleEvaluatorExtension() {
    override fun evaluateMethodCall(target: PsiMethod, argumentValues: List<UValue>): Any? {
      if (target.name == "getTestName") {
        (argumentValues.singleOrNull() as? UBooleanConstant)?.let { arg ->
          return if (arg.value) "UPPER" else "lower"
        }
      }

      return super.evaluateMethodCall(target, argumentValues)
    }
  })

  @Test
  fun testParamViaEvaluatorExtension(): Unit = doTest("Simple/ParamViaEvaluatorExtension.java", object : SimpleEvaluatorExtension() {
    override fun evaluateVariable(variable: UVariable): Any? {
      if (variable is UParameter && variable.name == "x") {
        return "0"
      }
      return super.evaluateVariable(variable)
    }
  })
}