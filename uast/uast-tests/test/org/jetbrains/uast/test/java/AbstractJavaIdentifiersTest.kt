/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.uast.test.java

import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.test.common.IdentifiersTestBase
import java.io.File

abstract class AbstractJavaIdentifiersTest : AbstractJavaUastTest(), IdentifiersTestBase {
  protected var _evaluatorExtension: UEvaluatorExtension? = null

  private fun getTestFile(testName: String, ext: String) =
    File(File(TEST_JAVA_MODEL_DIR, testName).canonicalPath.substringBeforeLast('.') + '.' + ext)

  override fun getIdentifiersFile(testName: String): File = getTestFile(testName, "identifiers.txt")
}
