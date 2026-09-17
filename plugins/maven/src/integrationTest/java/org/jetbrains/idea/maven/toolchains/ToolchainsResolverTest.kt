// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    val sdkFile = createFakeJdkHome()
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
    val toolchainsFile = createTempDirectory("testToolchains").resolve("toolchains.xml")
    // The cache lives in the .m2 directory, not next to the toolchains file.
    val discoveredCacheFile = createTempDirectory("testM2").resolve("discovered-jdk-toolchains-cache.xml")
    val sdkFile = createFakeJdkHome()
    var jdk: Sdk? = null
    mavenSession.syncContext.putUserData(MavenSyncSession.TOOLCHAINS_FILE, toolchainsFile)
    mavenSession.syncContext.putUserData(MavenSyncSession.DISCOVERED_JDK_CACHE_FILE, discoveredCacheFile)
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
    putEmptyDiscoveryCache(mavenSession)
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
    putEmptyDiscoveryCache(mavenSession)
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


  @Test
  fun testShouldKeepFullVersionOfRegisteredSdk() = runBlocking {
    val mavenSession = MavenSyncSession(maven.project, MavenSyncSpec.incremental("test"), MavenProjectsTree(maven.project))
    val toolchainsFile = createTempDirectory("testToolchains").resolve("toolchains.xml")
    val sdk = createTestSdk("test-jdk47", "47.0.1", createTempDirectory("testSdk").toString())
    mavenSession.syncContext.putUserData(MavenSyncSession.TOOLCHAINS_FILE, toolchainsFile)
    putEmptyDiscoveryCache(mavenSession)
    edtWriteAction {
      ProjectJdkTable.getInstance(maven.project).addJdk(sdk)
    }

    try {
      val session = ToolchainResolverSession.forSession(mavenSession)
      val rangeRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
        .set("version", "[47.0.1,48)")
        .discoverJdks(true)
        .build()
      assertSame(sdk, session.findOrInstallJdk(rangeRequirement))

      val bareRequirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
        .set("version", "47")
        .discoverJdks(true)
        .build()
      assertNull(session.findOrInstallJdk(bareRequirement), "A bare version must match only an exact equal version")
    }
    finally {
      edtWriteAction {
        ProjectJdkTable.getInstance(maven.project).removeJdk(sdk)
      }
    }
  }

  @Test
  fun testShouldSkipStaleDiscoveryCacheEntry() = runBlocking {
    val mavenSession = MavenSyncSession(maven.project, MavenSyncSpec.incremental("test"), MavenProjectsTree(maven.project))
    val toolchainsDir = createTempDirectory("testToolchains")
    val toolchainsFile = toolchainsDir.resolve("toolchains.xml")
    val discoveredCacheFile = createTempDirectory("testM2").resolve("discovered-jdk-toolchains-cache.xml")
    val staleHome = toolchainsDir.resolve("removed-jdk")
    mavenSession.syncContext.putUserData(MavenSyncSession.TOOLCHAINS_FILE, toolchainsFile)
    mavenSession.syncContext.putUserData(MavenSyncSession.DISCOVERED_JDK_CACHE_FILE, discoveredCacheFile)
    write(discoveredCacheFile, """<?xml version="1.0" encoding="UTF-8"?>
      <toolchains>
        <toolchain>
          <type>jdk</type>
          <provides>
              <version>17</version>
          </provides>
          <configuration>
              <jdkHome>$staleHome</jdkHome>
          </configuration>
        </toolchain>
      </toolchains>""")
    val sdk = createTestSdk("test-jdk17-fallback", "17", createTempDirectory("testSdk").toString())
    edtWriteAction {
      ProjectJdkTable.getInstance(maven.project).addJdk(sdk)
    }

    try {
      val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
        .set("version", "17")
        .discoverJdks(true)
        .build()

      val jdk = ToolchainResolverSession.forSession(mavenSession).findOrInstallJdk(requirement)
      assertSame(sdk, jdk, "A stale cache entry must not hide a valid registered SDK")
    }
    finally {
      edtWriteAction {
        ProjectJdkTable.getInstance(maven.project).removeJdk(sdk)
      }
    }
  }

  @Test
  fun testShouldBuildJdkModelsFromEnvironmentVariables() {
    val jdkHome = createTempDirectory("testEnvJdk").toRealPath()
    val otherDir = createTempDirectory("testEnvOther").toRealPath()
    val env = mapOf(
      "JAVA47_HOME" to jdkHome.toString(),
      "JAVA_HOME" to jdkHome.toString(),
      "JAVA_BROKEN_HOME" to otherDir.toString(),
      "JAVA_MISSING_HOME" to otherDir.resolve("missing").toString(),
      "MAVEN_HOME" to jdkHome.toString(),
      "PATH" to "irrelevant",
    )
    val versionInfo = JdkVersionDetector.JdkVersionInfo(JavaVersion.compose(47, 0, 1, 0, false), JdkVersionDetector.Variant.Temurin,
                                                        CpuArch.X86_64)

    val models = jdkModelsFromEnvironment(env) { if (it == jdkHome) versionInfo else null }

    assertEquals(1, models.size)
    val model = models.single()
    assertEquals("47.0.1", model.provides["version"])
    assertEquals("Eclipse Temurin", model.provides["vendor"])
    assertEquals("JAVA47_HOME,JAVA_HOME", model.provides["env"])
    assertEquals(jdkHome.toString(), model.jdkHome)
  }

  @Test
  fun testShouldDiscoverJdkFromEnvironmentVariable() = runBlocking {
    val mavenSession = MavenSyncSession(maven.project, MavenSyncSpec.incremental("test"), MavenProjectsTree(maven.project))
    val toolchainsFile = createTempDirectory("testToolchains").resolve("toolchains.xml")
    mavenSession.syncContext.putUserData(MavenSyncSession.TOOLCHAINS_FILE, toolchainsFile)
    putEmptyDiscoveryCache(mavenSession)
    val session = ToolchainResolverSession.forSession(mavenSession)
    session.environment = { mapOf("JAVA_TEST_HOME" to System.getProperty("java.home")) }

    val requirement = ToolchainRequirement.Builder(ToolchainRequirement.JDK_TYPE)
      .set("env", "JAVA_TEST_HOME")
      .discoverJdks(true)
      .build()

    val registeredBefore = ProjectJdkTable.getInstance(maven.project).allJdks.toSet()
    var jdk: Sdk? = null
    try {
      jdk = session.findOrInstallJdk(requirement)
      assertNotNull(jdk, "The resolver must discover the JDK behind the JAVA_TEST_HOME variable")
    }
    finally {
      if (jdk != null && jdk !in registeredBefore) {
        edtWriteAction {
          SdkConfigurationUtil.removeSdk(jdk)
        }
      }
    }
  }

  private fun write(file: Path, data: String) {
    Files.write(file, data.toByteArray())
  }

  private fun putEmptyDiscoveryCache(mavenSession: MavenSyncSession) {
    val cacheFile = createTempDirectory("testM2").resolve("discovered-jdk-toolchains-cache.xml")
    mavenSession.syncContext.putUserData(MavenSyncSession.DISCOVERED_JDK_CACHE_FILE, cacheFile)
  }

  /** Creates a directory layout that [com.intellij.openapi.projectRoots.JdkUtil.checkForJdk] accepts. */
  private fun createFakeJdkHome(): Path {
    val home = createTempDirectory("testFakeJdk")
    Files.createDirectories(home.resolve("bin"))
    Files.createDirectories(home.resolve("lib"))
    Files.createFile(home.resolve("bin/javac"))
    Files.createFile(home.resolve("bin/javac.exe"))
    Files.createFile(home.resolve("lib/jrt-fs.jar"))
    return home.toRealPath()
  }

  private suspend fun createTestSdk(name: String, version: String, homePath: String): Sdk {
    val sdk = ProjectJdkTable.getInstance(maven.project).createSdk(name, JavaSdk.getInstance())
    val sdkModificator = sdk.sdkModificator
    // The VFS stores canonical paths. A short 8.3 temp path breaks the home path comparison.
    sdkModificator.homePath = Path.of(homePath).toRealPath().toString()
    sdkModificator.versionString = version
    edtWriteAction { sdkModificator.commitChanges() }
    return sdk
  }
}
