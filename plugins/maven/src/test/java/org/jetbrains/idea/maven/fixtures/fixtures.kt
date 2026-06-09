// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.fixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.model.MavenConstants
import java.nio.file.Path

/**
 * Creates a [MavenDomTestFixture] that hosts a `CodeInsightTestFixture` over a real Maven project and,
 * when [withIndices] is `true`, sets up the `local1`/`local2` test repository plus GAV indices.
 *
 * Use it with the property-delegate pattern in a `@TestApplication`-annotated test class:
 * ```
 * private val maven by mavenDomFixture(withIndices = true)
 * ```
 *
 * @param withIndices when `true`, a [MavenIndicesTestFixture] copies the test
 *   repository and points Maven's local repo at it; when `false`, Maven is pointed at the JetBrains cache redirector.
 * @param initialPom the pom imported during fixture set-up; pass `null` to skip the initial import.
 */
fun mavenDomFixture(
  mavenVersion: String = "bundled",
  modelVersion: String = MavenConstants.MODEL_VERSION_4_0_0,
  skipPluginResolution: Boolean = true,
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") initialPom: String? = MavenDomTestFixture.DEFAULT_POM,
  withIndices: Boolean = false,
  localRepoDir: String = "local1",
  extraRepoDirs: List<String> = listOf("local2"),
): TestFixture<MavenDomTestFixture> {
  val dirFixture = tempPathFixture()
  // The project must be opened so that the hosted CodeInsightTestFixture can commit documents and run
  // completion / highlighting against it.
  // Put the project base in its own per-run sub-directory so the directory ABOVE the project pom is a fresh, per-run
  // temp dir rather than the shared system temp root. Some tests legitimately write a pom OUTSIDE the project (e.g.
  // `createPomFile(projectRoot.parent, ...)` to test parent resolution by relativePath); without this, that write
  // lands in the shared temp root and leaks across runs (poisoning Maven 4's default `../pom.xml` parent inheritance).
  val projectFixture = projectFixture(pathFixture = tempPathFixture(subdirName = "project"), openAfterCreation = true)
  val codeInsightFixture = mavenCodeInsightFixture(projectFixture, tempPathFixture())
  return testFixture {
    val dir = dirFixture.init()
    val project = projectFixture.init()
    val fixture = MavenDomTestFixture(project,
                                      dir,
                                      mavenVersion,
                                      modelVersion,
                                      skipPluginResolution,
                                      withIndices,
                                      localRepoDir,
                                      extraRepoDirs)
    try {
      fixture.attachCodeInsight(codeInsightFixture.init())
      fixture.setUp(initialPom)
    }
    catch (e: Throwable) {
      try {
        fixture.tearDown()
      }
      catch (t: Throwable) {
        e.addSuppressed(t)
      }
      throw e
    }
    initialized(fixture) {
      fixture.tearDown()
    }
  }
}

/**
 * A [CodeInsightTestFixture] over [projectFixture]'s project. It mirrors the platform `codeInsightFixture`, with one
 * crucial difference: the wrapped [IdeaProjectTestFixture] **disposes the project** in its `tearDown()`.
 *
 * [CodeInsightTestFixtureImpl.tearDown] calls that `tearDown()` *before* it asserts that all `VirtualFilePointer`s are
 * disposed, so the project-scoped pointers the Maven import creates (e.g. `src/main/java` source roots) are released in
 * the correct order rather than reported as leaks. The project is created by [projectFixture]; disposing it here makes
 * that fixture's own (idempotent) close a no-op.
 */
private fun mavenCodeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<CodeInsightTestFixture> = testFixture {
  val project = projectFixture.init()
  val tempDir = tempDirFixture.init()
  val projectTestFixture = object : IdeaProjectTestFixture {
    override fun getProject(): Project = project
    override fun getModule(): Module {
      project.modules.firstOrNull()?.let { return it }
      // The legacy heavy test fixture always provided a module, so code-insight could configure files even when a test
      // does not import a Maven project (e.g. parent/resolution highlighting tests on a not-yet-imported pom). Create a
      // throwaway non-persistent module on demand to match that. Tests that assert on the module set import a Maven
      // project first, so project.modules is already non-empty and this fallback never adds an unexpected module.
      return runWriteAction {
        ModuleManager.getInstance(project).newNonPersistentModule("light", "")
      }
    }
    override fun setUp() {
    }
    override fun tearDown() {
      // Dispose the project here, inside CodeInsightTestFixtureImpl.tearDown() and before its VirtualFilePointerTracker
      // assertion, so the Maven import's project-scoped pointers are disposed in the right order.
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }
  val tempDirTestFixture = object : TempDirTestFixtureImpl() {
    override fun doCreateTempDirectory(): Path = tempDir
    override fun deleteOnTearDown(): Boolean = false
  }
  val codeInsight = CodeInsightTestFixtureImpl(projectTestFixture, tempDirTestFixture)
  codeInsight.setUp()
  initialized(codeInsight as CodeInsightTestFixture) {
    codeInsight.tearDown()
  }
}
