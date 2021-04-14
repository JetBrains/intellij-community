// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PsiJavaPatterns.psiMethod
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.castSafelyTo
import junit.framework.TestCase
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.analysis.UStringEvaluator
import org.jetbrains.uast.test.java.AbstractJavaUastLightTest
import org.jetbrains.uast.toUElement

class UStringEvaluatorTest : AbstractJavaUastLightTest() {
  fun `test simple string`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a() {
          return "ab<caret>c";
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("abc", pks.concatenationOfKnown)
    TestCase.assertEquals(1, pks.segments.size)
  }

  fun `test simple concatenation`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a() {
          return "abc" +<caret> "def";
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("abcdef", pks.concatenationOfKnown)
    TestCase.assertEquals(2, pks.segments.size)
  }

  fun `test concatenation with variable`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a() {
          String a = "def";
          return "abc" +<caret> a;
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("abcdef", pks.concatenationOfKnown)
    TestCase.assertEquals(2, pks.segments.size)
  }

  fun `test concatenation with ternary op and variable`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a(boolean condition) {
          String a = condition ? "def" : "xyz";
          return "abc" +<caret> a;
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'{'def'|'xyz'}", pks.debugConcatenation)
    TestCase.assertEquals(2, pks.segments.size)
  }

  fun `test concatenation with if and variable`() {
    val file = myFixture.configureByText("MyFile.java", """
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
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'{'def'|'xyz'}", pks.debugConcatenation)
    TestCase.assertEquals(2, pks.segments.size)
  }

  fun `test concatenation with unknown`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a(boolean condition, String a) {
          return "abc" +<caret> a;
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'NULL", pks.debugConcatenation)
    TestCase.assertEquals(2, pks.segments.size)
  }

  fun `test concatenation with constant`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        public static final String myConst = "def";
        
        String a() {
          return "abc" +<caret> myConst;
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc''def'", pks.debugConcatenation)
    TestCase.assertEquals(2, pks.segments.size)
  }

  fun `test concatenation with constant from different file`() {
    myFixture.configureByText("A.java", """
      class A {
        public static final String myConst = "xyz";
      }
    """.trimIndent())

    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        public static final String myConst = "def" + A.myConst;
        
        String a() {
          return "abc" +<caret> myConst;
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc''def''xyz'", pks.debugConcatenation)

  }

  fun `test concatenation with parameter with value in another function`() {
    val file = myFixture.configureByText("MyFile.java", """
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
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      parameterUsagesDepth = 2,
      usagesSearchScope = LocalSearchScope(file)
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'{'def'|'xyz'}", pks.debugConcatenation)
  }

  fun `test concatenation with parameter with complex values`() {
    val file = myFixture.configureByText("MyFile.java", """
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
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      parameterUsagesDepth = 2,
      usagesSearchScope = LocalSearchScope(file)
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'{'def''fed'|'xyz'NULL}", pks.debugConcatenation)
  }

  fun `test concatenation with function`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a() {
          return "abc" +<caret> b(false);
        }
        
        String b(boolean a) {
          if (!a) return "";
          
          return "xyz"
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      methodCallDepth = 2,
      methodsToAnalyzePattern = psiMethod().withName("b")
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'{''|'xyz'}", pks.debugConcatenation)
  }

  fun `test concatenation with function with parameter`() {
    val file = myFixture.configureByText("MyFile.java", """
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
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      methodCallDepth = 2,
      methodsToAnalyzePattern = psiMethod().withName("a", "b")
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'{'aaa'|'xyz''my''var''1'}", pks.debugConcatenation)
  }

  fun `test concatenation with recursive function`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a() {
          return "abc" +<caret> b();
        }
        
        String b() {
          return "xyz" + a();
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      methodCallDepth = 3,
      methodsToAnalyzePattern = psiMethod().withName("a", "b")
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'{'xyz'{'abc'NULL}}", pks.debugConcatenation)
  }

  fun `test concatenation with self recursive function with parameter`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a(String param) {
          return "a" +<caret> a(param + "b") + param;
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      methodCallDepth = 4,
      methodsToAnalyzePattern = psiMethod().withName("a", "b")
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'a'{'a'{'a'{'a'NULLNULL'b''b''b'}NULL'b''b'}NULL'b'}NULL", pks.debugConcatenation)
  }

  fun `test concatenation with two recursive functions with parameter`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a(String param) {
          return "abc" +<caret> b(param + "a") + param;
        }
        
        String b(String param) {
          return "xyz" + a(param + "b") + param;
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      methodCallDepth = 4,
      methodsToAnalyzePattern = psiMethod().withName("a", "b")
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'abc'{'xyz'{'abc'{'xyz'NULLNULL'a''b''a'}NULL'a''b'}NULL'a'}NULL", pks.debugConcatenation)
  }

  fun `test parentheses`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String a() {
          return "(" + b() <caret> + ")";
        }
        
        String b() {
          return "[" + a() + "]";
        }
      }
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      methodCallDepth = 4,
      methodsToAnalyzePattern = psiMethod().withName("a", "b")
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'('{'['{'('{'['NULL']'}')'}']'}')'", pks.debugConcatenation)
  }

  fun `test deep function call`() {
    val file = myFixture.configureByText("MyFile.java", """
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
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      methodCallDepth = 5,
      methodsToAnalyzePattern = psiMethod().withName("a", "b", "c", "d", "e")
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("{{{{'a''b''c''d''e'}}}}", pks.debugConcatenation)
  }

  fun `test custom evaluator`() {
    val file = myFixture.configureByText("MyFile.java", """
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
    """.trimIndent())

    val myAnnoValueProvider = UStringEvaluator.DeclarationValueProvider {
      val myAnnotation = it.uAnnotations.firstOrNull { anno -> anno.qualifiedName == "MyAnno" }
      myAnnotation?.findAttributeValue("value")?.castSafelyTo<ULiteralExpression>()?.takeIf { it.isString }?.let { literal ->
        PartiallyKnownString(StringEntry.Known(literal.value as String, literal.sourcePsi!!, TextRange(0, literal.sourcePsi!!.textLength)))
      }
    }

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      valueProviders = listOf(myAnnoValueProvider)
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("'value'NULL", pks.debugConcatenation)
  }

  fun `test method filter`() {
    val file = myFixture.configureByText("MyFile.java", """
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
    """.trimIndent())

    val elementAtCaret = file.findElementAt(myFixture.caretOffset)?.parent?.toUElement() ?: fail("Cannot find UElement at caret")
    val pks = UStringEvaluator().calculateValue(elementAtCaret, UStringEvaluator.Configuration(
      methodCallDepth = 2,
      methodsToAnalyzePattern = psiMethod().withName("b")
    )) ?: fail("Cannot evaluate string")

    TestCase.assertEquals("{'b''a'}NULL", pks.debugConcatenation)
  }

  fun `test many assignments`() {
    val file = myFixture.configureByText("MyFile.java", """
      class MyFile {
        String b() {
          return "b";
        }
      
        String a() {
          String a0 = "a";
          ${
            (1..1000).map { """String a$it = a${it - 1} + (true ? "a" : b());""" }.joinToString("\n          ") { it }
          }
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

  private val PartiallyKnownString.debugConcatenation: String
    get() = buildString {
      for (segment in segments) {
        when (segment) {
          is StringEntry.Known -> append("'").append(segment.value).append("'")
          is StringEntry.Unknown -> {
            segment.possibleValues
              ?.map { it.debugConcatenation }
              ?.sorted()
              ?.joinTo(this, "|", "{", "}") { it }
            ?: append("NULL")
          }
        }
      }
    }
}