/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.test.java

import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.evaluation.uValueOf
import org.jetbrains.uast.java.internal.JavaUElementWithComments
import org.jetbrains.uast.visitor.UastVisitor


abstract class AbstractJavaExpressionValueTest : AbstractJavaUastTest() {

  override fun check(testName: String, file: UFile) {
    var valuesFound = 0
    file.accept(object : UastVisitor {
      override fun visitElement(node: UElement): Boolean {
        node as? JavaUElementWithComments ?: return false
        for (comment in node.comments) {
          val text = comment.text.removePrefix("/* ").removeSuffix(" */")
          val parts = text.split(" = ")
          if (parts.size != 2) continue
          when (parts[0]) {
            "constant" -> {
              val expectedValue = parts[1]
              val actualValue =
                  (node as? UExpression)?.uValueOf()?.toConstant()?.toString()
                  ?: "cannot evaluate $node of ${node.javaClass}"
              assertEquals(expectedValue, actualValue)
              valuesFound++
            }
          }
        }
        return false
      }
    })
    assertTrue("No values found, add some /* constant = ... */ to the input file", valuesFound > 0)
  }

}

