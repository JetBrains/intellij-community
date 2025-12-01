// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures.util

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.removeUnitTestMode
import com.intellij.ide.starter.config.setUnitTestMode
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class SetUnitTestMode : BeforeAllCallback, BeforeEachCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) = ConfigurationStorage.setUnitTestMode()
  override fun beforeEach(context: ExtensionContext) = ConfigurationStorage.setUnitTestMode()
  override fun afterAll(context: ExtensionContext) = ConfigurationStorage.removeUnitTestMode()
}