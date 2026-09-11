// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.api.WslTest
import com.intellij.python.community.services.systemPython.impl.binaryStamp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest
import java.nio.file.Files
import kotlin.io.path.writeText

/**
 * The search stamps an interpreter on any machine the IDE reaches, so the file attributes must be readable through
 * an eel file system as well. See PY-88315.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS])
internal class BinaryStampEelTest {

  @ParameterizedTest
  @EelSource
  @DockerTest("python:3.14.2-trixie", mandatory = false)
  @WslTest("Ubuntu-22.04", mandatory = false)
  fun testStampOfAFileOnEel(eelHolder: EelHolder) {
    val file = eelHolder.eel.userInfo.home.asNioPath().resolve("binary-stamp-test")
    try {
      file.writeText("one")
      val before = file.binaryStamp()
      assertThat(before).describedAs("No stamp for $file on ${eelHolder.type}").isNotNull

      file.writeText("a longer text")
      assertThat(file.binaryStamp()).describedAs("The size changed").isNotEqualTo(before)
    }
    finally {
      Files.deleteIfExists(file)
    }
  }
}
