// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.platform.uast.testFramework.common.ValuesTestBase
import org.jetbrains.uast.UFile
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUFile

abstract class AbstractJavaValuesTest : AbstractJavaUastTest(), ValuesTestBase {
  private var _evaluatorExtension: UEvaluatorExtension? = null

  override fun getEvaluatorExtension(): UEvaluatorExtension? = _evaluatorExtension
  
  override fun createCopyToCheck(file: UFile): UFile? {
    // TODO: when JavaUFile (and/or its constructor) becomes `internal`, this should move into JavaUFile.
    return if (file is JavaUFile) JavaUFile(file.sourcePsi, file.languagePlugin) else null
  }

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