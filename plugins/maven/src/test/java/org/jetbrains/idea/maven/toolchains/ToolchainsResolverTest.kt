// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import com.intellij.maven.testFramework.fixtures.mavenFixture
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


  private fun write(file: Path, data: String) {
    Files.write(file, data.toByteArray())
  }
}
