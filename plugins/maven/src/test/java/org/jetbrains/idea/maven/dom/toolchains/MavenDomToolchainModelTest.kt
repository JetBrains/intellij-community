// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.toolchains

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.workspace.storage.InternalEnvironmentName
import com.intellij.testFramework.LightPlatform4TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.toolchains.addIntoToolchainsFile
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

class MavenDomToolchainModelTest : LightPlatform4TestCase() {

  override fun runInDispatchThread(): Boolean {
    return false
  }

  @Test
  fun testEmptyFile() = runBlocking {
    val path = createTempDirectory(getTestName(true)).resolve("toolchains.xml")
    write(path, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><toolchains></toolchains>")
    val vFile = VfsUtil.findFile(path, true)!!
    val toolchainsModel = readAction { MavenDomUtil.getMavenDomModel(project, vFile, MavenDomToolchainsModel::class.java) }
    assertNotNull("Toolchain model should not be null", toolchainsModel)
    assertEmpty(readAction { toolchainsModel!!.toolchains })

  }

  @Test
  fun testReadToolchains() = runBlocking {
    val path = createTempDirectory(getTestName(true)).resolve("toolchains.xml")
    write(path, """<?xml version="1.0" encoding="UTF-8"?>
      <toolchains>
        <toolchain>
          <type>jdk</type>
          <provides>
              <version>17</version>
              <vendor>temurin</vendor>
              <purpose>for tests</purpose>
          </provides>
          <configuration>
              <jdkHome>/path/to/jdk</jdkHome>
          </configuration>
        </toolchain>
      </toolchains>""")
    val vFile = VfsUtil.findFile(path, true)!!
    val toolchainsModel = readAction { MavenDomUtil.getMavenDomModel(project, vFile, MavenDomToolchainsModel::class.java) }
    assertNotNull("Toolchain model should not be null", toolchainsModel)
    readAction {
      assertSize(1, toolchainsModel!!.toolchains)
      val toolchain = toolchainsModel.toolchains[0]
      assertEquals("jdk", toolchain.type.value)
      assertEquals("17", toolchain.provides.version.value)
      assertEquals("/path/to/jdk", toolchain.configuration.jdkHome.stringValue)
    }
  }

  @Test
  fun testWriteToolchainsToExistingFile() = runBlocking {
    val path = createTempDirectory(getTestName(true)).resolve("toolchains.xml")
    write(path, """<?xml version="1.0" encoding="UTF-8"?>
      <toolchains>
        <toolchain>
          <type>jdk</type>
          <provides>
              <version>17</version>
              <vendor>temurin</vendor>
              <purpose>for tests</purpose>
          </provides>
          <configuration>
              <jdkHome>/path/to/jdk</jdkHome>
          </configuration>
        </toolchain>
      </toolchains>""")
    val vFile = VfsUtil.findFile(path, true)!!
    val testSdk = createTestSdk(path.parent, "12.123")
    addIntoToolchainsFile(project, path, testSdk)
    edtWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
    readAction {
      val toolchainsModel = MavenDomUtil.getMavenDomModel(project, vFile, MavenDomToolchainsModel::class.java)
      assertNotNull("Toolchain model should not be null", toolchainsModel)
      assertSize(2, toolchainsModel!!.toolchains)

      assertEquals("jdk", toolchainsModel.toolchains[1].type.value)
      assertEquals("12", toolchainsModel.toolchains[1].provides.version.value)
      assertEquals(path.parent.toString(), toolchainsModel.toolchains[1].configuration.jdkHome.stringValue)
    }
  }

  @Test
  fun testWriteToolchainsToExistingEmptyFile() = runBlocking {
    val path = createTempDirectory(getTestName(true)).resolve("toolchains.xml")
    write(path, """<?xml version="1.0" encoding="UTF-8"?>
      <toolchains>
      </toolchains>""")
    val vFile = VfsUtil.findFile(path, true)!!
    val testSdk = createTestSdk(path.parent, "12.123")
    addIntoToolchainsFile(project, path, testSdk)
    edtWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
    readAction {
      val toolchainsModel = MavenDomUtil.getMavenDomModel(project, vFile, MavenDomToolchainsModel::class.java)
      assertNotNull("Toolchain model should not be null", toolchainsModel)
      assertSize(1, toolchainsModel!!.toolchains)

      assertEquals("jdk", toolchainsModel.toolchains[0].type.value)
      assertEquals("12", toolchainsModel.toolchains[0].provides.version.value)
      assertEquals(path.parent.toString(), toolchainsModel.toolchains[0].configuration.jdkHome.stringValue)
    }
  }

  @Test
  fun testWriteToolchainsToNonExisting() = runBlocking {
    val path = createTempDirectory(getTestName(true)).resolve("toolchains.xml")
    assertFalse("File should not exists create toolchains file", path.exists())
    val testSdk = createTestSdk(path.parent, "12.123")
    addIntoToolchainsFile(project, path, testSdk)
    edtWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
    assertTrue("Should create toolchains file", path.isRegularFile())
    val vFile = VfsUtil.findFile(path, true)!!
    readAction {
      val toolchainsModel = MavenDomUtil.getMavenDomModel(project, vFile, MavenDomToolchainsModel::class.java)
      assertNotNull("Toolchain model should not be null", toolchainsModel)
      assertSize(1, toolchainsModel!!.toolchains)

      assertEquals("jdk", toolchainsModel.toolchains[0].type.value)
      assertEquals("12", toolchainsModel.toolchains[0].provides.version.value)
      assertEquals(path.parent.toString(), toolchainsModel.toolchains[0].configuration.jdkHome.stringValue)
    }
  }

  private suspend fun createTestSdk(path: Path, version: String): Sdk {
    return ProjectJdkImpl(name, JavaSdk.getInstance(), path.toString(), version, InternalEnvironmentName.Local)
  }

  private fun write(myTestFile: Path, data: String) {
    Files.write(myTestFile, data.toByteArray())
  }
}