// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable

@Deprecated("Use 'MavenExecutionTestCase'")
abstract class MavenExecutionTestCaseLegacy : MavenMultiVersionImportingTestCaseLegacy() {
  private lateinit var myFixture: MavenProjectJDKTestFixture

  public override fun setUp() {
    super.setUp()
    myFixture = MavenProjectJDKTestFixture(project, JDK_NAME)
    edt<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.setUp() })
    })

  }

  public override fun tearDown() {
    RunAll.runAll(
      {
        edt<RuntimeException?>(ThrowableRunnable {
          WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.tearDown() })

        })
      },
      { super.tearDown() }
    )
  }

  companion object {
    private const val JDK_NAME = "MavenExecutionTestJDK"
  }
}
