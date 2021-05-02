// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.java.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PsiJavaPatterns.psiMethod
import com.intellij.patterns.uast.callExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.castSafelyTo
import junit.framework.TestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.analysis.UStringEvaluator

class UStringEvaluatorTest : AbstractStringEvaluatorTest() {
  fun `test simple string`() = doTest(
    """
    class MyFile {
      String a() {
        return "ab<caret>c";
      }
    }
  """.trimIndent(),
    "'abc'",
    additionalAssertions = {
      TestCase.assertEquals(1, it.segments.size)
    }
  )

  fun `test simple concatenation`() = doTest(
    """
    class MyFile {
      String a() {
        return "abc" +<caret> "def";
      }
    }
    """.trimIndent(),
    "'abc''def'",
    additionalAssertions = {
      TestCase.assertEquals(2, it.segments.size)
    }
  )

  fun `test concatenation with variable`() = doTest(
    """
    class MyFile {
      String a() {
        String a = "def";
        return "abc" +<caret> a;
      }
    }
    """.trimIndent(),
    "'abc''def'",
    additionalAssertions = {
      TestCase.assertEquals(2, it.segments.size)
    }
  )

  fun `test concatenation with ternary op and variable`() = doTest(
    """
    class MyFile {
      String a(boolean condition) {
        String a = condition ? "def" : "xyz";
        return "abc" +<caret> a;
      }
    }
    """.trimIndent(),
    "'abc'{'def'|'xyz'}",
    additionalAssertions = {
      TestCase.assertEquals(2, it.segments.size)
    }
  )

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
        return "abc" +<caret> a;
      }
    }
    """.trimIndent(),
    "'abc'{'def'|'xyz'}",
    additionalAssertions = {
      TestCase.assertEquals(2, it.segments.size)
    }
  )

  fun `test concatenation with unknown`() = doTest(
    """
    class MyFile {
      String a(boolean condition, String a) {
        return "abc" +<caret> a;
      }
    }
    """.trimIndent(),
    "'abc'NULL",
    additionalAssertions = {
      TestCase.assertEquals(2, it.segments.size)
    }
  )

  fun `test concatenation with constant`() = doTest(
    """
    class MyFile {
      public static final String myConst = "def";
      
      String a() {
        return "abc" +<caret> myConst;
      }
    }
    """.trimIndent(),
    "'abc''def'",
    additionalAssertions = {
      TestCase.assertEquals(2, it.segments.size)
    }
  )

  fun `test concatenation with constant from different file`() = doTest(
    """
    class MyFile {
      public static final String myConst = "def" + A.myConst;
      
      String a() {
        return "abc" +<caret> myConst;
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
        return "abc" +<caret> param;
      }
    }
    """.trimIndent(),
    "'abc'{'def'|'xyz'}",
    configuration = {
      UStringEvaluator.Configuration(
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
        return "abc" +<caret> param;
      }
    }
    """.trimIndent(),
    "'abc'{'def''fed'|'xyz'NULL}",
    configuration = {
      UStringEvaluator.Configuration(
        parameterUsagesDepth = 2,
        usagesSearchScope = LocalSearchScope(file)
      )
    }
  )

  fun `test concatenation with function`() = doTest(
    """
    class MyFile {
      String a() {
        return "abc" +<caret> b(false);
      }
      
      String b(boolean a) {
        if (!a) return "";
        
        return "xyz"
      }
    }
    """.trimIndent(),
    "'abc'{''|'xyz'}",
    configuration = {
      UStringEvaluator.Configuration(
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
        return "abc" +<caret> b(false, s + "1");
      }
      
      String b(boolean a, String param) {
        if (!a) return "aaa";
        
        return "xyz" + param
      }
    }
    """.trimIndent(),
    "'abc'{'aaa'|'xyz''my''var''1'}",
    configuration = {
      UStringEvaluator.Configuration(
        methodCallDepth = 2,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test concatenation with recursive function`() = doTest(
    """
    class MyFile {
      String a() {
        return "abc" +<caret> b();
      }
      
      String b() {
        return "xyz" + a();
      }
    }
    """.trimIndent(),
    "'abc'{'xyz'{'abc'NULL}}",
    configuration = {
      UStringEvaluator.Configuration(
        methodCallDepth = 3,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test concatenation with self recursive function with parameter`() = doTest(
    """
    class MyFile {
      String a(String param) {
        return "a" +<caret> a(param + "b") + param;
      }
    }
    """.trimIndent(),
    "'a'{'a'{'a'{'a'NULLNULL'b''b''b'}NULL'b''b'}NULL'b'}NULL",
    configuration = {
      UStringEvaluator.Configuration(
        methodCallDepth = 4,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test concatenation with two recursive functions with parameter`() = doTest(
    """
    class MyFile {
      String a(String param) {
        return "abc" +<caret> b(param + "a") + param;
      }
      
      String b(String param) {
        return "xyz" + a(param + "b") + param;
      }
    }
    """.trimIndent(),
    "'abc'{'xyz'{'abc'{'xyz'NULLNULL'a''b''a'}NULL'a''b'}NULL'a'}NULL",
    configuration = {
      UStringEvaluator.Configuration(
        methodCallDepth = 4,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test parentheses`() = doTest(
    """
    class MyFile {
      String a() {
        return "(" + b() <caret> + ")";
      }
      
      String b() {
        return "[" + a() + "]";
      }
    }
    """.trimIndent(),
    "'('{'['{'('{'['NULL']'}')'}']'}')'",
    configuration = {
      UStringEvaluator.Configuration(
        methodCallDepth = 4,
        methodsToAnalyzePattern = psiMethod().withName("a", "b")
      )
    }
  )

  fun `test deep function call`() = doTest(
    """
    class MyFile {
      String a() {
        return b("a")<caret>;
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
    "{{{{'a''b''c''d''e'}}}}",
    configuration = {
      UStringEvaluator.Configuration(
        methodCallDepth = 5,
        methodsToAnalyzePattern = psiMethod().withName("a", "b", "c", "d", "e")
      )
    }
  )

  fun `test custom evaluator`() {
    val myAnnoValueProvider = UStringEvaluator.DeclarationValueProvider { declaration ->
      val myAnnotation = declaration.uAnnotations.firstOrNull { anno -> anno.qualifiedName == "MyAnno" }
      myAnnotation?.findAttributeValue("value")?.castSafelyTo<ULiteralExpression>()?.takeIf { it.isString }?.let { literal ->
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
          return myValue +<caret> anotherValue;
        }
      }
      """.trimIndent(),
      "'value'NULL",
      configuration = {
        UStringEvaluator.Configuration(
          valueProviders = listOf(myAnnoValueProvider)
        )
      }
    )
  }

  fun `test method filter`() = doTest(
    """
    class MyFile {
      String a() {
        return b("a")<caret> + c("b");
      }
      
      String b(String param) {
        return "b" + param;
      }
      
      String c(String param) {
        return "c" + param;
      }
    }
    """.trimIndent(),
    "{'b''a'}NULL",
    configuration = {
      UStringEvaluator.Configuration(
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
        return Strings.jo<caret>in("\\m/", "abacaba", param, "my-string" + " is cool", s);
      }
    }
    """.trimIndent(),
    """'abacaba''\m/'NULL'\m/''my-string'' is cool''\m/''aaa'""",
    retrieveElement = UElement?::getUCallExpression,
    configuration = {
      UStringEvaluator.Configuration(
        methodEvaluators = mapOf(
          callExpression().withResolvedMethod(
            psiMethod().withName("join").definedInClass("Strings").withModifiers(PsiModifier.STATIC), false
          ) to UStringEvaluator.MethodCallEvaluator body@{ uStringEvaluator: UStringEvaluator,
                                                           configuration: UStringEvaluator.Configuration,
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

  fun `test many assignments`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String b() {
          return "b";
        }
      
        String a() {
          String a0 = "a";
          ${(1..1000).map { """String a$it = a${it - 1} + (true ? "a" : b());""" }.joinToString("\n          ") { it }}
          return a1000 + <caret> (true ? "a" : b());
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")

    val expected = "'a'${"{'a'|{'b'}}".repeat(1001)}"
    PlatformTestUtil.startPerformanceTest("calculate value of many assignments", 2000) {
      val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
        methodCallDepth = 2,
        methodsToAnalyzePattern = psiMethod().withName("b")
      )) ?: fail("Cannot evaluate string")
      TestCase.assertEquals(expected, pks.debugConcatenation)
    }.attempts(1).assertTiming()
  }
}