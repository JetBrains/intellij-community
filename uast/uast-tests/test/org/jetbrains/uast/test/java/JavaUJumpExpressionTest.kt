// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.castSafelyTo
import junit.framework.TestCase
import org.jetbrains.uast.*

abstract class JavaUJumpExpressionBase : AbstractJavaUastLightTest() {
  protected inline fun <reified TElement : UElement, reified TJumpFromElement> doTest(fileSource: String) {
    val file = myFixture.configureByText("File.java", fileSource)
    val element = file.findElementAt(myFixture.editor.caretModel.offset)?.parent?.toUElementOfType<TElement>()
                  ?: fail("cannot find element")
    UsefulTestCase.assertInstanceOf((element as? UJumpExpression)?.jumpTarget, TJumpFromElement::class.java)
  }

  protected inline fun <reified TElement : UJumpExpression> doTestWithNullTarget(fileSource: String) {
    val file = myFixture.configureByText("File.java", fileSource)
    val element = file.findElementAt(myFixture.editor.caretModel.offset)?.parent?.toUElementOfType<TElement>()
                  ?: fail("cannot find element")
    TestCase.assertNull(element.jumpTarget)
  }
}

class JavaUJumpExpressionTest : JavaUJumpExpressionBase() {
  fun `test break`() = doTest<UBreakExpression, UForExpression>("""
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

  fun `test break with label`() = doTest<UBreakExpression, UWhileExpression>("""
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

  fun `test break in switch`() = doTest<UBreakExpression, USwitchExpression>("""
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

  fun `test continue`() = doTest<UContinueExpression, UForExpression>("""
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

  fun `test continue with label`() = doTest<UContinueExpression, UWhileExpression>("""
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

  fun `test return`() = doTest<UReturnExpression, UMethod>("""
    class Break {
        static void a() {
          ret<caret>urn;
        }
      }
  """)

  fun `test return from lambda`() = doTest<UReturnExpression, ULambdaExpression>("""
    class Break {
        static void a() {
          Supplier a = () -> {
          ret<caret>urn a;
          }
        }
      }
  """)

  fun `test return from inner method`() = doTest<UReturnExpression, UMethod>("""
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

  fun `test implicit return`() {
    val lambda = JavaPsiFacade.getElementFactory(project).createExpressionFromText("() -> 10", null)
                   .toUElementOfType<ULambdaExpression>() ?: fail("cannot create lambda")

    val returnExpr = (lambda.body as? UBlockExpression)?.expressions?.singleOrNull()?.castSafelyTo<UReturnExpression>()
    TestCase.assertEquals((returnExpr as? UJumpExpression)?.jumpTarget, lambda)
  }

  fun `test strange break`() = doTestWithNullTarget<UBreakExpression>("""
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

  fun `test strange continue`() = doTestWithNullTarget<UContinueExpression>("""
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

class Java13UJumpExpressionTest : JavaUJumpExpressionBase() {

  fun `test break in switch`() = doTest<UYieldExpression, USwitchExpression>("""
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
