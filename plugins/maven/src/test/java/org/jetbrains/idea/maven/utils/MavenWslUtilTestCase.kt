// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.RunAll
import junit.framework.TestCase
import com.intellij.maven.testFramework.MavenTestCase
import org.jetbrains.idea.maven.utils.MavenWslUtil.getWindowsFile
import org.jetbrains.idea.maven.utils.MavenWslUtil.getWslFile
import org.jetbrains.idea.maven.utils.MavenWslUtil.resolveLocalRepository
import org.jetbrains.idea.maven.utils.MavenWslUtil.resolveM2Dir
import org.jetbrains.idea.maven.utils.MavenWslUtil.resolveUserSettingsFile
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.io.IOException

class MavenWslUtilTestCase : MavenTestCase() {
  private lateinit var ourWslTempDir: File
  private lateinit var myDistribution: WSLDistribution
  private lateinit var myWslDir: File
  private lateinit var myUserHome: File

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    Assume.assumeTrue("Windows only", SystemInfo.isWindows)
    Assume.assumeFalse("WSL should be installed", WslDistributionManager.getInstance().installedDistributions.isEmpty())
    myDistribution = WslDistributionManager.getInstance().installedDistributions[0]

    val user = myDistribution.environment?.get("USER")
    if (user == null){
      fail("Cannot retrieve env variables from WSL")
    }
    myUserHome = File("\\\\wsl$\\${myDistribution.msId}\\home\\${user}")
    ensureWslTempDirCreated()
  }

  @Throws(IOException::class)
  private fun ensureWslTempDirCreated() {
    ourWslTempDir = File(myDistribution!!.getWindowsPath("/tmp"), "mavenTests")
    myWslDir = File(ourWslTempDir, getTestName(false))
    FileUtil.delete(ourWslTempDir!!)
    FileUtil.ensureExists(myWslDir!!)
  }

  @Throws(Exception::class)
  public override fun tearDown() {
    RunAll(
      { deleteDirOnTearDown(ourWslTempDir) },
      { super.tearDown() }
    ).run()
  }

  @Test
  fun testShouldReturnMavenLocalDirOnWsl() {
    TestCase.assertEquals(
      File(File(myUserHome, ".m2"), "repository"),
      myDistribution.resolveLocalRepository(null, null, null));
  }

  @Test
  fun testShouldReturnMavenLocalSettings() {
    TestCase.assertEquals(
      File(File(myUserHome, ".m2"), "settings.xml"),
      myDistribution.resolveUserSettingsFile(null))
  }

  @Test
  fun testShouldReturnMavenRepoForOverloadedSettings() {

    val subFile = createProjectSubFile("settings.xml",
                                       "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                       "      xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">\n" +
                                       "      <localRepository>/tmp/path/to/repo</localRepository>\n" +
                                       "</settings>");
    TestCase.assertEquals(
      File(myDistribution.getWindowsPath("/tmp/path/to/repo")!!),
      myDistribution.resolveLocalRepository(null, null, subFile.path));
  }

  @Test
  fun testShouldReturnCorrectM2Dir() {
    TestCase.assertEquals(
      File(myUserHome, ".m2"),
      myDistribution.resolveM2Dir())
  }

  @Test
  fun testWindowFileMapInMnt() {
    TestCase.assertEquals(File("c:\\somefile"), myDistribution.getWindowsFile(File("/mnt/c/somefile")));
  }

  @Test
  fun testWindowFileMapInternalWsl() {
    TestCase.assertEquals(File("\\\\wsl$\\${myDistribution.msId}\\somefile"), myDistribution.getWindowsFile(File("/somefile")))
  }

  @Test
  fun testWslFileMapInMnt() {
    TestCase.assertEquals(File("/mnt/c/somefile"), myDistribution.getWslFile(File("c:\\somefile")));
  }

  @Test
  fun testWslFileMapInternalWsl() {
    TestCase.assertEquals(File("/somefile"), myDistribution.getWslFile(File("\\\\wsl$\\${myDistribution.msId}\\somefile")))
  }
}