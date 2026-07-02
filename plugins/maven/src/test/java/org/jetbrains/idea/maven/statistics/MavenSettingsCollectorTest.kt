// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.FUCollectorTestCase.collectProjectStateCollectorEvents
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenExecutionOptions
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class MavenSettingsCollectorTest {
  private val maven by mavenImportingFixture()

  @Test
  fun `test should collect maven settings`() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
    """.trimIndent())

    maven.projectsManager.generalSettings.apply {
      checksumPolicy = MavenExecutionOptions.ChecksumPolicy.FAIL
      failureBehavior = MavenExecutionOptions.FailureMode.FAST
      outputLevel = MavenExecutionOptions.LoggingLevel.DEBUG
      isAlwaysUpdateSnapshots = true
      isWorkOffline = true
      setLocalRepository("/tmp/maven-repository")
      setUserSettingsFile("/tmp/settings.xml")
    }
    maven.projectsManager.importingSettings.apply {
      isLookForNested = true
      isDownloadDocsAutomatically = true
      isDownloadSourcesAutomatically = false
      isUseMavenOutput = false
      generatedSourcesFolder = GeneratedSourcesFolder.SUBFOLDER
      updateFoldersOnImportPhase = "generate-sources"
    }
    MavenRunner.getInstance(maven.project).settings.apply {
      isDelegateBuildToMaven = true
      isSkipTests = true
      setEnvironmentProperties(mapOf("MAVEN_OPTS" to "-Xmx512m"))
      setMavenProperties(mapOf("skipITs" to "true"))
    }

    val metrics = collectProjectStateCollectorEvents(MavenSettingsCollector::class.java, maven.project)

    assertEquals(mapOf("enabled" to true), metrics.data("hasMavenProject"))
    assertEquals(mapOf("value" to "fail"), metrics.data("checksumPolicy"))
    assertEquals(mapOf("value" to "fast"), metrics.data("failureBehavior"))
    assertEquals(mapOf("value" to "debug"), metrics.data("outputLevel"))
    assertEquals(mapOf("value" to "debug"), metrics.data("loggingLevel"))
    assertEquals(mapOf("enabled" to true), metrics.data("alwaysUpdateSnapshots"))
    assertEquals(mapOf("enabled" to true), metrics.data("workOffline"))
    assertEquals(mapOf("enabled" to true), metrics.data("localRepository"))
    assertEquals(mapOf("enabled" to true), metrics.data("userSettingsFile"))
    assertEquals(mapOf("enabled" to true), metrics.data("lookForNested"))
    assertEquals(mapOf("enabled" to true), metrics.data("downloadDocsAutomatically"))
    assertEquals(mapOf("enabled" to false), metrics.data("downloadSourcesAutomatically"))
    assertEquals(mapOf("enabled" to false), metrics.data("useMavenOutput"))
    assertEquals(mapOf("value" to "subfolder"), metrics.data("generatedSourcesFolder"))
    assertEquals(mapOf("value" to "generate-sources"), metrics.data("updateFoldersOnImportPhase"))
    assertEquals(mapOf("enabled" to true), metrics.data("delegateBuildRun"))
    assertEquals(mapOf("enabled" to true), metrics.data("skipTests"))
    assertEquals(mapOf("enabled" to false), metrics.data("hasRunnerVmOptions"))
    assertEquals(mapOf("enabled" to true), metrics.data("hasRunnerEnvVariables"))
    assertEquals(mapOf("enabled" to true), metrics.data("hasRunnerMavenProperties"))
  }

  @Test
  fun `test should collect toolchain settings`() = runBlocking {
    val toolchainsFile = maven.createProjectSubFile("toolchains.xml", """
      <toolchains>
        <toolchain>
          <type>jdk</type>
          <provides>
            <version>17</version>
          </provides>
          <configuration>
            <jdkHome>/tmp/jdk</jdkHome>
          </configuration>
        </toolchain>
      </toolchains>
    """.trimIndent())
    maven.createProjectSubFile(".mvn/maven.config", """
      --toolchains
      ${toolchainsFile.path}
    """.trimIndent())

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-toolchains-plugin</artifactId>
            <executions>
              <execution>
                <goals>
                  <goal>toolchain</goal>
                </goals>
              </execution>
            </executions>
            <configuration>
              <toolchains>
                <jdk>
                  <version>17</version>
                </jdk>
              </toolchains>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())

    val metrics = collectProjectStateCollectorEvents(MavenSettingsCollector::class.java, maven.project)

    assertEquals(mapOf("enabled" to true), metrics.data("hasCustomToolchainsFile"))
    assertEquals(mapOf("enabled" to true), metrics.data("hasToolchainRequirements"))
  }

  private fun Set<MetricEvent>.data(eventId: String): Map<String, Any> = single { it.eventId == eventId }.data.build()
}
