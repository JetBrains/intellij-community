// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.api.WslTest
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl
import com.intellij.python.community.services.systemPython.impl.UpdateCacheDelayer
import com.intellij.python.junit5Tests.framework.applicationScope
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest
import kotlin.time.Duration.Companion.seconds

@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS])
internal class SystemPythonServiceStaleDescriptorTest {
  companion object {
    private lateinit var sut: SystemPythonServiceImpl
    private val startUpdate = Mutex(locked = true)
    private val updateFinished = Mutex(locked = true)
    private val scope = applicationScope("${SystemPythonServiceStaleDescriptorTest}")

    @BeforeAll
    @JvmStatic
    fun setUp() {
      sut = SystemPythonServiceImpl(scope.get()) {
        UpdateCacheDelayer {
          startUpdate.lock() // Start cache update when lock is opened
          ({
            updateFinished.unlock() // this lock will be opened as soon as cache updated
          })
        }
      }
    }

    @AfterAll
    @JvmStatic
    fun makeSureNoEelLeaked(): Unit = timeoutRunBlocking(30.seconds) {
      startUpdate.unlock() // Start cache update
      updateFinished.lock() // Wait for its end
    }
  }

  @ParameterizedTest
  @EelSource
  @DockerTest("python:3.14.2-trixie", mandatory = false)
  @WslTest("Ubuntu-22.04", mandatory = false)
  fun testPythonOnDocker(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    Assertions.assertTrue(sut.findSystemPythons(eelHolder.eel).isNotEmpty(), "No pythons found on ${eelHolder.eel}")
  }
}