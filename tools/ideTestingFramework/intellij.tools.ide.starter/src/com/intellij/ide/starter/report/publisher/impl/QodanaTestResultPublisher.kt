// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report.publisher.impl

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.report.qodana.QodanaClient
import com.intellij.ide.starter.report.sarif.TestContextToQodanaSarifMapper

object QodanaTestResultPublisher : ReportPublisher {

  override fun publish(ideStartResult: IDEStartResult) {
    QodanaClient.report(TestContextToQodanaSarifMapper.map(ideStartResult))
  }

}