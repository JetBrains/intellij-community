// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.test.common.ValuesTestBase

abstract class AbstractJavaValuesTest : AbstractJavaUastTest(), ValuesTestBase {
  private var _evaluatorExtension: UEvaluatorExtension? = null

  override fun getEvaluatorExtension(): UEvaluatorExtension? = _evaluatorExtension

  fun doTest(name: String, extension: UEvaluatorExtension) {
    _evaluatorExtension = extension
    try {
      doTest(name)
    }
    finally {
      _evaluatorExtension = null
    }
  }
}