// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.compiler.artifacts.ArtifactsTestUtil
import com.intellij.compiler.impl.ModuleCompileScope
import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.getModule
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.IOException
import java.nio.file.Path
import java.util.Arrays
import java.util.Optional
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.readText

// Ported from com.intellij.maven.testFramework.MavenCompilingTestCase for fixture-based JUnit 5 tests.

val MAVEN_COMPILER_PROPERTIES: String = """
    <properties>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <maven.compiler.source>11</maven.compiler.source>
            <maven.compiler.target>11</maven.compiler.target>
    </properties>

    """.trimIndent()

suspend fun MavenImportingTestFixture.compileModules(vararg moduleNames: String) {
  // blockingContextScope here because we want to propagate cancellation to invokeAndWait
  blockingContextScope {
    compile(createModulesCompileScope(*moduleNames))
  }
}

@Throws(Exception::class)
suspend fun MavenImportingTestFixture.rebuildProject() {
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
suspend fun MavenImportingTestFixture.compileFile(moduleName: String, file: VirtualFile) {
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

fun MavenImportingTestFixture.buildArtifacts(vararg artifactNames: String) {
  compile(createArtifactsScope(*artifactNames))
}

private fun MavenImportingTestFixture.compile(scope: CompileScope) {
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

private fun MavenImportingTestFixture.createArtifactsScope(vararg artifactNames: String): CompileScope {
  val artifacts: MutableList<Artifact> = ArrayList()
  for (name in artifactNames) {
    artifacts.add(ArtifactsTestUtil.findArtifact(project, name))
  }
  return ReadAction.nonBlocking<CompileScope> { ArtifactCompileScope.createArtifactsScope(project, artifacts) }.executeSynchronously()
}

private fun MavenImportingTestFixture.createModulesCompileScope(vararg moduleNames: String): CompileScope {
  val modules: MutableList<Module> = ArrayList()
  for (name in moduleNames) {
    modules.add(getModule(name))
  }
  return ModuleCompileScope(project, modules.toTypedArray(), false)
}

@Throws(IOException::class)
fun MavenImportingTestFixture.assertResult(relativePath: String, content: String?) {
  assertResult(projectPom, relativePath, content)
}

fun MavenImportingTestFixture.assertDirectory(relativePath: String, fileSystemBuilder: TestFileSystemBuilder) {
  val directory = projectPom.parent.toNioPath().resolve(relativePath)
  fileSystemBuilder.build().assertDirectoryEqual(directory.toFile())
}

fun MavenImportingTestFixture.assertJar(relativePath: String, fileSystemBuilder: TestFileSystemBuilder) {
  val jar = projectPom.parent.toNioPath().resolve(relativePath)
  fileSystemBuilder.build().assertFileEqual(jar.toFile())
}

fun MavenImportingTestFixture.assertCopied(path: String) {
  val parent = projectPom.parent.toNioPath()
  val resolvedPath = parent.resolve(path)
  assertTrue("File $resolvedPath doesn't exist", resolvedPath.exists())
}

fun MavenImportingTestFixture.assertExists(path: String) {
  assertCopied(path)
}

fun MavenImportingTestFixture.assertDoesNotExist(path: String) {
  assertNotCopied(path)
}

fun MavenImportingTestFixture.assertExists(path: Path) {
  assertTrue("File should exist $path", path.exists())
}

@Throws(IOException::class)
fun MavenImportingTestFixture.assertCopied(path: String, content: String?) {
  val parent = projectPom.parent.toNioPath()
  val file = parent.resolve(path)
  assertTrue(file.exists())
  assertEquals(content, file.readText())
}

fun MavenImportingTestFixture.assertNotCopied(path: String) {
  val parent = projectPom.parent.toNioPath()
  val file = parent.resolve(path)
  assertTrue(file.notExists())
}

@Throws(IOException::class)
fun MavenImportingTestFixture.assertResult(pomFile: VirtualFile, relativePath: String, content: String?) {
  assertEquals(content, loadResult(pomFile, relativePath))
}

@Throws(IOException::class)
fun MavenImportingTestFixture.loadResult(pomFile: VirtualFile, relativePath: String): String {
  val parent = pomFile.parent.toNioPath()
  val file = parent.resolve(relativePath)
  assertTrue("file not found: $relativePath", file.exists())
  return file.readText()
}

fun MavenImportingTestFixture.extractJdkVersion(module: Module, fallbackToInternal: Boolean): String? {
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
