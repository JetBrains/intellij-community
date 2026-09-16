// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.lang.JavaVersion
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile

@TestApplication
internal class ToolchainsResolverTest {
  private val maven by mavenFixture()

  @Test
  fun testShouldReturnSameResolutionSession() {
    val mavenSession = MavenSyncSession(maven.project, MavenSyncSpec.incremental("test"), MavenProjectsTree(maven.project))

    val first = ToolchainResolverSession.forSession(mavenSession)
    val second = ToolchainResolverSession.forSession(mavenSession)
    assertSame(first, second)
  }


  @Test
  fun testShouldInstallSdkInIdea() = runBlocking {
    val mavenSession = MavenSyncSession(maven.project, MavenSyncSpec.incremental("test"), MavenProjectsTree(maven.project))
    val toolchainsFile = createTempFile("test", "toolchains.xml")
    val sdkFile = createTempDirectory("testSdk")
    var jdk: Sdk? = null
    mavenSession.syncContext.putUserData(MavenSyncSession.TOOLCHAINS_FILE, toolchainsFile)
    write(toolchainsFile, """<?xml version="1.0" encoding="UTF-8"?>
      <toolchains>
        <toolchain>
          <type>jdk</type>
          <provides>
              <version>17</version>
          </provides>
          <configuration>
              <jdkHome>$sdkFile</jdkHome>
          </configuration>
        </toolchain>
      </toolchains>""")

    val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "17")
      .build()

    try {
      jdk = ToolchainResolverSession.forSession(mavenSession).findOrInstallJdk(requirement)
      assertNotNull(jdk)
    }
    finally {
      if (jdk != null) {
        edtWriteAction {
          SdkConfigurationUtil.removeSdk(jdk)
        }
      }
    }
  }

  @Test
  fun testShouldInstallSdkFromDiscoveredJdkToolchainCache() = runBlocking {
    val mavenSession = MavenSyncSession(maven.project, MavenSyncSpec.incremental("test"), MavenProjectsTree(maven.project))
    val toolchainsDir = createTempDirectory("testToolchains")
    val toolchainsFile = toolchainsDir.resolve("toolchains.xml")
    val discoveredCacheFile = toolchainsDir.resolve("discovered-jdk-toolchains-cache.xml")
    val sdkFile = createTempDirectory("testSdk")
    var jdk: Sdk? = null
    mavenSession.syncContext.putUserData(MavenSyncSession.TOOLCHAINS_FILE, toolchainsFile)
    write(discoveredCacheFile, """<?xml version="1.0" encoding="UTF-8"?>
      <toolchains>
        <toolchain>
          <type>jdk</type>
          <provides>
              <version>17</version>
          </provides>
          <configuration>
              <jdkHome>$sdkFile</jdkHome>
          </configuration>
        </toolchain>
      </toolchains>""")

    val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("version", "17")
      .discoverJdks(true)
      .build()

    try {
      jdk = ToolchainResolverSession.forSession(mavenSession).findOrInstallJdk(requirement)
      assertNotNull(jdk)
    }
    finally {
      if (jdk != null) {
        edtWriteAction {
          SdkConfigurationUtil.removeSdk(jdk)
        }
      }
    }
  }

  @Test
  fun testShouldFindRegisteredSdkWhenToolchainsFileIsAbsent() = runBlocking {
    val mavenSession = MavenSyncSession(maven.project, MavenSyncSpec.incremental("test"), MavenProjectsTree(maven.project))
    val toolchainsFile = createTempDirectory("testToolchains").resolve("toolchains.xml")
    val sdk = createTestSdk("test-jdk17", "17", createTempDirectory("testSdk").toString())
    mavenSession.syncContext.putUserData(MavenSyncSession.TOOLCHAINS_FILE, toolchainsFile)
    edtWriteAction {
      ProjectJdkTable.getInstance(maven.project).addJdk(sdk)
    }

    try {
      val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
        .set("version", "17")
        .discoverJdks(true)
        .build()

      val jdk = ToolchainResolverSession.forSession(mavenSession).findOrInstallJdk(requirement)
      assertSame(sdk, jdk)
    }
    finally {
      edtWriteAction {
        ProjectJdkTable.getInstance(maven.project).removeJdk(sdk)
      }
    }
  }

  @Test
  fun testShouldUseImporterJdkForSelectJdkToolchainWhenItMatches() = runBlocking {
    val mavenSession = MavenSyncSession(maven.project, MavenSyncSpec.incremental("test"), MavenProjectsTree(maven.project))
    val toolchainsFile = createTempDirectory("testToolchains").resolve("toolchains.xml")
    mavenSession.syncContext.putUserData(MavenSyncSession.TOOLCHAINS_FILE, toolchainsFile)
    val settings = MavenWorkspaceSettingsComponent.getInstance(maven.project).settings
    val originalJdkForImporter = settings.importingSettings.jdkForImporter
    val originalProjectSdk = ProjectRootManager.getInstance(maven.project).projectSdk
    val sdk = createTestSdk("test-importer-jdk17", "17", createTempDirectory("testSdk").toString())
    edtWriteAction {
      ProjectJdkTable.getInstance(maven.project).addJdk(sdk)
      ProjectRootManager.getInstance(maven.project).projectSdk = sdk
    }
    settings.importingSettings.jdkForImporter = MavenRunnerSettings.USE_PROJECT_JDK

    try {
      val version = JavaVersion.parse(sdk.versionString ?: error("The test SDK has no version")).toFeatureString()
      val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
        .set("version", version)
        .useImporterJdkIfMatches(true)
        .discoverJdks(true)
        .build()

      val jdk = ToolchainResolverSession.forSession(mavenSession).findOrInstallJdk(requirement)
      assertSame(sdk, jdk)
    }
    finally {
      settings.importingSettings.jdkForImporter = originalJdkForImporter
      edtWriteAction {
        ProjectRootManager.getInstance(maven.project).projectSdk = originalProjectSdk
        ProjectJdkTable.getInstance(maven.project).removeJdk(sdk)
      }
    }
  }


  private fun write(file: Path, data: String) {
    Files.write(file, data.toByteArray())
  }

  private suspend fun createTestSdk(name: String, version: String, homePath: String): Sdk {
    val sdk = ProjectJdkTable.getInstance(maven.project).createSdk(name, JavaSdk.getInstance())
    val sdkModificator = sdk.sdkModificator
    sdkModificator.homePath = homePath
    sdkModificator.versionString = version
    edtWriteAction { sdkModificator.commitChanges() }
    return sdk
  }
}
