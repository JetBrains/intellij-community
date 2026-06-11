// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.utils.RealMavenPreventionFixture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.Observation
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil

// Project import / sync orchestration.

@RequiresBackgroundThread
suspend fun MavenTestFixture.awaitConfiguration() {
  val isEdt = ApplicationManager.getApplication().isDispatchThread
  if (isEdt) {
    MavenLog.LOG.warn("Calling awaitConfiguration() from EDT sometimes causes deadlocks, even though it shouldn't")
  }
  assertFalse("Call awaitConfiguration() from background thread", isEdt)
  Observation.awaitConfiguration(project) { message ->
    logConfigurationMessage(message)
  }
}

private fun logConfigurationMessage(message: String) {
  if (message.contains("scanning")) return
  MavenLog.LOG.warn(message)
}

suspend fun MavenImportingTestFixture.importProjectAsync(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  val pom = createProjectPom(xml)
  importProjectAsync()
  return pom
}

suspend fun MavenImportingTestFixture.importProjectAsync() {
  importProjectsAsync(listOf(projectPom))
}

suspend fun MavenImportingTestFixture.importProjectsAsync(vararg files: VirtualFile) {
  importProjectsAsync(files.toList())
}

suspend fun MavenImportingTestFixture.importProjectsAsync(files: List<VirtualFile>) {
  files.forEach { checkNoFixtureTags(it) }
  projectsManager.addManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE, null, null, true)
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
  awaitConfiguration()
  // Mirror MavenDomWithIndicesTestCase: wait for the project's GAV index so reference resolution/highlighting is stable.
  if (this is MavenDomTestFixture && null != indices) {
    MavenIndicesManager.getInstance(project).waitForGavUpdateCompleted()
  }
}

/** Imports [files] tolerating reading errors and `<caret>`/`<error>` markers left in the poms. */
suspend fun MavenImportingTestFixture.importProjectsWithErrors(vararg files: VirtualFile) {
  projectsManager.addManagedFilesWithProfiles(files.toList(), MavenExplicitProfiles.NONE, null, null, true)
  awaitConfiguration()
}

suspend fun MavenImportingTestFixture.importProjectWithProfiles(vararg profiles: String) {
  projectsManager.addManagedFilesWithProfiles(listOf(projectPom), MavenExplicitProfiles(profiles.toList(), emptyList()), null, null, true)
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
  awaitConfiguration()
}

suspend fun MavenImportingTestFixture.updateAllProjects() {
  projectsManager.updateAllMavenProjects(MavenSyncSpec.incremental("MavenDomTestFixture incremental sync"))
}

suspend fun MavenImportingTestFixture.updateAllProjectsFullSync() {
  projectsManager.updateAllMavenProjects(MavenSyncSpec.full("MavenDomTestFixture full sync"))
}

fun MavenImportingTestFixture.setIgnoredFilesPathForNextImport(paths: List<String?>) {
  projectsManager.setIgnoredFilesPaths(paths)
}

fun MavenImportingTestFixture.setIgnoredPathPatternsForNextImport(patterns: List<String?>) {
  projectsManager.setIgnoredFilesPatterns(patterns)
}

val MavenImportingTestFixture.mavenGeneralSettings
  get() = projectsManager.generalSettings

suspend fun MavenDomTestFixture.configureProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String) {
  val file = createProjectPom(xml)
  configTest(file)
}

fun MavenImportingTestFixture.removeFromLocalRepository(relativePath: String) {
  if (SystemInfo.isWindows) {
    MavenServerManager.getInstance().closeAllConnectorsAndWait()
  }
  FileUtil.delete(repositoryPath.resolve(relativePath))
}

private fun checkNoFixtureTags(file: VirtualFile) {
  val content = String(file.contentsToByteArray())
  check(!content.contains("<caret")) { "There should be no any <caret> tag in pom.xml during import" }
  check(!content.contains("<error")) { "There should be no any <error> tag in pom.xml during import" }
}

/** Runs [test] with real Maven sync disabled, so in-test pom edits don't trigger background re-imports. */
suspend fun MavenImportingTestFixture.withoutSync(test: suspend () -> Unit) {
  val preventionFixture = RealMavenPreventionFixture(project)
  preventionFixture.setUp()
  try {
    test()
  }
  finally {
    preventionFixture.tearDown()
  }
}

fun MavenTestFixture.runBlockingNoSync(test: suspend () -> Unit) {
  val preventionFixture = RealMavenPreventionFixture(project)
  preventionFixture.setUp()
  try {
    @Suppress("SSBasedInspection")
    runBlocking {
      test()
    }
  }
  finally {
    preventionFixture.tearDown()
  }
}

fun MavenImportingTestFixture.initProjectsManager(enableEventHandling: Boolean) {
  projectsManager.initForTests()
  if (enableEventHandling) {
    projectsManager.enableAutoImportInTests()
  }
}

// Auto-reload (external-system project tracker) assertions, mirroring MavenImportingTestCase. The legacy
// assertAutoReloadIsEnabled() guard is omitted: the fixture does not track that flag; tests enable auto-reload via
// projectsManager.enableAutoImportInTests() (see initProjectsManager).

private val MavenImportingTestFixture.projectWithMavenNotificationExists: Boolean
  get() = AutoImportProjectNotificationAware.getInstance(project).getProjectsWithNotification().any { it.systemId == MavenUtil.SYSTEM_ID }

suspend fun MavenImportingTestFixture.assertHasPendingProjectForReload() {
  awaitConfiguration()
  assertTrue("Expected notification about pending projects for auto-reload",
             AutoImportProjectNotificationAware.getInstance(project).isNotificationVisible())
  assertTrue(projectWithMavenNotificationExists)
}

suspend fun MavenImportingTestFixture.assertNoPendingProjectForReload() {
  awaitConfiguration()
  assertFalse(projectWithMavenNotificationExists)
}

@RequiresBackgroundThread
suspend fun MavenImportingTestFixture.scheduleProjectImportAndWait() {
  // otherwise all imports will be skipped
  assertHasPendingProjectForReload()
  withContext(Dispatchers.EDT) {
    AutoImportProjectTracker.getInstance(project).scheduleProjectRefresh()
  }
  awaitConfiguration()
  // otherwise project settings was modified while importing
  assertNoPendingProjectForReload()
}

suspend fun MavenImportingTestFixture.importProjectAsync(file: VirtualFile) {
  importProjectsAsync(listOf(file))
}

val MavenImportingTestFixture.projectsTree: MavenProjectsTree
  get() = projectsManager.projectsTree

val MavenImportingTestFixture.testRootDisposable: Disposable
  get() = disposable