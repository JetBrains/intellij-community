// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.ci

import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind

/**
 * Reports a test infrastructure problem as an extra failed test of the kind [SyntheticTestKind.TEST_INFRA_EXCEPTION].
 * `AllureListener` reports missing Allure metadata in the same way.
 * The caller does not need to see [SyntheticTestKind].
 */
fun CIServer.reportTestInfraFailure(testName: String, message: String) {
  reportTestFailure(testName, message, details = "", kind = SyntheticTestKind.TEST_INFRA_EXCEPTION)
}
