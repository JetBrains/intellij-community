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
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Vitaliy.Bibaev
 */
abstract class DslTestCase(private val directoryName: String, private val dsl: Dsl) : LightCodeInsightFixtureTestCase() {
  fun testCall() {
    doTest {
      call(+"this", "method")
    }
  }

  fun testCallOneArg() {
    doTest {
      call(+"this", "method", +"10")
    }
  }

  fun testCallManyArgs() {
    doTest {
      call(+"this", "method", +"10", +"20", +"30")
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
      declare(variable(types.INT, "a"), +"10", false)
    }
  }

  fun testUseVariable() {
    doTest {
      declare(variable(types.INT, "a"), +"100", true)
      declare(variable(types.INT, "b"), +"a", false)
    }
  }

  fun testMergeCodeBlocks() {
    val block = dsl.block {
      declare(variable(types.INT, "b"), +"20", false)
    }

    doTest {
      declare(variable(types.INT, "a"), +"10", false)
      add(block)
    }
  }

  fun testMergeCodeBlocksReversed() {
    val block = dsl.block {
      declare(variable(types.INT, "a"), +"10", false)
    }

    doTest {
      add(block)
      declare(variable(types.INT, "b"), +"20", false)
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
      ifBranch(+"true") {
        call(+"receiver", "success")
      }
    }
  }

  fun testIfElse() {
    doTest {
      ifBranch(+"false") {
        call(+"this", "success")
      }.elseBranch {
        call(+"this", "fail")
      }
    }
  }

  fun testIfElseIf() {
    doTest {
      ifBranch(+"false") {
        call(+"this", "success")
      }.elseIfBranch(+"true") {
        call(+"this", "failSuccess")
      }
    }
  }

  fun testIfElseIfElse() {
    doTest {
      ifBranch(+"false") {
        call(+"this", "success")
      }.elseIfBranch(+"true") {
        call(+"this", "failSuccess")
      }.elseBranch {
        call(+"this", "failFail")
      }
    }
  }

  fun testForEach() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), +"getObjects()", false)
      forEachLoop(variable(types.ANY, "object"), objects) {
        +(loopVariable.call("toString"))
      }
    }
  }

  fun testForLoop() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), +"getObjects()", false)
      val i = variable(types.INT, "i")
      forLoop(declaration(i, +"0", true), +"i < $objects.size()", +"i++") {
        +(loopVariable.call("toString"))
      }
    }
  }

  fun testLoopWithBreak() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), +"getObjects()", false)
      forEachLoop(variable(types.ANY, "object"), objects) {
        +breakIteration()
      }
    }
  }

  fun testLoopWithNestedBreak() {
    doTest {
      val objects = declare(variable(types.list(types.ANY), "objects"), +"getObjects()", false)
      forEachLoop(variable(types.ANY, "object"), objects) {
        ifBranch(loopVariable.property("isEmpty")) {
          +breakIteration()
        }
      }
    }
  }

  fun testLambdaWithExpression() {
    doTest {
      +lambda("x") {
        +lambdaArg.call("method")
      }
    }
  }

  fun testLambdaWithCodeBlock() {
    doTest {
      +lambda("y") {
        +lambdaArg.call("method1")
        +lambdaArg.call("method2")
      }
    }
  }

  fun testCodeBlockReturn() {
    doTest {
      val a = declare(variable(types.INT, "a"), +"10", true)
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
        +lambdaArg.call("method1")
        doReturn(lambdaArg)
      }
    }
  }

  fun testAssignment() {
    doTest {
      val a = declare(variable(types.INT, "a"), true)
      a.assign(+"100")
    }
  }

  fun testNestedAssignment() {
    doTest {
      val a = declare(variable(types.INT, "a"), true)
      ifBranch(+"true") {
        a.assign(+"100")
      }.elseBranch {
        a.assign(+"200")
      }
    }
  }

  fun testProperties() {
    doTest {
      val a = variable(types.INT, "a")
      +a.property("myProperty")
    }
  }

  fun testNegation() {
    doTest {
      val lst = list(types.INT, "lst")
      +(not(lst.contains(+"1")))
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
      +map.get(+"key")
    }
  }

  fun testMapPut() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      +map.set(+"key", +"value")
    }
  }

  fun testMapContains() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      +map.contains(+"key")
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
      +map.computeIfAbsent(+"key", lambda("y") {
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
      +lst.add(TextExpression("100"))
      +lst.get(0).call("methodWithSideEffect")
      +lst.set(1, lst.get(0))
      +lst.contains(lst.size())
    }
  }

  fun testNewList() {
    doTest {
      val variable = list(types.INT, "lst")
      variable.assign(newList(types.INT, +"0", +"1", +"2", +"3"))
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
      +array[10]
      +array[+"11"]
    }
  }

  fun testArrayElementAssignment() {
    doTest {
      val array = array(types.INT, "a")
      +(array.set(0, +"1"))
      +(array.set(1, +"2"))
    }
  }

  fun testArrayDefaultDeclaration() {
    doTest {
      val a = array(types.INT, "array")
      declare(a.defaultDeclaration(+"10"))
    }
  }

  fun testArrayCreateFromElements() {
    doTest {
      val a = array(types.DOUBLE, "array")
      declare(a, newArray(types.DOUBLE, +"10.0", +"20.0"), false)
    }
  }

  fun testMapConvertToArray() {
    doTest {
      val map = map(types.INT, types.ANY, "map")
      add(map.convertToArray(this, "resultArray"))
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
    UsefulTestCase.assertSameLinesWithFile("testData/dsl/$directoryName/$testName.out", actualText, false)
  }
}
