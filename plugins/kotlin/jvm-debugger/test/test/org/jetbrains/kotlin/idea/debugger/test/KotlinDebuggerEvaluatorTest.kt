// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.ui.JVMDebuggerEvaluatorTest
import com.intellij.idea.TestFor

/**
 * See also [AbstractSelectExpressionForDebuggerTest].
 */
class KotlinDebuggerEvaluatorTest : JVMDebuggerEvaluatorTest() {

    fun testConstructor() {
        doTestRangeExpression("<caret>A()", expressionWithSideEffects("A()"))
        doTestRangeExpression("A<caret>()", expressionWithSideEffects("A()"))
        doTestRangeExpression("A(<caret>)", expressionWithSideEffects("A()"))
        doTestRangeExpression("A()<caret>", noExpressions())
    }

    fun testVariable() {
        doTestRangeExpression("va<caret>l a = 5", noExpressions())
        doTestRangeExpression("va<caret>l a = 5", noExpressions())
        doTestRangeExpression("val <caret>a = 5", expressionWithoutSideEffects("a"))
        doTestRangeExpression("val a <caret>= 5", noExpressions())
        doTestRangeExpression("val a = <caret>5", noExpressions()) // just a constant expression, considered not interesting
        doTestRangeExpression("val a = <caret>bar(5)", expressionWithSideEffects("bar(5)"))
    }

    fun testInstanceof() {
        doTestRangeExpression("var a = 1 i<caret>s Int", expressionWithoutSideEffects("1 is Int"))
    }

    fun testLogical() {
        doTestRangeExpression("val a = true <caret>&& isOdd(5)", expressionWithSideEffects("true && isOdd(5)"))

        // The following ones could be evaluated without side effects, but we are not yet smart enough.
        doTestRangeExpression("val a = true <caret>&& false", expressionWithSideEffects("true && false"))
        doTestRangeExpression("val a = true &<caret>& false", expressionWithSideEffects("true && false"))
        doTestRangeExpression("val a = true <caret>&& false || true", expressionWithSideEffects("true && false"))
    }

    fun testArithmetical() {
        // Operators could be overloaded, we have to be careful.
        doTestRangeExpression("1 <caret>+ 2", expressionWithSideEffects("1 + 2"))
        doTestRangeExpression("1 <caret>> 2", expressionWithSideEffects("1 > 2"))
        doTestRangeExpression("1 <caret>== 2", expressionWithSideEffects("1 == 2"))
        doTestRangeExpression("val a = 1 <caret>+ 2", expressionWithSideEffects("1 + 2"))
        doTestRangeExpression("(1 <caret>> 2) && (3 > 4) ", expressionWithSideEffects("1 > 2"))
        doTestRangeExpression("val a = (1 <caret>> 2) && (3 > 4) ", expressionWithSideEffects("1 > 2"))
        doTestRangeExpression("val a = (1 ><caret> 2) && (3 > 4) ", expressionWithSideEffects("1 > 2"))
        doTestRangeExpression("val a = (1 > 2) && (3 <caret>> 4) ", expressionWithSideEffects("3 > 4"))
        doTestRangeExpression("val a = 1 + 2 <caret>+ 3 > 4", expressionWithSideEffects("1 + 2 + 3"))
        doTestRangeExpression("val a = 1 + <caret>2 + 3 > 4", expressionWithSideEffects("1 + 2"))
    }

    fun testClassNamePure() {
        doTestRange("class Abc { fun foo() { val s = A<caret>bc::class.java.name } }", expressionWithoutSideEffects("Abc::class"))
        doTestRange("class Abc { fun foo() { val s = Abc::cl<caret>ass.java.name } }", expressionWithoutSideEffects("Abc::class"))
    }

    fun testClassName1() {
        doTestRange("class Abc { fun foo() { val b = A<caret>bc.aa } companion object { var aa: Int }}", expressionWithoutSideEffects("Abc"))
        doTestRange("class Abc { fun foo() { val b = Abc.a<caret>a } companion object { var aa: Int }}", expressionWithoutSideEffects("Abc.aa"))
    }

    fun testClassName2() {
        doTestRange("class Abc { fun foo() { A<caret>bc.aa(5) } companion object { fun aa(a: Int) {} }}", expressionWithoutSideEffects("Abc"))
        doTestRange("class Abc { fun foo() { Abc.a<caret>a(5) } companion object { fun aa(a: Int) {} }}", expressionWithSideEffects("Abc.aa(5)"))
    }

    fun testFields() {
        doTestRange("class Abc { int aa; void foo() { Abc ins = new Abc(); i<caret>ns.aa;}}", expressionWithoutSideEffects("ins"))
        doTestRange("class Abc { int aa; void foo() { Abc ins = new Abc(); ins.a<caret>a;}}", expressionWithoutSideEffects("ins.aa"))
    }

    fun testContextParameters() {
        doTestRange("context(c<caret>tx: String) fun f1(arg: String): String = ctx + arg",
                    expressionWithoutSideEffects("ctx"))
        doTestRange("context(ctx: String) fun f1(arg: String): String = c<caret>tx + arg",
                    expressionWithoutSideEffects("ctx"))
    }

    fun testMethods() {
        doTestRangeExpression("val ins = A(); i<caret>ns.foo()", expressionWithoutSideEffects("ins"))
        doTestRangeExpression("val ins = A(); ins.f<caret>oo()", expressionWithSideEffects("ins.foo()"))
    }

    fun testThis() {
        doTestRangeExpression("th<caret>is.bar(5)", expressionWithoutSideEffects("this"))
        doTestRangeExpression("this.ba<caret>r(5)", expressionWithSideEffects("this.bar(5)"))
        doTestRangeExpression("th<caret>is@A.bar(5)", expressionWithoutSideEffects("this@A"))
    }

    fun testTernaryOperator() {
        // Actually, all of them could be evaluated without side effects.
        doTestRangeExpression("val expr = true; val a = if (expr<caret>) 56 else 73", expressionWithSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr)<caret> 56 else 73", expressionWithSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr) 5<caret>6 else 73", expressionWithSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr) 56<caret> else 73", expressionWithSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr) 56 <caret>else 73", expressionWithSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr) 56 else<caret> 73", expressionWithSideEffects("if (expr) 56 else 73"))
    }

    fun testTernaryOperatorWithMethods() {
        doTestRangeExpression("val expr = false; val a = if (expr) 5<caret>6 else bar(56)", expressionWithSideEffects("if (expr) 56 else bar(56)"))
    }

    fun testTryWithMethods() {
        doTestRangeExpression("try { b<caret>ar(37) } finally {}", expressionWithSideEffects("bar(37)"))
        doTestRangeExpression("t<caret>ry { bar(37) } finally {}", expressionWithSideEffects("try { bar(37) } finally {}"))
        doTestRangeExpression("try { bar(37) }<caret> finally {}", expressionWithSideEffects("try { bar(37) } finally {}"))
        doTestRangeExpression("try { bar(37) } <caret>finally {}", expressionWithSideEffects("try { bar(37) } finally {}"))
        doTestRangeExpression("try { bar(37) } fina<caret>lly {}", expressionWithSideEffects("try { bar(37) } finally {}"))
    }

    fun testWhenWithMethods() {
        doTestRangeExpression("val x = 37; when (x) { 1<caret>0 -> bar(10); 20 -> bar(20) }", expressionWithSideEffects("when (x) { 10 -> bar(10); 20 -> bar(20) }"))
    }

    fun testArrayAccess() {
        doTestRangeExpression("val abc = Array<Int>(200); a<caret>bc[100]", expressionWithoutSideEffects("abc"))
        doTestRangeExpression("val abc = Array<Int>(200); abc[1<caret>00]", expressionWithSideEffects("abc[100]"))
    }

    fun testArrayAccessVar() {
        doTestRangeExpression("val id = 5; val abc = Array<Int>(200); a<caret>bc[id]", expressionWithoutSideEffects("abc"))
        doTestRangeExpression("val id = 5; val abc = Array<Int>(200); abc[i<caret>d]", expressionWithoutSideEffects("id"))
        doTestRangeExpression("val id = 5; val abc = Array<Int>(200); abc<caret>[id]", expressionWithSideEffects("abc[id]"))
    }

    fun testLambda() {
        doTestRangeExpression("{ x: Int -<caret>> bar(x)}(25)", noExpressions())
    }

    fun testMethodReference() {
        doTestRangeExpression("(::f<caret>oo)()", expressionWithoutSideEffects("::foo"))
        doTestRangeExpression("(::foo)(<caret>)", expressionWithSideEffects("(::foo)()"))
    }

    fun testCast() {
        doTestRangeExpression("val a = 5 a<caret>s Int", expressionWithoutSideEffects("5 as Int"))
        doTestRangeExpression("(::bar a<caret>s (Int) -> Int)(25)", expressionWithoutSideEffects("::bar as (Int) -> Int"))
    }

    fun testMethodParenthesis() {
        doTestRangeExpression("foo<caret>()", expressionWithSideEffects("foo()"))
    }

    fun testTextBlock() {
        doTestRangeExpression("val a = \"\"\"\nx<caret>xx\n\"\"\"", expressionWithSideEffects("\"\"\"\nxxx\n\"\"\""))
    }

    @TestFor(issues = ["IDEA-368508"])
    fun testLabels() {
        doTestRangeExpression("run la<caret>bel@{ return@label }", noExpressions())
        doTestRangeExpression("run label<caret>@{ return@label }", noExpressions())
        doTestRangeExpression("run label@<caret>{ return@label }", noExpressions())
        doTestRangeExpression("run label@{ return<caret>@label }", noExpressions())
        doTestRangeExpression("run label@{ return@<caret>label }", noExpressions())
        doTestRangeExpression("run label@{ return@la<caret>bel }", noExpressions())
    }

    //////////// Utility methods

    private fun doTestRangeExpression(code: String, expected: ExpectedExpression) {
        doTestRange("class A { fun foo() {" + code + "} fun bar(x: Int) = 37 fun isOdd(x: Int) = (x % 2 != 0) }", expected)
    }

    private fun doTestRange(code: String, expected: ExpectedExpression) {
        configureFromFileText(getTestName(false) + ".kt", code)
        checkExpressionRangeAtCaret(expected)
    }

}
