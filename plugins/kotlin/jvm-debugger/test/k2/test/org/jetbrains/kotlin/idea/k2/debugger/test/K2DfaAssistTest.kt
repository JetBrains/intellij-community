// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test

import com.intellij.debugger.engine.dfaassist.DfaAssistTest
import com.intellij.debugger.mockJDI.MockLocalVariable
import com.intellij.debugger.mockJDI.MockStackFrame
import com.intellij.debugger.mockJDI.MockVirtualMachine
import com.intellij.debugger.mockJDI.values.MockBooleanValue
import com.intellij.debugger.mockJDI.values.MockIntegerValue
import com.intellij.debugger.mockJDI.values.MockObjectReference
import com.intellij.debugger.mockJDI.values.MockValue
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.lang.annotation.ElementType
import java.util.function.BiConsumer

class K2DfaAssistTest : DfaAssistTest(), ExpectedPluginModeProvider {
    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    fun testSimple() {
        doTest("""fun test(x: Int) {
                    <caret>if (x > 0/*TRUE*/) {
                        
                    }
                    if (x in 1..6/*TRUE*/) {
                
                    }
                    if (1 in x..10/*FALSE*/) /*unreachable_start*/{
                
                    }/*unreachable_end*/
                }
                
                fun main() {
                    test(5)
                }""") { vm, frame -> frame.addVariable("x", MockIntegerValue(vm, 5)) }
    }

    fun testSuppression() {
        doTest("""fun test(x: Boolean, y: Boolean) {
               <caret>if(!x/*FALSE*/) /*unreachable_start*/{}/*unreachable_end*/
               if (x/*TRUE*/ /*unreachable_start*/|| y/*unreachable_end*/) {}
               if (y || x/*TRUE*/) {}
               var z: Boolean
               z = x/*TRUE*/
               var b = true
            }""") { vm, frame -> frame.addVariable("x", MockBooleanValue(vm, true)) }
    }

    fun testWhen() {
        doTest("""fun obj(x: Any) {
                    <caret>when(x) {
                        is String/*FALSE*/ -> /*unreachable_start*/{}/*unreachable_end*/
                        is Int/*TRUE*/ -> {}
                    }
                }

                fun main() {
                    obj(5)
                }""") { vm, frame -> frame.addVariable("x", MockValue.createValue(1, Integer::class.java, vm)) }
    }

    fun testElvis() {
        doTest("""fun obj(x: String?) {
                    <caret>x /*unreachable_start*/?: return/*unreachable_end*/
                }

                fun main() {
                    obj("x")
                }""") { vm, frame -> frame.addVariable("x", MockValue.createValue("x", String::class.java, vm)) }
    }

    fun testLet() {
        doTest("""fun obj(x: String?) {
                    <caret>x/*unreachable_start*/?.let { println() }/*unreachable_end*/
                }

                fun main() {
                    obj(null)
                }""") { vm, frame -> frame.addVariable(MockLocalVariable(vm, "x", vm.createReferenceType(String::class.java), null)) }
    }

    fun testUnreachableWhile() {
        doTest("""fun test(x: Int) {
                    <caret>while (x < 0/*FALSE*/) /*unreachable_start*/{
                        x--
                    }/*unreachable_end*/
                }
                
                fun main() {
                    test(5)
                }""") { vm, frame -> frame.addVariable("x", MockIntegerValue(vm, 5)) }
    }

    fun testUnreachableFor() {
        doTest("""fun test(x: Int) {
                    <caret>for (y in 10..x) /*unreachable_start*/{
                        println()
                    }/*unreachable_end*/
                }
                
                fun main() {
                    test(5)
                }""") { vm, frame -> frame.addVariable("x", MockIntegerValue(vm, 5)) }
    }

    fun testUnreachableTail() {
        doTest("""fun test(x: Int) {
                    <caret>if (x > 0/*TRUE*/) {
                      return
                    }
                    /*unreachable_start*/println()
                    println()
                    println()
                    println()/*unreachable_end*/
                }
                
                fun main() {
                    test(5)
                }""") { vm, frame -> frame.addVariable("x", MockIntegerValue(vm, 5)) }
    }

    fun testUnreachableTailContract() {
        doTest("""fun test(x: Int) {
                        <caret>check(x <= 0/*FALSE*/) { "" }
                        /*unreachable_start*/println()
                        println()
                        println()
                        println()/*unreachable_end*/
                    }
                    fun main() {
                        test(5)
                    }""") { vm, frame -> frame.addVariable("x", MockIntegerValue(vm, 5)) }
    }

    fun testUnreachableTailInLambda() {
        doTest("""fun test(x: Int) {
                    <caret>run {
                        if (x > 0/*TRUE*/) {
                          return@run
                        }
                        /*unreachable_start*/println()
                        println()
                        println()
                        println()/*unreachable_end*/
                    }
                }
                
                fun main() {
                    test(5)
                }""") { vm, frame -> frame.addVariable("x", MockIntegerValue(vm, 5)) }
    }

    fun testUnreachableTailInLambda2() {
        doTest("""fun test(x: Int) {
                    run {
                        <caret>if (x > 0/*TRUE*/) {
                          return@run
                        }
                        /*unreachable_start*/println()
                        println()
                        println()
                        println()/*unreachable_end*/
                    }
                }
                
                fun main() {
                    test(5)
                }""") { vm, frame -> frame.addVariable("x", MockIntegerValue(vm, 5)) }
    }

    fun testSmartCast() {
        doTest("""fun main() {
                        test(object : ArrayList<String>(){}, -1)
                    }
                    
                    fun test(any: Any, i: Int) {
                        <caret>if (any is ArrayList<*>) {
                            println(any.size > i/*TRUE*/)
                        }
                    }
                    """) { vm, frame -> frame.addVariable("i", MockIntegerValue(vm, -1)) }
    }

    fun testWrapped() {
        doTest("""fun test(x: Int) {
                    var y = x
                    val fn = { y++ }
                    fn()
                    <caret>if (y > 0/*TRUE*/) {
                
                    }
                    if (y == 0/*FALSE*/) /*unreachable_start*/{}/*unreachable_end*/
                    if (y < 0/*FALSE*/) /*unreachable_start*/{}/*unreachable_end*/
                }
                
                fun main() {
                    test(5)
                }""") { vm, frame ->
            val cls = Class.forName("kotlin.jvm.internal.Ref\$IntRef")
            val box = cls.getConstructor().newInstance()
            cls.getDeclaredField("element").set(box, 6)
            frame.addVariable("y", MockValue.createValue(box, cls, vm))
        }
    }

    class Nested(@Suppress("unused") val x:Int)

    fun testThis() {
        doTest("""package org.jetbrains.kotlin.idea.k2.debugger.test
            class K2DfaAssistTest {
              class Nested(val x:Int) {
                fun test() {
                  <caret>if (x == 5/*TRUE*/) {}
                }
              }
            }
        """) { vm, frame ->
            frame.setThisValue(MockObjectReference.createObjectReference(Nested(5), Nested::class.java, vm))
        }
    }

    private interface I
    private object O: I

    fun testObject() {
        doTest("""package org.jetbrains.kotlin.idea.k2.debugger.test
            class K2DfaAssistTest {
                interface I
                object O : I

                fun test(i: I) {
                  <caret>if (i is O/*TRUE*/) {}
                }
              }
            }
        """) { vm, frame ->
            frame.addVariable("i", MockObjectReference.createObjectReference(O, O::class.java, vm))
        }
    }

    fun testQualified() {
        doTest("""package org.jetbrains.kotlin.idea.k2.debugger.test
            class K2DfaAssistTest {
              class Nested(val x:Int)
            }
            
            fun use(n : K2DfaAssistTest.Nested) {
                <caret>if (n.x > 5/*TRUE*/) {}
            }
        """) { vm, frame ->
            frame.addVariable("n", MockValue.createValue(Nested(6), Nested::class.java, vm))
        }
    }

    fun testExtensionMethod() {
        doTest("""fun String.isLong(): Boolean {
                      <caret>return this.length > 5/*FALSE*/
                  }

                  fun main() {
                      "xyz".isLong()
                  }""") { vm, frame ->
            frame.addVariable("\$this\$isLong", MockValue.createValue("xyz", String::class.java, vm))
        }
    }

    fun testDeconstruction() {
        doTest("""fun test(x: Pair<Int, Int>) {
                      val (a, b) = x
                      <caret>if (a > b/*FALSE*/) /*unreachable_start*/{}/*unreachable_end*/
                  }
                  
                  fun main() {
                      test(3 to 5)
                  }""") { vm, frame ->
            frame.addVariable("a", MockIntegerValue(vm, 3))
            frame.addVariable("b", MockIntegerValue(vm, 5))
        }
    }

    fun testNPE() {
        doTest("""
            fun test(x: String?) {
              <caret>println(x/*NPE*/!!)
            }
        """.trimIndent()) { vm, frame ->
            frame.addVariable(MockLocalVariable(vm, "x", vm.createReferenceType(String::class.java), null))
        }
    }

    fun testAsNull() {
        doTest("""
            fun test(x: Any?) {
              <caret>println(x as String?) 
              println(x as/*NPE*/ String)
            }
        """.trimIndent()) { vm, frame ->
            frame.addVariable(MockLocalVariable(vm, "x", vm.createReferenceType(String::class.java), null))
        }
    }

    fun testAs() {
        doTest("""
            fun test(x: Any?) {
              <caret>println(x as/*CCE*/ Int) 
            }
        """.trimIndent()) { vm, frame ->
            frame.addVariable("x", MockValue.createValue("", vm))
        }
    }

    fun testNull() {
        doTest("""
            fun main() {
                test("hello", null)
            }

            fun test(x: Any, y: String?) {
                <caret>println(x as? Int/*NULL*/)
                println(y/*NULL*/ ?: "oops")
            }
        """.trimIndent()) { vm, frame ->
            frame.addVariable("x", MockValue.createValue("xyz", vm))
            frame.addVariable(MockLocalVariable(vm, "y", vm.createReferenceType(String::class.java), null))
        }
    }

    fun testEnum() {
        val text = """import java.lang.annotation.ElementType
                
                class Test {
                  fun test(t : ElementType) {
                    <caret>if (t == ElementType.PARAMETER/*FALSE*/) /*unreachable_start*/{}/*unreachable_end*/
                    if (t == ElementType.METHOD/*TRUE*/) {}
                  }
                }"""
        doTest(text) { vm, frame ->
            frame.addVariable("t", MockValue.createValue(ElementType.METHOD, ElementType::class.java, vm))
        }
    }

    private fun doTest(text: String, mockValues: BiConsumer<MockVirtualMachine, MockStackFrame>) {
        doTest(text, mockValues, "Test.kt")
    }
}