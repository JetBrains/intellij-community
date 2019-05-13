// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.test

import com.intellij.debugger.streams.trace.dsl.CodeContext
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Vitaliy.Bibaev
 */
abstract class DslTestCase(private val dsl: Dsl) : LightCodeInsightFixtureTestCase() {
  fun testCall() {
    doTest {
      call("this".expr, "method")
    }
  }

  fun testCallOneArg() {
    doTest {
      call("this".expr, "method", "10".expr)
    }
  }

  fun testCallManyArgs() {
    doTest {
      call("this".expr, "method", "10".expr, "20".expr, "30".expr)
    }
  }

  fun testDeclareMutableVariable() {
    doTest {
      declare(variable(types.INT, "a"), true)
    }
  }

  fun testDeclareImmutableVariable() {
    doTest {
      declare(variable(types.INT, "a"), true)
    }
  }

  fun testDeclareVariableAndInit() {
    doTest {
      declare(variable(types.INT, "a"), "10".expr, false)
    }
  }

  fun testUseVariable() {
    doTest {
      declare(variable(types.INT, "a"), "100".expr, true)
      declare(variable(types.INT, "b"), "a".expr, false)
    }
  }

  fun testMergeCodeBlocks() {
    val block = dsl.block {
      declare(variable(types.INT, "b"), "20".expr, false)
    }

    doTest {
      declare(variable(types.INT, "a"), "10".expr, false)
      add(block)
    }
  }

  fun testMergeCodeBlocksReversed() {
    val block = dsl.block {
      declare(variable(types.INT, "a"), "10".expr, false)
    }

    doTest {
      add(block)
      declare(variable(types.INT, "b"), "20".expr, false)
    }
  }

  fun testScope() {
    doTest {
      scope {
        call(thisExpression, "method")
      }
    }
  }

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

  fun testIf() {
    doTest {
      ifBranch("true".expr) {
        call("receiver".expr, "success")
      }
    }
  }

  fun testIfElse() {
    doTest {
      ifBranch("false".expr) {
        call("this".expr, "success")
      }.elseBranch {
        call("this".expr, "fail")
      }
    }
  }

  fun testIfElseIf() {
    doTest {
      ifBranch("false".expr) {
        call("this".expr, "success")
      }.elseIfBranch("true".expr) {
        call("this".expr, "failSuccess")
      }
    }
  }

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

  fun testForEach() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), "getObjects()".expr, false)
      forEachLoop(variable(types.ANY, "object"), objects) {
        statement { (loopVariable.call("toString")) }
      }
    }
  }

  fun testForLoop() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), "getObjects()".expr, false)
      val i = variable(types.INT, "i")
      forLoop(declaration(i, "0".expr, true), "i < $objects.size()".expr, "i++".expr) {
        statement { (loopVariable.call("toString")) }
      }
    }
  }

  fun testLoopWithBreak() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), "getObjects()".expr, false)
      forEachLoop(variable(types.ANY, "object"), objects) {
        statement { breakIteration() }
      }
    }
  }

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

  fun testLambdaWithExpression() {
    doTest {
      +lambda("x") {
        statement { lambdaArg.call("method") }
      }
    }
  }

  fun testLambdaWithCodeBlock() {
    doTest {
      +lambda("y") {
        statement { lambdaArg.call("method1") }
        statement { lambdaArg.call("method2") }
      }
    }
  }

  fun testCodeBlockReturn() {
    doTest {
      val a = declare(variable(types.INT, "a"), "10".expr, true)
      doReturn(a)
    }
  }

  fun testLambdaExpressionReturn() {
    doTest {
      +lambda("y") {
        doReturn(lambdaArg)
      }

    }
  }

  fun testLambdaBlockReturn() {
    doTest {
      +lambda("y") {
        statement { lambdaArg.call("method1") }
        doReturn(lambdaArg)
      }
    }
  }

  fun testAssignment() {
    doTest {
      val a = declare(variable(types.INT, "a"), true)
      a assign "100".expr
    }
  }

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

  fun testProperties() {
    doTest {
      val a = variable(types.INT, "a")
      statement { a.property("myProperty") }
    }
  }

  fun testNegation() {
    doTest {
      val lst = list(types.INT, "lst")
      statement { !lst.contains("1".expr) }
    }
  }

  fun testMapDeclaration() {
    doTest {
      declare(map(types.INT, types.BOOLEAN, "map"), false)
    }
  }

  fun testLinkedMapDeclaration() {
    doTest {
      declare(linkedMap(types.INT, types.BOOLEAN, "map"), true)
    }
  }

  fun testMapGet() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      statement { map.get("key".expr) }
    }
  }

  fun testMapPut() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      statement { map.set("key".expr, "value".expr) }
    }
  }

  fun testMapContains() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      statement { map.contains("key".expr) }
    }
  }

  fun testMapInitialization() {
    doTest {
      declare(map(types.INT, types.INT, "map").defaultDeclaration(true))
    }
  }

  fun testLinkedMapInitialization() {
    doTest {
      declare(linkedMap(types.INT, types.BOOLEAN, "map").defaultDeclaration(false))
    }
  }

  fun testMapComputeIfAbsent() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      +map.computeIfAbsent("key".expr, lambda("y") {
        doReturn(map.call("method"))
      })
    }
  }

  fun testListDeclaration() {
    doTest {
      declare(list(types.INT, "lst"), true)
    }
  }

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

  fun testNewList() {
    doTest {
      val variable = list(types.INT, "lst")
      variable assign newList(types.INT, "0".expr, "1".expr, "2".expr, "3".expr)
    }
  }

  fun testArrayDeclaration() {
    doTest {
      declare(array(types.INT, "a"), false)
    }
  }

  fun testArrayElementUsage() {
    doTest {
      val array = array(types.INT, "a")
      statement { array[10] }
      statement { array["11".expr] }
    }
  }

  fun testArrayElementAssignment() {
    doTest {
      val array = array(types.INT, "a")
      statement { array.set(0, "1".expr) }
      statement { array.set(1, "2".expr) }
    }
  }

  fun testArrayDefaultDeclaration() {
    doTest {
      val a = array(types.INT, "array")
      declare(a.defaultDeclaration("10".expr))
    }
  }

  fun testArrayCreateFromElements() {
    doTest {
      val a = array(types.DOUBLE, "array")
      declare(a, newArray(types.DOUBLE, "10.0".expr, "20.0".expr), false)
    }
  }

  fun testMapConvertToArray() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      add(map.convertToArray(this, "resultArray"))
    }
  }

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

  fun testTryBlock() {
    doTest {
      tryBlock {
        call(thisExpression, "hashCode")
      }.catch(variable(types.EXCEPTION, "e")) {
        call(thisExpression, "fail")
      }
    }
  }

  fun testTryWithUnspecifiedCatch() {
    assertException(object : AbstractExceptionCase<IllegalStateException>() {
      override fun tryClosure() {
        doTest {
          tryBlock {
          }
        }
      }

      override fun getExpectedExceptionClass(): Class<IllegalStateException> = IllegalStateException::class.java
    })
  }

  fun testTimeVariableDeclaration() {
    doTest {
      declare(timeDeclaration())
    }
  }

  fun testSizedArrayCreation() {
    doTest {
      +newSizedArray(types.STRING, 100)
    }
  }

  private fun doTest(init: CodeContext.() -> Unit) {
    check(dsl.code(init))
  }

  private fun check(actualText: String) {
    val testName = getTestName(true)
    assertSameLinesWithFile("$testDataPath/$testName.out", actualText, false)
  }
}
