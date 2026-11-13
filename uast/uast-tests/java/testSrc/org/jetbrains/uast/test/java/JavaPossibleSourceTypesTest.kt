// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.platform.uast.testFramework.common.PossibleSourceTypesTestBase
import com.intellij.platform.uast.testFramework.common.allUElementSubtypes
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.junit.jupiter.api.Test


@TestApplication
class JavaPossibleSourceTypesTest : PossibleSourceTypesTestBase {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture) { _, file -> check(file) }

  private fun check(file: UFile) {
    val psiFile = file.sourcePsi
    for (uastType in allUElementSubtypes) {
      checkConsistencyWithRequiredTypes(psiFile, uastType)
    }
    checkConsistencyWithRequiredTypes(psiFile, UClass::class.java, UMethod::class.java, UField::class.java)
    checkConsistencyWithRequiredTypes(
      psiFile,
      USimpleNameReferenceExpression::class.java,
      UQualifiedReferenceExpression::class.java,
      UCallableReferenceExpression::class.java)
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

  @Test
  fun testLoopWithReassignment(): Unit = uast.doTest("Performance/LoopWithReassignment.java")

}