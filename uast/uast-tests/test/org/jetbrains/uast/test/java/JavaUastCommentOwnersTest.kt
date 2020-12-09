// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import junit.framework.TestCase
import org.jetbrains.uast.*
import org.junit.Test


class JavaUastCommentOwnersTest : AbstractJavaUastTest() {

  override fun check(testName: String, file: UFile) {
    with(file) {
      // check file comments
      file.ensureHasComments("// file", "/* file */")

      // check class level declarations comments
      classes.single { it.javaPsi.nameIdentifier?.text == "CommentOwners" }.ensureHasComments("/** CommentOwners */").apply {
        fields.single().ensureHasComments("/** field */")
        methods.single { it.isConstructor }.ensureHasComments("/** constructor */")
        innerClasses.single().ensureHasComments("/** NestedClass */")

        methods.single { it.name == "method" }.ensureHasComments("/** method */").apply {
          // check fun declaration parameters comments
          uastParameters.single().ensureHasComments("/* fun param before */", "/* fun param after */")

          // check expressions comments
          (uastBody as UBlockExpression).apply {
            ensureHasComments(
              "/* method call */",
              "// cycle",
              "// if",
              "// localValueDefinition"
            )

            expressions
              .singleIsInstance<UCallExpression> { it.methodName == "method" }
              .valueArguments.single()
              .ensureHasComments("/* call arg before */", "/* call arg after */")
          }
        }
      }

      // check enum values comments
      classes.single { it.javaPsi.nameIdentifier?.text == "MyBooleanEnum" }.ensureHasComments("/** enum */").apply {
        fields.asIterable().apply {
          singleIsInstance<UEnumConstantEx> { it.javaPsi.nameIdentifier.text == "TRUE" }.ensureHasComments("/** enum true value */")
          singleIsInstance<UEnumConstantEx> { it.javaPsi.nameIdentifier.text == "FALSE" }.ensureHasComments("/** enum false value */")
        }
      }
    }
  }

  @Test
  fun testCommentOwners() = doTest("Simple/CommentOwners.java")
}

private inline fun <reified T> Iterable<*>.singleIsInstance(predicate: (T) -> Boolean): T = filterIsInstance<T>().single(predicate)

private inline fun <reified U : UElement> U.ensureHasComments(vararg comments: String): U =
  also { TestCase.assertEquals(comments.toSet(), this.comments.mapTo(mutableSetOf()) { it.text.trim() }) }