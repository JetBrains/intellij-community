// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report.qodana

import com.intellij.ide.starter.utils.logOutput
import com.jetbrains.qodana.sarif.model.SarifReport

object QodanaClient {

  fun report(sarifReport: SarifReport) {
    logOutput("QodanaClient report")
    //TODO("Implement client")
  }
}