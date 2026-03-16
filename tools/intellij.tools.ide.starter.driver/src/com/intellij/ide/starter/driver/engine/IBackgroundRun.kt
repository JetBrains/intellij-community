package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDEHandle
import kotlinx.coroutines.Deferred
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface IBackgroundRun {
  val startResult: Deferred<IDEStartResult>
  val process: IDEHandle
  val driver: Driver
  fun forceKill()
  fun closeIdeAndWait(closeIdeTimeout: Duration = 1.minutes, takeScreenshot: Boolean = true)
}