// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.asSafely
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UJumpExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.toUElementOfType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

private inline fun <reified TElement : UElement, reified TJumpFromElement> doTest(myFixture: CodeInsightTestFixture, fileSource: String) {
  val file = myFixture.configureByText("File.java", fileSource)
  ReadAction.runBlocking<RuntimeException> {
    val element = file.findElementAt(myFixture.editor.caretModel.offset)?.parent?.toUElementOfType<TElement>()
                  ?: fail("cannot find element")
    assertInstanceOf(TJumpFromElement::class.java, (element as? UJumpExpression)?.jumpTarget)
  }
}

private inline fun <reified TElement : UJumpExpression> doTestWithNullTarget(myFixture: CodeInsightTestFixture, fileSource: String) {
  val file = myFixture.configureByText("File.java", fileSource)
  ReadAction.runBlocking<RuntimeException> {
    val element = file.findElementAt(myFixture.editor.caretModel.offset)?.parent?.toUElementOfType<TElement>()
                  ?: fail("cannot find element")
    assertNull(element.jumpTarget)
  }
}

@TestApplication
class JavaUJumpExpressionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test break`(): Unit = doTest<UBreakExpression, UForExpression>(myFixture, """
      class Break {
        static void a() {
          while (true) {
            for (int i = 0; i < 10; i++) {  
              brea<caret>k;
            }
          }
        }
      }
    """)

  @Test
  fun `test break with label`(): Unit = doTest<UBreakExpression, UWhileExpression>(myFixture, """
      class Break {
        static void a() {
          a: while (true) {
            for (int i = 0; i < 10; i++) {  
              brea<caret>k a;
            }
          }
        }
      }
    """)

  @Test
  fun `test break in switch`(): Unit = doTest<UBreakExpression, USwitchExpression>(myFixture, """
      class Break {
        static void a() {
          while (true) {
            switch (1) {
              case 2: bre<caret>ak;
            }
          }
        }
      }
    """)

  @Test
  fun `test continue`(): Unit = doTest<UContinueExpression, UForExpression>(myFixture, """
      class Break {
        static void a() {
          while (true) {
            for (int i = 0; i < 10; i++) {  
              con<caret>tinue;
            }
          }
        }
      }
    """)

  @Test
  fun `test continue with label`(): Unit = doTest<UContinueExpression, UWhileExpression>(myFixture, """
      class Break {
        static void a() {
          a: while (true) {
            for (int i = 0; i < 10; i++) {  
              con<caret>tinue a;
            }
          }
        }
      }
    """)

  @Test
  fun `test return`(): Unit = doTest<UReturnExpression, UMethod>(myFixture, """
    class Break {
        static void a() {
          ret<caret>urn;
        }
      }
  """)

  @Test
  fun `test return from lambda`(): Unit = doTest<UReturnExpression, ULambdaExpression>(myFixture, """
    class Break {
        static void a() {
          Supplier a = () -> {
          ret<caret>urn a;
          }
        }
      }
  """)

  @Test
  fun `test return from inner method`(): Unit = doTest<UReturnExpression, UMethod>(myFixture, """
    class Break {
      static Consumer a = (b) -> {
        new Object() {
          Object kek() {
            ret<caret>urn b;
          }
        };
      };
    }
  """)

  @Test
  fun `test implicit return`(): Unit = ReadAction.runBlocking<RuntimeException> {
    val lambda = JavaPsiFacade.getElementFactory(myFixture.project).createExpressionFromText("() -> 10", null)
                   .toUElementOfType<ULambdaExpression>() ?: fail("cannot create lambda")

    val returnExpr = (lambda.body as? UBlockExpression)?.expressions?.singleOrNull()?.asSafely<UReturnExpression>()
    assertEquals((returnExpr as? UJumpExpression)?.jumpTarget, lambda)
  }

  @Test
  fun `test strange break`(): Unit = doTestWithNullTarget<UBreakExpression>(myFixture, """
    class Break {
      static void a() {
         while (true) {
          for (int i = 0; i < 10; i++) {  
            a: brea<caret>k a;
          }
        }
      }
    }
  """)

  @Test
  fun `test strange continue`(): Unit = doTestWithNullTarget<UContinueExpression>(myFixture, """
    class Break {
      static void a() {
         while (true) {
          for (int i = 0; i < 10; i++) {  
            a: continu<caret>e a;
          }
        }
      }
    }
  """)
}

@TestApplication
class Java13UJumpExpressionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test break in switch`(): Unit = doTest<UYieldExpression, USwitchExpression>(myFixture, """
      class Break {
        static void a() {
          while (true) {
            int a = switch (1) {
              case 2: yie<caret>ld 10;
              default: yield 15;
            }
          }
        }
      }
    """)
}
