// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report.sarif

import com.intellij.ide.starter.models.IDEStartResult
import com.jetbrains.qodana.sarif.SarifUtil
import com.jetbrains.qodana.sarif.model.SarifReport
import java.nio.file.Path

object TestContextToQodanaSarifMapper {

  fun map(ideStartResult: IDEStartResult): SarifReport {

    val defaultReportPath = this::class.java.classLoader.getResource("sarif/qodana.sarif.json")?.path
    if (defaultReportPath == null) throw RuntimeException("Default report doesn' exits")
    val sarifReport = SarifUtil.readReport(Path.of(defaultReportPath))

    return sarifReport(sarifReport) {
      sarifRun(runs[0]) {
        driver(tool.driver) {

          taxa.add(taxa {
            id = ideStartResult.context.testName
            name = ideStartResult.context.testName
          })
        }

        invocations.add(invocation {
          exitCode = if (ideStartResult.failureError == null) 0 else 1
          executionSuccessful = ideStartResult.failureError == null
        })

        versionControlProvenance.add(versionControlProvenance {

        })

        results.add(result {
          ruleId = "Perf test"
        })
      }
    }
  }
}