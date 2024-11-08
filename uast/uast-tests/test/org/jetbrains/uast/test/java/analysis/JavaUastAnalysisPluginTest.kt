// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java.analysis

import com.intellij.lang.java.JavaLanguage
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.java.analysis.JavaUastAnalysisPlugin
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class JavaUastAnalysisPluginTest : LightJavaCodeInsightFixtureTestCase() {
  @Test
  fun `test dataflow not null`() = doTest("""
      void dataFlowNotNull(boolean b) {
          String a = b ? null : "hello";
        
          if (a != null) {
              System.out.println(/*NOT_NULL*/a);
          }
      }
    """.trimIndent())

  @Test
  fun `test not null declaration`() = doTest("""
      void notNullDeclaration() {
          String a = "hello";
          System.out.println(/*NOT_NULL*/a);
      }
    """.trimIndent())

  @Test
  fun `test nullable declaration`() = doTest("""
      void nullableDeclaration(boolean b) {
          String a = b ? null : "hello";
          System.out.println(/*NULLABLE*/ a);
      }
    """.trimIndent())

  @Test
  @Suppress("StringBufferReplaceableByString")
  fun `test platform types`() = doTest("""
      void platformTypes() {
          System.out.println(/*NOT_NULL*/ new java.lang.StringBuilder().append("a"));
      }
    """.trimIndent())

  @Test
  fun `test unknown type`() = doTest("""
      void typeNotNull(StringBuilder builder) {
          System.out.println(/*UNKNOWN*/builder);
      }
    """.trimIndent())

  @Test
  fun `test type with nullable annotation`() = doTest("""
      import org.jetbrains.annotations.Nullable;    
       
      void typeNullable(@Nullable StringBuilder builder) {
          System.out.println(/*NULLABLE*/builder);
      }
    """.trimIndent())

  @Test
  fun `test ternary operator nullability`() = doTest("""
      void ternaryOperator(boolean b) {
          String a = b ? null : "hello";
          System.out.println(/*NOT_NULL*/ (a != null ? a : ""));
      }
    """.trimIndent())

  @Test
  fun `test null expression`() = doTest("""
      void nullExpression() {
          System.out.println(/*NULL*/ null);
      }
    """.trimIndent())

  @Test
  fun `test nullability of parameter with dfa`() = doTest("""
      import org.jetbrains.annotations.Nullable;    
  
      void nullableParamWithDfa(@Nullable Integer p) {
          if (p != null) {
              System.out.println(/*NOT_NULL*/p);
          }
      }
    """.trimIndent())

  @Test
  @Suppress("SwitchStatementWithTooFewBranches")
  fun `test nullability of switch expression`() = doTest("""
      import org.jetbrains.annotations.Nullable;    
      
      void notNullIfExpression(@Nullable Integer d) {
          var a = switch (d) {
              case null -> 1;
              default -> d;
          };
          
          System.out.println(/*NOT_NULL*/a);
      }
    """.trimIndent())

  @Test
  fun `test nullability with platform type and if`() = doTest("""
      void platformWithIf(StringBuilder a) {    
          if (a != null) {
              System.out.println(/*NOT_NULL*/a);
          } else {
              "a";
          }
      }
    """.trimIndent())

  @Suppress("ConstantValue")
  @Test
  fun `test if and ternary`() = doTest("""
      import org.jetbrains.annotations.Nullable;
      
      void notNullIfWithElvis(@Nullable String a) {
            if (a != null) {
                System.out.println(/*NOT_NULL*/a);
            } else {
                System.out.println(/*NOT_NULL*/ (a != null ? a : "a"));
            }    
      }
    """.trimIndent())

  @Test
  fun `test complex if condition`() = doTest("""
      import org.jetbrains.annotations.Nullable;    
  
      void twoNotNull(@Nullable String a, @Nullable String b) {
          if (a != null && b != null) {
              System.out.println(/*NOT_NULL*/a);
          }
      }
    """.trimIndent())
  
  private fun doTest(@Language("java") source: String) {
    val uastAnalysisPlugin = UastLanguagePlugin.byLanguage(JavaLanguage.INSTANCE)?.analysisPlugin
    assertInstanceOf(uastAnalysisPlugin, JavaUastAnalysisPlugin::class.java)
    val file = myFixture.configureByText("file.java", source).toUElement()

    checkNotNull(file)
    checkNotNull(uastAnalysisPlugin)

    var visitAny = false
    file.accept(object : AbstractUastVisitor() {
      override fun visitExpression(node: UExpression): Boolean {
        val uNullability = node.comments.firstOrNull()?.text
                             ?.removePrefix("/*")
                             ?.removeSuffix("*/")
                             ?.trim()
                             ?.let { UNullability.valueOf(it) } ?: return super.visitExpression(node)
        visitAny = true

        with(uastAnalysisPlugin) {
          assertEquals("Failed for ${node.asRenderString()}", uNullability, node.getExpressionFact(UExpressionFact.UNullabilityFact))
        }

        return super.visitExpression(node)
      }
    })

    assertTrue(visitAny)
  }
}