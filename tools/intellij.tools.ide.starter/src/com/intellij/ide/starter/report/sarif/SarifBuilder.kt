// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report.sarif

import com.jetbrains.qodana.sarif.model.*

fun sarifReport(sarif: SarifReport, init: SarifReport.() -> Unit): SarifReport {
  sarif.init()
  return sarif
}

fun sarifRun(run: Run, init: Run.() -> Unit): Run {
  run.init()
  return run
}

fun driver(driver: ToolComponent, init: ToolComponent.() -> Unit): ToolComponent {
  driver.init()
  return driver
}

fun taxa(init: ReportingDescriptor.() -> Unit): ReportingDescriptor {
  val taxa = ReportingDescriptor()
  taxa.init()
  return taxa
}

fun invocation(init: Invocation.() -> Unit): Invocation {
  val invocation = Invocation()
  invocation.init()
  return invocation
}

fun result(init: Result.() -> Unit): Result {
  val result = Result()
  result.init()
  return result
}

fun result(result: Result, init: Result.() -> Unit): Result {
  result.init()
  return result
}

fun versionControlProvenance(init: VersionControlDetails.() -> Unit): VersionControlDetails {
  val versionControl = VersionControlDetails()
  versionControl.init()
  return versionControl
}