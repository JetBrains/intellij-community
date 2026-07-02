// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lambda.sampleTestsWithFixtures.util

import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.reused.recovery.forceKillAndAwaitDeath
import com.intellij.lambda.testFramework.starter.LambdaReusedSessionStrategy
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Force-kills the IDE, because an IDE that runs test fixtures does not exit on a graceful close.
 */
internal class FixturesLambdaSessionStrategy : LambdaReusedSessionStrategy() {
  override fun closeIde(context: ExtensionContext, bgRun: BackgroundRun) {
    // TODO: remove this strategy when an IDE that runs test fixtures exits on a graceful close.
    forceKillAndAwaitDeath(bgRun)
  }
}
