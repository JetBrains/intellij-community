// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.ui.JVMDebuggerEvaluatorTest

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
        // doTestRangeExpression("val a = <caret>5", expressionWithoutSideEffects("5"))
    }

    fun testInstanceof() {
        doTestRangeExpression("var a = 1 i<caret>s Int", expressionWithoutSideEffects("1 is Int"))
    }

    fun testLogical() {
        doTestRangeExpression("val a = (1 > 2) <caret>&& (3 > 4)", expressionWithoutSideEffects("(1 > 2) && (3 > 4)"))
        doTestRangeExpression("val a = (1 > 2) &<caret>& (3 > 4)", expressionWithoutSideEffects("(1 > 2) && (3 > 4)"))
        doTestRangeExpression("val a = (1 > 2) <caret>&& (3 > 4)", expressionWithoutSideEffects("(1 > 2) && (3 > 4)"))
        doTestRangeExpression("val a = (1 > 2) <caret>&& (3 > 4) && true", expressionWithoutSideEffects("(1 > 2) && (3 > 4)"))
    }

    fun testArithmetical() {
        // doTestRangeExpression("val a = 1 <caret>+ 2", expressionWithoutSideEffects("1 + 2"))
        // doTestRangeExpression("val a = (1 <caret>> 2) && (3 > 4) ", expressionWithoutSideEffects("1 > 2"))
        // doTestRangeExpression("val a = (1 ><caret> 2) && (3 > 4) ", expressionWithoutSideEffects("1 > 2"))
        // doTestRangeExpression("val a = (1 > 2) && (3 <caret>> 4) ", expressionWithoutSideEffects("3 > 4"))
        // doTestRangeExpression("val a = 1 + 2 <caret>+ 3 > 4", expressionWithoutSideEffects("1 + 2 + 3"))
        // doTestRangeExpression("val a = 1 + <caret>2 + 3 > 4", expressionWithoutSideEffects("1 + 2 + 3"))
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

    fun testMethods() {
        doTestRangeExpression("val ins = A(); i<caret>ns.foo()", expressionWithoutSideEffects("ins"))
        doTestRangeExpression("val ins = A(); ins.f<caret>oo()", expressionWithSideEffects("ins.foo()"))
    }

    fun testTernaryOperator() {
        doTestRangeExpression("val expr = true; val a = if (expr<caret>) 56 else 73", expressionWithoutSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr)<caret> 56 else 73", expressionWithoutSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr) 5<caret>6 else 73", expressionWithoutSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr) 56<caret> else 73", expressionWithoutSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr) 56 <caret>else 73", expressionWithoutSideEffects("if (expr) 56 else 73"))
        doTestRangeExpression("val expr = true; val a = if (expr) 56 else<caret> 73", expressionWithoutSideEffects("if (expr) 56 else 73"))
    }

    fun testTernaryOperatorWithMethods() {
        // doTestRangeExpression("val expr = false; val a = if (expr) 5<caret>6 else bar()", expressionWithSideEffects("if (expr) 56 else bar()"))
    }

    fun testTryWithMethods() {
        // doTestRangeExpression("try { prin<caret>tln(37) } finally {}", expressionWithSideEffects("println(37)"))
        // doTestRangeExpression("t<caret>ry { println(37) } finally {}", expressionWithSideEffects("try { println(37) } finally {}"))
        // doTestRangeExpression("try { println(37) }<caret> finally {}", expressionWithSideEffects("try { println(37) } finally {}"))
        // doTestRangeExpression("try { println(37) } <caret>finally {}", expressionWithSideEffects("try { println(37) } finally {}"))
        // doTestRangeExpression("try { println(37) } fina<caret>lly {}", expressionWithSideEffects("try { println(37) } finally {}"))
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
        doTestRangeExpression("(::b<caret>ar)()", expressionWithoutSideEffects("::bar"))
        doTestRangeExpression("(::bar)(<caret>)", expressionWithSideEffects("(::bar)()"))
    }

    fun testCast() {
        doTestRangeExpression("val a = 5 a<caret>s Int", expressionWithoutSideEffects("5 as Int"))
        doTestRangeExpression("(::bar a<caret>s (Int) -> Unit)(25)", expressionWithoutSideEffects("::bar as (Int) -> Unit"))
    }

    fun testMethodParenthesis() {
        doTestRangeExpression("foo<caret>()", expressionWithSideEffects("foo()"))
    }

    fun testTextBlock() {
        doTestRangeExpression("val a = \"\"\"\nx<caret>xx\n\"\"\"", expressionWithSideEffects("\"\"\"\nxxx\n\"\"\""))
    }

    //////////// Utility methods

    private fun doTestRangeExpression(code: String, expected: ExpectedExpression) {
        doTestRange("class A { fun foo() {" + code + "} fun bar() = 37 }", expected)
    }

    private fun doTestRange(code: String, expected: ExpectedExpression) {
        configureFromFileText(getTestName(false) + ".kt", code)
        checkExpressionRangeAtCaret(expected)
    }

}