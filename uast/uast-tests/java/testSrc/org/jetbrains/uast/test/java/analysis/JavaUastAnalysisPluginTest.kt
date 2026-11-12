// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java.analysis

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.java.analysis.JavaUastAnalysisPlugin
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
internal class JavaUastAnalysisPluginTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test dataflow not null`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      void dataFlowNotNull(boolean b) {
          String a = b ? null : "hello";
        
          if (a != null) {
              System.out.println(/*NOT_NULL*/a);
          }
      }
    """.trimIndent()) }

  @Test
  fun `test not null declaration`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      void notNullDeclaration() {
          String a = "hello";
          System.out.println(/*NOT_NULL*/a);
      }
    """.trimIndent()) }

  @Test
  fun `test nullable declaration`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      void nullableDeclaration(boolean b) {
          String a = b ? null : "hello";
          System.out.println(/*NULLABLE*/ a);
      }
    """.trimIndent()) }

  @Test
  @Suppress("StringBufferReplaceableByString")
  fun `test platform types`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      void platformTypes() {
          System.out.println(/*NOT_NULL*/ new java.lang.StringBuilder().append("a"));
      }
    """.trimIndent()) }

  @Test
  fun `test unknown type`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      void typeNotNull(StringBuilder builder) {
          System.out.println(/*UNKNOWN*/builder);
      }
    """.trimIndent()) }

  @Test
  fun `test type with nullable annotation`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      import org.jetbrains.annotations.Nullable;    
       
      void typeNullable(@Nullable StringBuilder builder) {
          System.out.println(/*NULLABLE*/builder);
      }
    """.trimIndent()) }

  @Test
  fun `test ternary operator nullability`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      void ternaryOperator(boolean b) {
          String a = b ? null : "hello";
          System.out.println(/*NOT_NULL*/ (a != null ? a : ""));
      }
    """.trimIndent()) }

  @Test
  fun `test null expression`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      void nullExpression() {
          System.out.println(/*NULL*/ null);
      }
    """.trimIndent()) }

  @Test
  fun `test nullability of parameter with dfa`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      import org.jetbrains.annotations.Nullable;    
  
      void nullableParamWithDfa(@Nullable Integer p) {
          if (p != null) {
              System.out.println(/*NOT_NULL*/p);
          }
      }
    """.trimIndent()) }

  @Test
  @Suppress("SwitchStatementWithTooFewBranches")
  fun `test nullability of switch expression`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      import org.jetbrains.annotations.Nullable;    
      
      void notNullIfExpression(@Nullable Integer d) {
          var a = switch (d) {
              case null -> 1;
              default -> d;
          };
          
          System.out.println(/*NOT_NULL*/a);
      }
    """.trimIndent()) }

  @Test
  fun `test nullability with platform type and if`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      void platformWithIf(StringBuilder a) {    
          if (a != null) {
              System.out.println(/*NOT_NULL*/a);
          } else {
              "a";
          }
      }
    """.trimIndent()) }

  @Suppress("ConstantValue")
  @Test
  fun `test if and ternary`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      import org.jetbrains.annotations.Nullable;
      
      void notNullIfWithElvis(@Nullable String a) {
            if (a != null) {
                System.out.println(/*NOT_NULL*/a);
            } else {
                System.out.println(/*NOT_NULL*/ (a != null ? a : "a"));
            }    
      }
    """.trimIndent()) }

  @Test
  fun `test complex if condition`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      import org.jetbrains.annotations.Nullable;    
  
      void twoNotNull(@Nullable String a, @Nullable String b) {
          if (a != null && b != null) {
              System.out.println(/*NOT_NULL*/a);
          }
      }
    """.trimIndent()) }

  @Test
  fun `test nullable field with primitive types`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      import org.jetbrains.annotations.Nullable;    
  
      class SomeClass {
          private @Nullable /*NULLABLE*/ String a;
          private final @Nullable /*NULLABLE*/ Integer b;
          private final @Nullable /*NULLABLE*/ Integer c;
  
          SomeClass(@Nullable String a, @Nullable Integer b, @Nullable Integer c) {
              this.a = a;
              this.b = b;
              this.c = c;
          }
      }
    """.trimIndent()) }

  @Test
  fun `test non nullable fields with primitive types`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      import org.jetbrains.annotations.NotNull;    
        
      class SomeClass {
          private final @NotNull /*NOT_NULL*/ String a;
          private final @NotNull /*NOT_NULL*/ Integer b;
            
          SomeClass(@NotNull String a, @NotNull Integer b) {
              this.a = a;
              this.b = b;
          }
      }
    """.trimIndent()) }

  @Test
  fun `test complex fields`(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest("""
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;
          
      class SomeClass {
          private final @NotNull /*NOT_NULL*/ String a; 
          private final @Nullable /*NULLABLE*/ Integer b;
          private final @Nullable /*NULLABLE*/ D c;
          private final @NotNull /*NOT_NULL*/ D d;
          
          SomeClass(@NotNull String a, @Nullable Integer b, @Nullable D c, @NotNull D d) {
              this.a = a;
              this.b = b;
              this.c = c;
              this.d = d;
          }
      }
          
      class D {}
    """.trimIndent()) }

  private suspend fun doTest(@Language("java") source: String) {
    val uastAnalysisPlugin = UastLanguagePlugin.byLanguage(JavaLanguage.INSTANCE)?.analysisPlugin
    assertInstanceOf(JavaUastAnalysisPlugin::class.java, uastAnalysisPlugin)
    val psiFile = myFixture.configureByText("file.java", source)
    readAction {
      val file = psiFile.toUElement()

      checkNotNull(file)
      checkNotNull(uastAnalysisPlugin)

      var visitAny = false
      file.accept(object : AbstractUastVisitor() {

        override fun visitField(node: UField): Boolean {
          val typeReference = node.typeReference ?: return super.visitField(node)
          return visitExpression(typeReference)
        }

        override fun visitExpression(node: UExpression): Boolean {
          val uNullability = node.comments.firstOrNull()?.text
                               ?.removePrefix("/*")
                               ?.removeSuffix("*/")
                               ?.trim()
                               ?.let { UNullability.valueOf(it) } ?: return super.visitExpression(node)
          visitAny = true

          with(uastAnalysisPlugin) {
            assertEquals(uNullability, node.getExpressionFact(UExpressionFact.UNullabilityFact), "Failed for ${node.asRenderString()}")
          }

          return super.visitExpression(node)
        }
      })

      assertTrue(visitAny)
    }
  }
}