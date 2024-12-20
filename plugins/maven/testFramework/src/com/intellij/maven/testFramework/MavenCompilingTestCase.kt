// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.compiler.artifacts.ArtifactsTestUtil
import com.intellij.compiler.impl.ModuleCompileScope
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.CompileScope
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.impl.compiler.ArtifactCompileScope
import com.intellij.testFramework.CompilerTester
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.TestFileSystemBuilder
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.readText

/**
 * This test case uses the NIO API for handling file operations.
 *
 * **Background**:
 * The test framework is transitioning from the `IO` API to the`NIO` API
 *
 * **Implementation Notes**:
 * - `<TestCase>` represents the updated implementation using the `NIO` API.
 * - `<TestCaseLegacy>` represents the legacy implementation using the `IO` API.
 * - For now, both implementations coexist to allow for a smooth transition and backward compatibility.
 * - Eventually, `<TestCaseLegacy>` will be removed from the codebase.
 *
 * **Action Items**:
 * - Prefer using `<TestCase>` for new test cases.
 * - Update existing tests to use `<TestCase>` where possible.
 *
 * **Future Direction**:
 * Once the transition is complete, all test cases relying on the `IO` API will be retired,
 * and the codebase will exclusively use the `NIO` implementation.
 */
abstract class MavenCompilingTestCase : MavenMultiVersionImportingTestCase() {

  protected suspend fun compileModules(vararg moduleNames: String) {
    // blockingContextScope here because we want to propagate cancellation to invokeAndWait
    blockingContextScope {
      compile(createModulesCompileScope(*moduleNames))
    }
  }

  @Throws(Exception::class)
  protected suspend fun rebuildProject() {
    val tester = CompilerTester(project, listOf(), null)
    try {
      blockingContextScope {
        tester.rebuild()
      }
    }
    finally {
      tester.tearDown()
    }
  }

  @Throws(Exception::class)
  protected suspend fun compileFile(moduleName: String, file: VirtualFile) {
    val tester = CompilerTester(project, listOf(getModule(moduleName)), null)
    try {
      blockingContextScope {
        tester.compileFiles(file)
      }
    }
    finally {
      tester.tearDown()
    }
  }

  protected fun buildArtifacts(vararg artifactNames: String) {
    compile(createArtifactsScope(*artifactNames))
  }

  private fun compile(scope: CompileScope) {
    try {
      val tester = CompilerTester(project, listOf(*scope.affectedModules), null)
      try {
        val messages = tester.make(scope)
        for (message in messages) {
          if (message.category === CompilerMessageCategory.ERROR) {
            fail("Compilation failed with error: " + message.message)
          }
        }
      }
      finally {
        tester.tearDown()
      }
    }
    catch (e: Exception) {
      ExceptionUtil.rethrow(e)
    }
  }

  private fun createArtifactsScope(vararg artifactNames: String): CompileScope {
    val artifacts: MutableList<Artifact> = ArrayList()
    for (name in artifactNames) {
      artifacts.add(ArtifactsTestUtil.findArtifact(project, name))
    }
    return ReadAction.nonBlocking<CompileScope> { ArtifactCompileScope.createArtifactsScope(project, artifacts) }.executeSynchronously()
  }

  private fun createModulesCompileScope(vararg moduleNames: String): CompileScope {
    val modules: MutableList<Module> = ArrayList()
    for (name in moduleNames) {
      modules.add(getModule(name))
    }
    return ModuleCompileScope(project, modules.toTypedArray(), false)
  }

  @Throws(IOException::class)
  protected fun assertResult(relativePath: String, content: String?) {
    assertResult(projectPom, relativePath, content)
  }

  protected fun assertDirectory(relativePath: String, fileSystemBuilder: TestFileSystemBuilder) {
    val directory = projectPom.parent.toNioPath().resolve(relativePath)
    fileSystemBuilder.build().assertDirectoryEqual(directory.toFile())
  }

  protected fun assertJar(relativePath: String, fileSystemBuilder: TestFileSystemBuilder) {
    val jar = projectPom.parent.toNioPath().resolve(relativePath)
    fileSystemBuilder.build().assertFileEqual(jar.toFile())
  }

  protected fun assertCopied(path: String) {
    val parent = projectPom.parent.toNioPath()
    assertTrue(parent.resolve(path).exists())
  }

  protected fun assertExists(path: String) {
    assertCopied(path)
  }

  protected fun assertDoesNotExist(path: String) {
    assertNotCopied(path)
  }

  protected fun assertExists(path: Path) {
    assertTrue("File should exist $path", path.exists());
  }

  @Throws(IOException::class)
  protected fun assertCopied(path: String, content: String?) {
    val parent = projectPom.parent.toNioPath()
    val file = parent.resolve(path)
    assertTrue(file.exists())
    assertEquals(content, file.readText())
  }

  protected fun assertNotCopied(path: String) {
    val parent = projectPom.parent.toNioPath()
    val file = parent.resolve(path)
    assertTrue(file.notExists())
  }

  @Throws(IOException::class)
  protected fun assertResult(pomFile: VirtualFile, relativePath: String, content: String?) {
    assertEquals(content, loadResult(pomFile, relativePath))
  }

  @Throws(IOException::class)
  protected fun loadResult(pomFile: VirtualFile, relativePath: String): String {
    val parent = pomFile.parent.toNioPath()
    val file = parent.resolve(relativePath)
    assertTrue("file not found: $relativePath", file.exists())
    return file.readText()
  }

  protected fun extractJdkVersion(module: Module, fallbackToInternal: Boolean): String? {
    var jdkVersion: String? = null
    val sdk = Optional.ofNullable(ModuleRootManager.getInstance(module).sdk)

    if (sdk.isEmpty) {
      val jdkEntry =
        Arrays.stream(ModuleRootManager.getInstance(module).orderEntries)
          .filter { obj: OrderEntry? -> JdkOrderEntry::class.java.isInstance(obj) }
          .map { obj: OrderEntry? -> JdkOrderEntry::class.java.cast(obj) }
          .findFirst()
      if (jdkEntry.isPresent) {
        jdkVersion = jdkEntry.get().jdkName
      }
    }
    else {
      jdkVersion = sdk.get().versionString
    }

    if (jdkVersion == null && fallbackToInternal) {
      jdkVersion = JavaAwareProjectJdkTableImpl.getInstanceEx().internalJdk.versionString
    }

    if (jdkVersion != null) {
      val quoteIndex = jdkVersion.indexOf('"')
      if (quoteIndex != -1) {
        jdkVersion = jdkVersion.substring(quoteIndex + 1, jdkVersion.length - 1)
      }
    }

    return jdkVersion
  }

}
