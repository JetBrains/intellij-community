// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report.publisher

import com.intellij.ide.starter.models.IDEStartResult

interface ReportPublisher {
  fun publish(ideStartResult: IDEStartResult)
}