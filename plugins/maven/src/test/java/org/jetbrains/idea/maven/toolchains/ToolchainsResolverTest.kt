// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains

import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsTree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile

internal class ToolchainsResolverTest : MavenTestCase() {

  override fun runInDispatchThread() = false

  fun testShouldReturnSameResolutionSession() {
    val mavenSession = MavenSyncSession(project, MavenSyncSpec.incremental("test"), MavenProjectsTree(project))

    val first = ToolchainResolverSession.forSession(mavenSession)
    val second = ToolchainResolverSession.forSession(mavenSession)
    assertSame(first, second)
  }


  fun testShouldInstallSdkInIdea() = runBlocking {
    val mavenSession = MavenSyncSession(project, MavenSyncSpec.incremental("test"), MavenProjectsTree(project))
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
        writeAction {
          SdkConfigurationUtil.removeSdk(jdk)
        }
      }
    }
  }


  private fun write(file: Path, data: String) {
    Files.write(file, data.toByteArray())
  }
}
