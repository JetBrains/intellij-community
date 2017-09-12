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
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.junit.Test

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
      declare(variable("int", "a"), true)
    }
  }

  fun testDeclareImmutableVariable() {
    doTest {
      +variable("int", "a")
    }
  }

  fun testDeclareVariableAndInit() {
    doTest {
      declare(variable("int", "a"), +"10", false)
    }
  }

  fun testUseVariable() {
    doTest {
      declare(variable("int", "a"), +"100", true)
      declare(variable("int", "b"), +"a", false)
    }
  }

  fun testScope() {
    doTest {
      scope {
        call(THIS, "method")
      }
    }
  }

  fun testNestedScopes() {
    doTest {
      scope {
        call(THIS, "method")
        scope {
          call(THIS, "method")
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
      val objects = declare(variable("List", "objects"), +"getObjects()", false)
      forEachLoop(variable("Object", "object"), objects) {
        +(loopVariable.call("toString"))
      }
    }
  }

  fun testForLoop() {
    doTest {
      val objects = declare(variable("List", "objects"), +"getObjects()", false)
      val i = variable("int", "i")
      forLoop(declaration(i, +"0", true), +"i < $objects.size()", +"i++") {
        +(loopVariable.call("toString"))
      }
    }
  }

  fun testLambdaWithExpression() {
    doTest {
      +lambda("x") {
        +(+argName).call("method")
      }
    }
  }

  fun testLambdaWithCodeBlock() {
    doTest {
      +lambda("y") {
        +(+argName).call("method1")
        +(+argName).call("method2")
      }
    }
  }

  fun testAssignment() {
    doTest {
      val a = declare(variable("int", "a"), true)
      a.assign(+"100")
    }
  }

  fun testNestedAssignment() {
    doTest {
      val a = declare(variable("int", "a"), true)
      ifBranch(+"true") {
        a.assign(+"100")
      }.elseBranch {
        a.assign(+"200")
      }
    }
  }

  fun testMapDeclaration() {
    doTest {
      declare(map(GenericType.INT, GenericType.BOOLEAN, "map"), false)
    }
  }

  fun testLinkedMapDeclaration() {
    doTest {
      declare(linkedMap(GenericType.INT, GenericType.BOOLEAN, "map"), true)
    }
  }

  fun testMapGet() {
    doTest {
      val map = map(GenericType.INT, GenericType.OBJECT, "map")
      +map[+"key"]
    }
  }

  fun testMapPut() {
    doTest {
      val map = map(GenericType.INT, GenericType.OBJECT, "map")
      +map.set(+"key", +"value")
    }
  }

  fun testMapContains() {
    doTest {
      val map = map(GenericType.INT, GenericType.OBJECT, "map")
      +map.contains(+"key")
    }
  }

  fun testMapInitialization() {
    doTest {
      declare(map(GenericType.INT, GenericType.INT, "map").defaultDeclaration(true))
    }
  }

  fun testLinkedMapInitialization() {
    doTest {
      declare(linkedMap(GenericType.INT, GenericType.BOOLEAN, "map").defaultDeclaration(false))
    }
  }

  fun testArrayDeclaration() {
    doTest {
      declare(array("int", "a"), false)
    }
  }

  fun testArrayElementUsage() {
    doTest {
      val array = array("int", "a")
      +array[10]
      +array[+"11"]
    }
  }

  fun testArrayElementAssignment() {
    doTest {
      val array = array("int", "a")
      +(array.set(0, +"1"))
      +(array.set(1, +"2"))
    }
  }

  fun testArrayDefaultDeclaration() {
    doTest {
      val a = array("int", "array")
      declare(a.defaultDeclaration(+"10"))
    }
  }

  fun testArrayCreateFromElements() {
    doTest {
      val a = array("double", "array")
      declare(a, newArray("double", +"10.0", +"20.0"), false)
    }
  }

  fun testMapConvertToArray() {
    doTest {
      val map = map(GenericType.INT, GenericType.OBJECT, "map")
      +(TextExpression(map.convertToArray(this, "resultArray")))
    }
  }

  fun testTryBlock() {
    doTest {
      tryBlock {
        call(THIS, "hashCode")
      }.catch(variable("Exception", "e")) {
        call(THIS, "fail")
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
      +newSizedArray("String", 100)
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
