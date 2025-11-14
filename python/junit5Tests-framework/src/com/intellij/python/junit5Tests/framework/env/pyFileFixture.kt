// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env

import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import com.jetbrains.python.sdk.PySdkUtil.getLanguageLevelForSdk

fun  <ENV : Any> TestFixture<PsiDirectory>.pyFileFixture(name: String, content: String, sdkFixture: TestFixture<SdkFixture<ENV>>) = testFixture {
  val project = init().project
  val sdk = sdkFixture.init()
  val languageLevel = getLanguageLevelForSdk(sdk)
  val file = virtualFileFixture(name, content).init()
  val pusher = PythonLanguageLevelPusher()
  readAction {
    pusher.persistAttribute (project, file.parent, languageLevel)
  }
  initialized(PsiDocumentManager.getInstance(project).commitAndRunReadAction(Computable {
    PsiManager.getInstance(project).findFile(file) ?: error("Fail to find file ${file}")
  })) {
  }
}
