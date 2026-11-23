// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.test

import com.intellij.debugger.streams.core.trace.dsl.CodeContext
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.trace.dsl.impl.TextExpression
import com.intellij.testFramework.PlatformTestUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertThrows

/**
 * @author Vitaliy.Bibaev
 */
abstract class DslTestCase(private val dsl: Dsl) {
  private lateinit var testName: String

  /**
   * Returns the directory of the expected `.out` files.
   */
  protected abstract fun getTestDataPath(): String

  @BeforeEach
  fun setUpTestName(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  @Test
  fun testCall() {
    doTest {
      call("this".expr, "method")
    }
  }

  @Test
  fun testCallOneArg() {
    doTest {
      call("this".expr, "method", "10".expr)
    }
  }

  @Test
  fun testCallManyArgs() {
    doTest {
      call("this".expr, "method", "10".expr, "20".expr, "30".expr)
    }
  }

  @Test
  fun testDeclareMutableVariable() {
    doTest {
      declare(variable(types.INT, "a"), true)
    }
  }

  @Test
  fun testDeclareImmutableVariable() {
    doTest {
      declare(variable(types.INT, "a"), true)
    }
  }

  @Test
  fun testDeclareVariableAndInit() {
    doTest {
      declare(variable(types.INT, "a"), "10".expr, false)
    }
  }

  @Test
  fun testUseVariable() {
    doTest {
      declare(variable(types.INT, "a"), "100".expr, true)
      declare(variable(types.INT, "b"), "a".expr, false)
    }
  }

  @Test
  fun testMergeCodeBlocks() {
    val block = dsl.block {
      declare(variable(types.INT, "b"), "20".expr, false)
    }

    doTest {
      declare(variable(types.INT, "a"), "10".expr, false)
      add(block)
    }
  }

  @Test
  fun testMergeCodeBlocksReversed() {
    val block = dsl.block {
      declare(variable(types.INT, "a"), "10".expr, false)
    }

    doTest {
      add(block)
      declare(variable(types.INT, "b"), "20".expr, false)
    }
  }

  @Test
  fun testScope() {
    doTest {
      scope {
        call(thisExpression, "method")
      }
    }
  }

  @Test
  fun testNestedScopes() {
    doTest {
      scope {
        call(thisExpression, "method")
        scope {
          call(thisExpression, "method")
        }
      }
    }
  }

  @Test
  fun testIf() {
    doTest {
      ifBranch("true".expr) {
        call("receiver".expr, "success")
      }
    }
  }

  @Test
  fun testIfElse() {
    doTest {
      ifBranch("false".expr) {
        call("this".expr, "success")
      }.elseBranch {
        call("this".expr, "fail")
      }
    }
  }

  @Test
  fun testIfElseIf() {
    doTest {
      ifBranch("false".expr) {
        call("this".expr, "success")
      }.elseIfBranch("true".expr) {
        call("this".expr, "failSuccess")
      }
    }
  }

  @Test
  fun testIfElseIfElse() {
    doTest {
      ifBranch("false".expr) {
        call("this".expr, "success")
      }.elseIfBranch("true".expr) {
        call("this".expr, "failSuccess")
      }.elseBranch {
        call("this".expr, "failFail")
      }
    }
  }

  @Test
  fun testForEach() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), "getObjects()".expr, false)
      forEachLoop(variable(types.ANY, "object"), objects) {
        statement { (loopVariable.call("toString")) }
      }
    }
  }

  @Test
  fun testForLoop() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), "getObjects()".expr, false)
      val i = variable(types.INT, "i")
      forLoop(declaration(i, "0".expr, true), "i < $objects.size()".expr, "i++".expr) {
        statement { (loopVariable.call("toString")) }
      }
    }
  }

  @Test
  fun testLoopWithBreak() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), "getObjects()".expr, false)
      forEachLoop(variable(types.ANY, "object"), objects) {
        statement { breakIteration() }
      }
    }
  }

  @Test
  fun testLoopWithNestedBreak() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), "getObjects()".expr, false)
      forEachLoop(variable(types.ANY, "object"), objects) {
        ifBranch(loopVariable.property("isEmpty")) {
          statement { breakIteration() }
        }
      }
    }
  }

  @Test
  fun testLambdaWithExpression() {
    doTest {
      +lambda("x") {
        statement { lambdaArg.call("method") }
      }
    }
  }

  @Test
  fun testLambdaWithCodeBlock() {
    doTest {
      +lambda("y") {
        statement { lambdaArg.call("method1") }
        statement { lambdaArg.call("method2") }
      }
    }
  }

  @Test
  fun testCodeBlockReturn() {
    doTest {
      val a = declare(variable(types.INT, "a"), "10".expr, true)
      doReturn(a)
    }
  }

  @Test
  fun testLambdaExpressionReturn() {
    doTest {
      +lambda("y") {
        doReturn(lambdaArg)
      }

    }
  }

  @Test
  fun testLambdaBlockReturn() {
    doTest {
      +lambda("y") {
        statement { lambdaArg.call("method1") }
        doReturn(lambdaArg)
      }
    }
  }

  @Test
  fun testAssignment() {
    doTest {
      val a = declare(variable(types.INT, "a"), true)
      a assign "100".expr
    }
  }

  @Test
  fun testNestedAssignment() {
    doTest {
      val a = declare(variable(types.INT, "a"), true)
      ifBranch("true".expr) {
        a assign "100".expr
      }.elseBranch {
        a assign "200".expr
      }
    }
  }

  @Test
  fun testProperties() {
    doTest {
      val a = variable(types.INT, "a")
      statement { a.property("myProperty") }
    }
  }

  @Test
  fun testNegation() {
    doTest {
      val lst = list(types.INT, "lst")
      statement { !lst.contains("1".expr) }
    }
  }

  @Test
  fun testMapDeclaration() {
    doTest {
      declare(map(types.INT, types.BOOLEAN, "map"), false)
    }
  }

  @Test
  fun testLinkedMapDeclaration() {
    doTest {
      declare(linkedMap(types.INT, types.BOOLEAN, "map"), true)
    }
  }

  @Test
  fun testMapGet() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      statement { map.get("key".expr) }
    }
  }

  @Test
  fun testMapPut() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      statement { map.set("key".expr, "value".expr) }
    }
  }

  @Test
  fun testMapContains() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      statement { map.contains("key".expr) }
    }
  }

  @Test
  fun testMapInitialization() {
    doTest {
      declare(map(types.INT, types.INT, "map").defaultDeclaration(true))
    }
  }

  @Test
  fun testLinkedMapInitialization() {
    doTest {
      declare(linkedMap(types.INT, types.BOOLEAN, "map").defaultDeclaration(false))
    }
  }

  @Test
  fun testMapComputeIfAbsent() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      val target = variable(types.ANY, "target")
      declare(target, true)
      add(map.computeIfAbsent(dsl, "key".expr, map.call("method"), target))
    }
  }

  @Test
  fun testListDeclaration() {
    doTest {
      declare(list(types.INT, "lst"), true)
    }
  }

  @Test
  fun testListOperations() {
    doTest {
      val lst = list(types.LONG, "lst")
      declare(lst.defaultDeclaration())
      statement { lst.add(TextExpression("100")) }
      statement { lst.get(0).call("methodWithSideEffect") }
      statement { lst.set(1, lst.get(0)) }
      statement { lst.contains(lst.size()) }
    }
  }

  @Test
  fun testNewList() {
    doTest {
      val variable = list(types.INT, "lst")
      variable assign newList(types.INT, "0".expr, "1".expr, "2".expr, "3".expr)
    }
  }

  @Test
  fun testArrayDeclaration() {
    doTest {
      declare(array(types.INT, "a"), false)
    }
  }

  @Test
  fun testArrayElementUsage() {
    doTest {
      val array = array(types.INT, "a")
      statement { array[10] }
      statement { array["11".expr] }
    }
  }

  @Test
  fun testArrayElementAssignment() {
    doTest {
      val array = array(types.INT, "a")
      statement { array.set(0, "1".expr) }
      statement { array.set(1, "2".expr) }
    }
  }

  @Test
  fun testArrayDefaultDeclaration() {
    doTest {
      val a = array(types.INT, "array")
      declare(a.defaultDeclaration("10".expr))
    }
  }

  @Test
  fun testArrayCreateFromElements() {
    doTest {
      val a = array(types.DOUBLE, "array")
      declare(a, newArray(types.DOUBLE, "10.0".expr, "20.0".expr), false)
    }
  }

  @Test
  fun testMapConvertToArray() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      add(map.convertToArray(this, "resultArray"))
    }
  }

  @Test
  fun testNullableTypes() {
    doTest {
      declare(variable(types.ANY, "usualType"), false)
      declare(variable(types.nullable { ANY }, "nullableType"), false)
      declare(variable(types.array(types.nullable { ANY }), "arrayWithNullableElements"), false)
      declare(variable(types.nullable { array(nullable { ANY }) }, "nullableArrayWithNullableElements"), false)
      declare(variable(types.nullable { list(types.BOOLEAN) }, "nullableList"), false)
      declare(variable(types.map(types.INT, types.nullable { ANY }), "mapWithNullableValues"), false)
    }
  }

  @Test
  fun testTryBlock() {
    doTest {
      tryBlock {
        call(thisExpression, "hashCode")
      }.catch(variable(types.EXCEPTION, "e")) {
        call(thisExpression, "fail")
      }
    }
  }

  @Test
  fun testTryWithUnspecifiedCatch() {
    assertThrows<IllegalStateException> {
      doTest {
        tryBlock {
        }
      }
    }
  }

  @Test
  fun testTimeVariableDeclaration() {
    doTest {
      declare(timeDeclaration())
    }
  }

  @Test
  fun testSizedArrayCreation() {
    doTest {
      +newSizedArray(types.STRING, 100)
    }
  }

  private fun doTest(init: CodeContext.() -> Unit) {
    check(dsl.code(init))
  }

  private fun check(actualText: String) {
    PlatformTestUtil.assertSameLinesWithFile("${getTestDataPath()}/$testName.out", actualText, false)
  }
}
