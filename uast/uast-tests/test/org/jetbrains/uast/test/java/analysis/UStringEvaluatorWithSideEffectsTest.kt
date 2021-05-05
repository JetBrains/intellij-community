// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java.analysis

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PartiallyKnownString
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.analysis.UStringBuilderEvaluator
import org.jetbrains.uast.analysis.UStringEvaluator
import org.jetbrains.uast.getUCallExpression

class UStringEvaluatorWithSideEffectsTest : AbstractStringEvaluatorTest() {
  fun `test StringBuilder toString evaluate`() {
    doTest(
      """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("I am StringBuilder");
          StringBuilder sb1 = sb.append(". Hi!");
          
          sb.append("a");
          
          if (param) {
            sb.append("x");
          } else {
            sb1.append("z");
            sb.append("y");
          }
          
          sb.append("c").append("d");
          
          sb1.append("b");
          
          return sb.toStrin<caret>g();
        }
      }
      """.trimIndent(),
      """'I am StringBuilder''. Hi!''a'{'x'|'z''y'}'c''d''b'""",
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      },
      retrieveElement = UElement?::getUCallExpression
    )
  }

  fun `test candidates`() = doTest(
    """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("a");
          StringBuilder sb1 = sb;
          
          if (param) {
            sb1.append("x");
          } else {
            sb1.append("y");
          }
          
          sb.append("c");
          
          sb1.append("b");
          
          return sb.toStrin<caret>g();
        }
      }
    """.trimIndent(),
    "'a'{'x'|'y'}'c''b'",
    retrieveElement = UElement?::getUCallExpression,
    configuration = {
      UStringEvaluator.Configuration(
        builderEvaluators = listOf(UStringBuilderEvaluator)
      )
    }
  )

  fun `test mixed assignment and side effect changes for StringBuilder`() {
    doTest(
      """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("a");
          sb = sb.append("b");
          sb.append("c");
          sb = sb.append("d");
          sb.append("e");
          return sb.toStrin<caret>g();
        }
      }
    """.trimIndent(),
      """'a''b''c''d''e'""",
      retrieveElement = UElement?::getUCallExpression,
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      }
    )
  }

  @Suppress("unused")
  fun `ignore test StringBuilder change through possible reference`() {
    doTest(
      """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("a");
          StringBuilder sb1 = new StringBuilder("b");
          
          StringBuilder sb2 = param ? sb : sb1; // ignore sb1 because sb1 != sb
          
          s1.append("-");
          
          StringBuilder sb3 = sb2;
          
          sb3.append("c");  // add because of equality (strict = true)
          
          sb1.append("d"); // ignore (strict = false, witness incorrect)
          
          sb2.append("e"); // add "c" as optional (strict = false, witness correct)
          
          sb1.append("f"); // ignore (no connection)
          
          return sb.toStrin<caret>g(); // go to potential update from sb
        }
      }
    """.trimIndent(),
      """'a'{''|'c'}""",
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      },
      retrieveElement = UElement?::getUCallExpression
    )
  }

  fun `test StringBuilder update through another equal reference`() {
    doTest(
      """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("a");
          StringBuilder sb1 = sb.append("b");
          StringBuilder sb2 = sb1.append("c");
          sb2.append("d");
          sb2.append("e");
          return sb.toStrin<caret>g();
        }
      }
    """.trimIndent(),
      """'a''b''c''d''e'""",
      retrieveElement = UElement?::getUCallExpression,
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      }
    )
  }

  fun `test StringBuilder with if`() {
    doTest(
      """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("a");
          
          if (param) {
            sb.append("b");
          } else {
            sb.append("c");
          }
          
          return sb.toStrin<caret>g();
        }
      }
    """.trimIndent(),
      """'a'{'b'|'c'}""",
      retrieveElement = UElement?::getUCallExpression,
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      }
    )
  }

  fun `test StringBuilder reassignment and side effect change in if`() {
    doTest(
      """"
      class MyFile {
      String a(boolean param) {
        StringBuilder sb = new StringBuilder("a");

        if (param) {
          sb.append("b");
        } else {
          sb = new StringBuilder("c");
        }

        return sb.toStrin<caret>g();
      }
    }
    """.trimIndent(),
      """{'a''b'|'c'}""",
      retrieveElement = UElement?::getUCallExpression,
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      }
    )
  }

  fun `test StringBuilder reference reassignment to another object`() {
    doTest(
      """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("a");
          StringBuilder sb1 = sb.append("b");
          
          sb1.append("c");
          
          sb1 = new StringBuilder("d");
          
          sb1.append("e");
          
          return sb.toStrin<caret>g();
        }
      }
    """.trimIndent(),
      """'a''b''c'""",
      retrieveElement = UElement?::getUCallExpression,
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      }
    )
  }

  fun `test deep StringBuilder update by another reference`() {
    doTest(
      """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("a");
          StringBuilder sb1 = sb.append("b");
          
          if (param) {
            sb.append("c");
            if (!param) {
              sb1.append("d")
            } else {
              sb1.append("e");
            }
          } else {
            sb1.append("f");
          }
          
          return sb.toStrin<caret>g();
        }
      }
    """.trimIndent(),
      """'a''b'{'c''d'|'c''e'|'f'}""",
      retrieveElement = UElement?::getUCallExpression,
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      }
    )
  }

  fun `test StringBuilder update through reference with lower scope`() {
    doTest(
      """
        class MyFile {
          String a(boolean param) {
            StringBuilder sb = new StringBuilder("a");
            StringBuilder sb1 = sb.append("b");
            
            if (param) {
              StringBuilder sb2 = sb1.append("c");
              sb2.append("d");
            }
            
            return sb.toStrin<caret>g();
          }
        }
      """.trimIndent(),
      """'a''b'{|'c''d'}""",
      retrieveElement = UElement?::getUCallExpression,
      configuration = {
        UStringEvaluator.Configuration(
          builderEvaluators = listOf(UStringBuilderEvaluator)
        )
      }
    )
  }

  fun `test StringBuilder with false deep evidence`() = doTest(
    """
      class MyFile {
        String a(boolean param) {
          StringBuilder sb = new StringBuilder("a");
          StringBuilder sb1 = sb.clone();
          StringBuilder sb2 = sb1.append("c");
          
          sb2.append("dd");
          
          return sb.toStrin<caret>g();
        }
      }
    """.trimIndent(),
    "'a'",
    retrieveElement = UElement?::getUCallExpression,
    configuration = {
      UStringEvaluator.Configuration(
        builderEvaluators = listOf(CloneAwareStringBuilderEvaluator())
      )
    }
  )

  fun `test candidates in different scopes`() = doTest(
    """
      class MyFile {
        String falseEvidenceInIf(boolean param, boolean param2) {
          StringBuilder sb = new StringBuilder("aaa");
  
          if (param) {
            StringBuilder sb1 = sb.clone();
            if (param2) {
                sb1.append("c");
            }
            sb.append("d");
          } else {
            sb.append("e");
          }
  
          return sb.toSt<caret>ring();
        }
      }
    """.trimIndent(),
    "'aaa'{'d'|'e'}",
    retrieveElement = UElement?::getUCallExpression,
    configuration = {
      UStringEvaluator.Configuration(
        builderEvaluators = listOf(CloneAwareStringBuilderEvaluator())
      )
    }
  )

  fun `test false candidates with many branches`() = doTest(
    """
      class MyFile {
        String falseEvidenceInIf(boolean param, boolean param2, boolean param3) {
          StringBuilder sb = new StringBuilder("a");
          StringBuilder sb1 = sb.clone();
  
          if (param) {
            if (param2) {
              if (param3) {
                sb.append("1");
              } else {
                sb1.append("2");
              }
              sb1.append("b");
            } else {
              sb.append("c");
            }
          } else {
            sb.append("d");
          }
  
          return sb.toSt<caret>ring();
        }
      }
    """.trimIndent(),
    "'a'{'1'|'c'|'d'}",
    retrieveElement = UElement?::getUCallExpression,
    configuration = {
      UStringEvaluator.Configuration(
        builderEvaluators = listOf(CloneAwareStringBuilderEvaluator())
      )
    }
  )

  private class CloneAwareStringBuilderEvaluator : UStringEvaluator.BuilderLikeExpressionEvaluator<PartiallyKnownString?> by UStringBuilderEvaluator {
    override val methodDescriptions: Map<ElementPattern<PsiMethod>, (UCallExpression, PartiallyKnownString?, UStringEvaluator, UStringEvaluator.Configuration) -> PartiallyKnownString?>
      get() = UStringBuilderEvaluator.methodDescriptions + mapOf(
        PsiJavaPatterns.psiMethod().withName("clone") to { _, partiallyKnownString, _, _ ->
          partiallyKnownString
        }
      )
  }
}