// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.java.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PsiJavaPatterns.psiMethod
import com.intellij.patterns.uast.callExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.asSafely
import junit.framework.TestCase
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.analysis.*
import org.jetbrains.uast.toUElement

class UStringEvaluatorTest : AbstractStringEvaluatorTest() {
  fun `test simple string`() = doTest(
    """
    class MyFile {
      String a() {
        return /*<caret>*/ "abc";
      }
    }
  """.trimIndent(),
    "'abc'"
  ) {
    TestCase.assertEquals(1, it.segments.size)
  }

  fun `test simple concatenation`() = doTest(
    """
    class MyFile {
      String a() {
        return /*<caret>*/ "abc" + "def";
      }
    }
    """.trimIndent(),
    "'abc''def'"
  ) {
    TestCase.assertEquals(2, it.segments.size)
  }

  fun `test concatenation with variable`() = doTest(
    """
    class MyFile {
      String a() {
        String a = "def";
        return /*<caret>*/ "abc" + a;
      }
    }
    """.trimIndent(),
    "'abc''def'"
  ) {
    TestCase.assertEquals(2, it.segments.size)
  }

  fun `test concatenation with ternary op and variable`() = doTest(
    """
    class MyFile {
      String a(boolean condition) {
        String a = condition ? "def" : "xyz";
        return /*<caret>*/ "abc" + a;
      }
    }
    """.trimIndent(),
    "'abc'{'def'|'xyz'}"
  ) {
    TestCase.assertEquals(2, it.segments.size)
  }

  fun `test concatenation with if and variable`() = doTest(
    """
    class MyFile {
      String a(boolean condition) {
        String a;
        if (condition) {
          a = "def";
        } else {
          a = "xyz";
        }
        return /*<caret>*/ "abc" + a;
      }
    }
    """.trimIndent(),
    "'abc'{'def'|'xyz'}"
  ) {
    TestCase.assertEquals(2, it.segments.size)
  }

  fun `test concatenation with unknown`() = doTest(
    """
    class MyFile {
      String a(boolean condition, String a) {
        return /*<caret>*/ "abc" + a;
      }
    }
    """.trimIndent(),
    "'abc'NULL"
  ) {
    TestCase.assertEquals(2, it.segments.size)
  }

  fun `test concatenation with constant`() = doTest(
    """
    class MyFile {
      public static final String myConst = "def";
      
      String a() {
        return /*<caret>*/ "abc" + myConst;
      }
    }
    """.trimIndent(),
    "'abc''def'"
  ) {
    TestCase.assertEquals(2, it.segments.size)
  }

  fun `test concatenation with constant from different file`() = doTest(
    """
    class MyFile {
      public static final String myConst = "def" + A.myConst;
      
      String a() {
        return /*<caret>*/ "abc" + myConst;
      }
    }
    """.trimIndent(),
    "'abc''def''xyz'",
    additionalSetup = {
      myFixture.configureByText("A.java", """
        class A {
          public static final String myConst = "xyz";
        }
        """.trimIndent())
    }
  )

  fun `test concatenation with parameter with value in another function`() = doTest(
    """
    class MyFile {
      String b() {
        return a(true, "def");
      }
      
      String c() {
        return a(false, "xyz");
      }
    
      String a(boolean a, String param) {
        return /*<caret>*/ "abc" + param;
      }
    }
    """.trimIndent(),
    "'abc'{'def'|'xyz'}",
    configuration = {
      UNeDfaConfiguration(
        parameterUsagesDepth = 2,
        usagesSearchScope = LocalSearchScope(file)
      )
    }
  )

  fun `test concatenation with parameter with complex values`() = doTest(
    """
    class MyFile {
      String b() {
        return a(true, "def" + "fed");
      }
      
      String c() {
        return a(false, "xyz" + e);
      }
    
      String a(boolean a, String param) {
        return /*<caret>*/ "abc" + param;
      }
    }
    """.trimIndent(),
    "'abc'{'def''fed'|'xyz'NULL}",
    configuration = {
      UNeDfaConfiguration(
        parameterUsagesDepth = 2,
        usagesSearchScope = LocalSearchScope(file)
      )
    }
  )

  fun `test concatenation with function`() = doTest(
    """
    class MyFile {
      String a() {
        return /*<caret>*/ "abc" + b(false);
      }
      
      String b(boolean a) {
        if (!a) return "";
        
        return "xyz";
      }
    }
    """.trimIndent(),
    "'abc'{''|'xyz'}",
    configuration = {
      UNeDfaConfiguration(
        methodCallDepth = 2,
        methodsToAnalyzePattern = psiMethod().withName("b")
      )
    }
  )

  fun `test concatenation with function with parameter`() = doTest(
    """
    class MyFile {
      String a() {
        String s = "my" + "var";
        return /*<caret>*/ "abc" + b(false, s + "1");
      }
      
      String b(boolean a, String param) {
        if (!a) return "aaa";
        
        return "xyz" + param;
      }
    }
    """.trimIndent(),
    "'abc'{'aaa'|'xyz''my''var''1'}",
    configuration = {
      UNeDfaConfiguration(
        methodCallDepth = 2,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test concatenation with recursive function`() = doTest(
    """
    class MyFile {
      String a() {
        return /*<caret>*/ "abc" + b();
      }
      
      String b() {
        return "xyz" + a();
      }
    }
    """.trimIndent(),
    "'abc''xyz''abc'NULL",
    configuration = {
      UNeDfaConfiguration(
        methodCallDepth = 3,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test concatenation with self recursive function with parameter`() = doTest(
    """
    class MyFile {
      String a(String param) {
        return /*<caret>*/ "a" + a(param + "b") + param;
      }
    }
    """.trimIndent(),
    "'a''a''a''a'NULLNULL'b''b''b'NULL'b''b'NULL'b'NULL",
    configuration = {
      UNeDfaConfiguration(
        methodCallDepth = 4,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test concatenation with two recursive functions with parameter`() = doTest(
    """
    class MyFile {
      String a(String param) {
        return /*<caret>*/ "abc" + b(param + "a") + param;
      }
      
      String b(String param) {
        return "xyz" + a(param + "b") + param;
      }
    }
    """.trimIndent(),
    "'abc''xyz''abc''xyz'NULLNULL'a''b''a'NULL'a''b'NULL'a'NULL",
    configuration = {
      UNeDfaConfiguration(
        methodCallDepth = 4,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test parentheses`() = doTest(
    """
    class MyFile {
      String a() {
        return /*<caret>*/ "(" + b() + ")";
      }
      
      String b() {
        return "[" + a() + "]";
      }
    }
    """.trimIndent(),
    "'(''[''(''['NULL']'')'']'')'",
    configuration = {
      UNeDfaConfiguration(
        methodCallDepth = 4,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test deep function call`() = doTest(
    """
    class MyFile {
      String a() {
        return /*<caret>*/ b("a");
      }
      
      String b(String param) {
        return c(param + "b");
      }
      
      String c(String param) {
        return d(param + "c");
      }
      
      String d(String param) {
        return e(param + "d");
      }
      
      String e(String param) {
        return param + "e";
      }
    }
    """.trimIndent(),
    "'a''b''c''d''e'",
    configuration = {
      UNeDfaConfiguration(
        methodCallDepth = 5,
        methodsToAnalyzePattern = psiMethod().withName("a", "b", "c", "d", "e")
      )
    }
  )

  fun `test custom evaluator`() {
    val myAnnoValueProvider = DeclarationValueEvaluator { declaration ->
      val myAnnotation = declaration.uAnnotations.firstOrNull { anno -> anno.qualifiedName == "MyAnno" }
      myAnnotation?.findAttributeValue("value")?.asSafely<ULiteralExpression>()?.takeIf { it.isString }?.let { literal ->
        PartiallyKnownString(StringEntry.Known(literal.value as String, literal.sourcePsi!!, TextRange(0, literal.sourcePsi!!.textLength)))
      }
    }

    doTest(
      """
      @interface MyAnno {
        String value();
      }
      
      @interface AnotherAnno {
        String value();
      }
      
      class MyFile {
        @MyAnno("value")
        String myValue;
                
        @AnotherAnno("aaa")        
        String anotherValue;
        
        String a() {
          return /*<caret>*/ myValue + anotherValue;
        }
      }
      """.trimIndent(),
      "'value'NULL",
      configuration = {
        UNeDfaConfiguration(
          valueProviders = listOf(myAnnoValueProvider)
        )
      }
    )
  }

  fun `test method filter`() = doTest(
    """
    class MyFile {
      String a() {
        return /*<caret>*/ b("a") + c("b");
      }
      
      String b(String param) {
        return "b" + param;
      }
      
      String c(String param) {
        return "c" + param;
      }
    }
    """.trimIndent(),
    "'b''a'NULL",
    configuration = {
      UNeDfaConfiguration(
        methodCallDepth = 2,
        methodsToAnalyzePattern = psiMethod().withName("b")
      )
    }
  )

  fun `test join method evaluator`() = doTest(
    """
    class Strings {
      public static String join(String separator, String... separators) {
        return null;
      }
    }      
    
    class MyFile {
      String a(String param) {
        String s = "aaa";
        return /*<caret>*/ Strings.join("\\m/", "abacaba", param, "my-string" + " is cool", s);
      }
    }
    """.trimIndent(),
    """'abacaba''\m/'NULL'\m/''my-string'' is cool''\m/''aaa'""",
    configuration = {
      UNeDfaConfiguration(
        methodEvaluators = mapOf(
          callExpression().withResolvedMethod(
            psiMethod().withName("join").definedInClass("Strings").withModifiers(PsiModifier.STATIC), false
          ) to MethodCallEvaluator body@{ uStringEvaluator: UNeDfaValueEvaluator<PartiallyKnownString>,
                                          configuration: UNeDfaConfiguration<PartiallyKnownString>,
                                          joinCall: UCallExpression ->
            val separator = joinCall.getArgumentForParameter(0)?.let { uStringEvaluator.calculateValue(it, configuration) }
                            ?: return@body null

            val resultSegments = mutableListOf<StringEntry>()
            val params = joinCall.getArgumentForParameter(1) as? UExpressionList ?: return@body null
            params.firstOrNull()
              ?.let { uStringEvaluator.calculateValue(it, configuration) }
              ?.let { resultSegments += it.segments }
            ?: return@body null

            for (element in params.expressions.drop(1)) {
              resultSegments += separator.segments
              val elementValue = uStringEvaluator.calculateValue(element, configuration) ?: return@body null
              resultSegments += elementValue.segments
            }
            PartiallyKnownString(resultSegments)
          }
        )
      )
    }
  )

  fun `test many assignments performance`() {
    val updateTimes = 500
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String b() {
          return "b";
        }
      
        String a() {
          String a0 = "a";
          ${(1..updateTimes).map { """String a$it = a${it - 1} + (true ? "a" : b());""" }.joinToString("\n          ") { it }}
          return a$updateTimes + <caret> (true ? "a" : b());
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")

    myFixture.doHighlighting()

    val expected = "'a'${"{'a'|'b'}".repeat(updateTimes + 1)}"
    Benchmark.newBenchmark("calculate value of many assignments") {
      val pks = UStringEvaluator().calculateValue(elementAtCaret, UNeDfaConfiguration(
        methodCallDepth = 2,
        methodsToAnalyzePattern = psiMethod().withName("b")
      )) ?: fail("Cannot evaluate string")
      TestCase.assertEquals(expected, pks.debugConcatenation)
    }.attempts(2).start()
  }
}