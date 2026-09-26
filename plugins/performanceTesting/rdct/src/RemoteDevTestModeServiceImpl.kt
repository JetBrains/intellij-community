// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.rdct

import com.intellij.remoteDev.tests.DistributedTestsAgentConstants
import com.intellij.remoteDev.tests.LambdaTestsConstants
import com.intellij.remoteDev.tests.RemoteDevTestModeService

internal class RemoteDevTestModeServiceImpl : RemoteDevTestModeService {
  override val isDistributedTestMode: Boolean
    get() = System.getProperty(DistributedTestsAgentConstants.protocolPortPropertyName)?.toIntOrNull() != null

  override val isLambdaTestMode: Boolean
    get() = System.getProperty(LambdaTestsConstants.protocolPortPropertyName)?.toIntOrNull() != null
}
