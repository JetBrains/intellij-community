// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ty

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.junit5.framework.pyMockInterpreterFixture
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.ConfigurationItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText

/** The environment of beta. Its name is not ASCII, so its `uri` must be escaped. */
private const val BETA_ENV = "环境"

/**
 * One ty server answers for every Python project, so each workspace folder must get the interpreter of its own
 * Python project. Without one, ty looks for an environment itself, and a folder can get the packages of another
 * Python project or none. `TyMultiModuleLspEnvTest` checks the result with a real server.
 */
@TestApplication
@TestFor(classes = [TyLspClientDescriptor::class], issues = ["PY-86395"])
internal class TyWorkspaceConfigurationTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val alphaPath = tempPathFixture(prefix = "alpha")
  private val betaPath = tempPathFixture(prefix = "beta")
  private val envsPath = tempPathFixture(prefix = "envs")
  private val alpha = projectFixture.pyModuleFixture(alphaPath, addPathToSourceRoot = true).pyProjectFixture()
  private val beta = projectFixture.pyModuleFixture(betaPath, addPathToSourceRoot = true).pyProjectFixture()
  private val alphaInterpreter = projectFixture.pyMockInterpreterFixture(alpha) {
    mockVenvAt(interpreterPath("alpha"))
  }
  private val betaInterpreter = projectFixture.pyMockInterpreterFixture(beta) {
    mockVenvAt(interpreterPath(BETA_ENV))
  }

  @Test
  fun `each folder gets the interpreter of its own Python project`(): Unit = timeoutRunBlocking {
    val descriptor = startedDescriptor()

    assertEquals(prefixPath("alpha"), sysPrefixIn(descriptor.getWorkspaceConfiguration(itemFor(descriptor, alpha.get()))))
    assertEquals(prefixPath(BETA_ENV), sysPrefixIn(descriptor.getWorkspaceConfiguration(itemFor(descriptor, beta.get()))))
  }

  @Test
  fun `the reply keeps the client options next to the interpreter`(): Unit = timeoutRunBlocking {
    val descriptor = startedDescriptor()

    val reply = descriptor.getWorkspaceConfiguration(itemFor(descriptor, beta.get()))
    assertNotNull(reply?.get("configuration"), "the source roots must still reach ty, got $reply")
  }

  /** ty 0.0.36 and earlier drop every setting of a folder whose interpreter has no valid `uri`. */
  @Test
  fun `the uri of the interpreter is an escaped file uri`(): Unit = timeoutRunBlocking {
    val descriptor = startedDescriptor()

    val uri = executableOf(descriptor.getWorkspaceConfiguration(itemFor(descriptor, beta.get())))["uri"] as String
    assertTrue(uri.startsWith("file:") && uri.endsWith("/%E7%8E%AF%E5%A2%83/bin/python")) { "not an escaped file URI: $uri" }
  }

  /** ty then finds an environment itself, instead of reporting an interpreter that does not exist. */
  @Test
  fun `an interpreter that the VFS does not find is left out`(): Unit = timeoutRunBlocking {
    val descriptor = startedDescriptor()
    withContext(Dispatchers.IO) {
      interpreterPath(BETA_ENV).deleteExisting()
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(interpreterPath(BETA_ENV).parent)?.refresh(false, false)
    }

    assertNull(descriptor.getWorkspaceConfiguration(itemFor(descriptor, beta.get()))?.get("pythonExtension"))
  }

  @Test
  fun `an item with no scope gets the interpreter of the primary Python project`(): Unit = timeoutRunBlocking {
    val descriptor = startedDescriptor()

    val item = ConfigurationItem().also { it.section = "ty" }
    assertEquals(prefixPath("alpha"), sysPrefixIn(descriptor.getWorkspaceConfiguration(item)))
  }

  /** ty merges the initialization options into the settings of each folder, and it panics on two interpreters. */
  @Test
  fun `the initialization options carry no interpreter`(): Unit = timeoutRunBlocking {
    val descriptor = startedDescriptor()

    val options = withContext(Dispatchers.IO) { descriptor.createInitializationOptions() }
    assertFalse(options.orEmpty().containsKey("pythonExtension"))
  }

  @Test
  fun `a section other than ty gets no reply`(): Unit = timeoutRunBlocking {
    val descriptor = startedDescriptor()

    val item = itemFor(descriptor, beta.get()).also { it.section = "python" }
    assertNull(descriptor.getWorkspaceConfiguration(item))
  }

  @Test
  fun `the prefix of a POSIX interpreter is the parent of bin`() {
    assertEquals(prefixPath("posix"), tySysPrefixOf(executableFile("posix", "bin", "python"))?.path)
  }

  @Test
  fun `the prefix of a Windows virtual environment is the parent of Scripts`() {
    assertEquals(prefixPath("winvenv"), tySysPrefixOf(executableFile("winvenv", "Scripts", "python.exe"))?.path)
  }

  @Test
  fun `the prefix of a Windows base installation is the directory of the interpreter`() {
    assertEquals(prefixPath("Python313"), tySysPrefixOf(executableFile("Python313", "python.exe"))?.path)
  }

  /**
   * A descriptor of both Python projects after the server start and the initialization options, which is when the
   * platform asks for a reply. The start records the interpreters that the replies carry.
   */
  private suspend fun startedDescriptor(): TyLspClientDescriptor {
    alphaInterpreter.get()
    betaInterpreter.get()
    val descriptor = TyLspClientDescriptor(alpha.get(), listOf(alpha.get(), beta.get()))
    // The platform builds the command line and the options on a pooled thread, which holds no lock.
    withContext(Dispatchers.IO) {
      descriptor.createCommandLine()
      descriptor.createInitializationOptions()
    }
    return descriptor
  }

  private fun itemFor(descriptor: TyLspClientDescriptor, pyProject: PyProject): ConfigurationItem {
    val root = ModuleRootManager.getInstance(pyProject.residesOnModule).contentRoots.single()
    return ConfigurationItem().also {
      it.scopeUri = descriptor.getFileUri(root)
      it.section = "ty"
    }
  }

  /** `<envs>/<name>/bin/python`, outside the project directory. */
  private fun interpreterPath(name: String): Path = envsPath.get().resolve(name).resolve("bin").resolve("python")

  /** `<envs>/<names>`, created on disk and in the VFS. */
  private fun executableFile(vararg names: String): VirtualFile {
    val path = names.fold(envsPath.get(), Path::resolve)
    createExecutable(path)
    return checkNotNull(VirtualFileManager.getInstance().findFileByNioPath(path)) { "$path is not in the VFS" }
  }

  /** The path of the prefix [name] as the descriptor names it, with `/` on every OS. */
  private fun prefixPath(name: String): String = envsPath.get().resolve(name).invariantSeparatorsPathString

  private fun executableOf(reply: Map<String, Any>?): Map<*, *> {
    val pythonExtension = checkNotNull(reply?.get("pythonExtension") as? Map<*, *>) { "no pythonExtension in $reply" }
    val activeEnvironment = checkNotNull(pythonExtension["activeEnvironment"] as? Map<*, *>) { "no activeEnvironment in $reply" }
    return checkNotNull(activeEnvironment["executable"] as? Map<*, *>) { "no executable in $reply" }
  }

  private fun sysPrefixIn(reply: Map<String, Any>?): Any? = executableOf(reply)["sysPrefix"]
}

/**
 * A mock SDK at [executable], with a `ty` beside it, so the server start finds the binary. The `pyvenv.cfg` makes it a
 * virtual environment, so its detected root is the prefix. A bare `bin/python` is a system Python, which is read-only
 * and so has no ty.
 */
private fun mockVenvAt(executable: Path): Sdk {
  createExecutable(executable)
  executable.parent.parent.resolve("pyvenv.cfg").writeText("home = ${executable.parent}\n")
  createExecutable(executable.resolveSibling(if (SystemInfoRt.isWindows) "ty.exe" else "ty"))
  val sdk = PythonMockSdk.create()
  val modificator = sdk.sdkModificator
  modificator.homePath = executable.toString()
  ApplicationManager.getApplication().invokeAndWait { runWriteAction { modificator.commitChanges() } }
  return sdk
}

private fun createExecutable(path: Path) {
  path.parent.createDirectories()
  path.writeText("")
  if (!SystemInfoRt.isWindows) {
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
  }
  VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
}
