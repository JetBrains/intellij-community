// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures.util

import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder.defaultLambdaPlugin
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class SetLambdaPluginCallback : BeforeAllCallback, AfterAllCallback {
  val localCustomLambdaPlugin = LambdaTestPluginHolder.AdditionalLambdaPlugin("intellij.lambda.sampleTestsWithFixtures._test",
                                                                              "intellij.lambda.sampleTestsWithFixtures.plugin",
                                                                              "lambda-sampleTestsWithFixtures-plugin")

  override fun beforeAll(context: ExtensionContext) {
    LambdaTestPluginHolder.setupAdditionalLambdaPlugins(localCustomLambdaPlugin.moduleID, listOf(localCustomLambdaPlugin, defaultLambdaPlugin))
  }

  override fun afterAll(context: ExtensionContext) {
    LambdaTestPluginHolder.cleanUpAdditionalLambdaPlugin()
  }
}