// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report.publisher.impl

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.utils.logOutput

object ConsoleTestResultPublisher : ReportPublisher {

  override fun publish(ideStartResult: IDEStartResult) {
    logOutput("Console publisher")
  }

}