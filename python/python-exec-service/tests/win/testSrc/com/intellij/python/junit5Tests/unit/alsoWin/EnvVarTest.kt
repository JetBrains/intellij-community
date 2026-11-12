// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.python.community.execService.EnvVar
import com.intellij.python.community.execService.EnvVarVal
import com.intellij.python.community.execService.impl.toMap
import com.intellij.python.community.execService.toEnvVars
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource


@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
internal class EnvVarTest {
  @ParameterizedTest
  @EelSource
  fun testEnvJoin(eelHolder: EelHolder) {
    val eel = eelHolder.eel
    val home = eel.fs.user.home
    val anotherDir = home.resolve("someDir")
    val osFamily = eel.platform.osFamily

    val envVars = mapOf("ENV1" to "BUG", "ENV2" to "VAL2").toEnvVars().toMutableList()
    envVars.add(EnvVar("ENV1", "VAL1"))
    envVars.add(EnvVar("MYPATH", EnvVarVal.NioPath(home.asNioPath())))
    envVars.add(EnvVar("MYPATH".let { if (osFamily.isWindows) it.lowercase() else it }, EnvVarVal.RemotePath(anotherDir.toString())))

    val map = envVars.toList().toMap(osFamily)
    Assertions.assertEquals("VAL1", map["ENV1"])
    Assertions.assertEquals("VAL2", map["ENV2"])
    val paths = map["MYPATH"]!!.split(osFamily.pathSeparator)
    assertThat(paths).hasSize(2).containsAll(listOf(home.toString(), anotherDir.toString()))
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun testSamePathIsRemoved(osFamily: EelOsFamily) {
    val path = when (osFamily) {
      EelOsFamily.Posix -> "/venv"
      EelOsFamily.Windows -> "c:\\venv"
    }
    val envVars = listOf(
      EnvVar("VIRTUAL_ENV", EnvVarVal.RemotePath(path)),
      EnvVar("VIRTUAL_ENV", EnvVarVal.RemotePath(path)),
    )
    Assertions.assertEquals(path, envVars.toMap(osFamily)["VIRTUAL_ENV"])
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun testBareStrIsNotTrimmed(osFamily: EelOsFamily) {
    val value = "${osFamily.pathSeparator}value${osFamily.pathSeparator}"
    Assertions.assertEquals(value, listOf(EnvVar("VAR", value)).toMap(osFamily)["VAR"])
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun testEmptyPathIsNotAdded(osFamily: EelOsFamily) {
    val path = when (osFamily) {
      EelOsFamily.Posix -> "/foo"
      EelOsFamily.Windows -> "c:\\foo"
    }
    val envVars = listOf(
      EnvVar("MYPATH", ""),
      EnvVar("MYPATH", EnvVarVal.RemotePath(path)),
      EnvVar("MYPATH", EnvVarVal.RemotePath("")),
      EnvVar("MYPATH", EnvVarVal.RemotePath(osFamily.pathSeparator)),
    )
    Assertions.assertEquals(path, envVars.toMap(osFamily)["MYPATH"])
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun testPathIsAddedAfterBareStrInSameOrder(osFamily: EelOsFamily) {
    val (first, second) = when (osFamily) {
      EelOsFamily.Posix -> "/first" to "/second"
      EelOsFamily.Windows -> "c:\\first" to "c:\\second"
    }
    val envVars = listOf(
      EnvVar("MYPATH", first),
      EnvVar("MYPATH", EnvVarVal.RemotePath(second)),
    )
    Assertions.assertEquals("$first${osFamily.pathSeparator}$second", envVars.toMap(osFamily)["MYPATH"])
  }

  @ParameterizedTest
  @EnumSource(EelOsFamily::class)
  fun testPathCaseSensitivity(osFamily: EelOsFamily) {
    val (first, second) = when (osFamily) {
      EelOsFamily.Posix -> "/Foo" to "/foo"
      EelOsFamily.Windows -> "C:\\Foo" to "c:\\foo"
    }
    val envVars = listOf(
      EnvVar("MYPATH", EnvVarVal.RemotePath(first)),
      EnvVar("MYPATH", EnvVarVal.RemotePath(second)),
    )
    val expected = when (osFamily) {
      // POSIX paths are case-sensitive: these are two different directories.
      EelOsFamily.Posix -> "$first${osFamily.pathSeparator}$second"
      // Windows paths are case-insensitive: this is one directory. The first spelling stays.
      EelOsFamily.Windows -> first
    }
    Assertions.assertEquals(expected, envVars.toMap(osFamily)["MYPATH"])
  }
}
