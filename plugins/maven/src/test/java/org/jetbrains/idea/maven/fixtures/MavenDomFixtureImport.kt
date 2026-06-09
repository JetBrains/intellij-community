// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.utils.RealMavenPreventionFixture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.Observation
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog

// Project import / sync orchestration.

@RequiresBackgroundThread
suspend fun MavenDomTestFixture.awaitConfiguration() {
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

suspend fun MavenDomTestFixture.importProjectAsync(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  val pom = createProjectPom(xml)
  importProjectAsync()
  return pom
}

suspend fun MavenDomTestFixture.importProjectAsync() {
  importProjectsAsync(listOf(projectPom))
}

suspend fun MavenDomTestFixture.importProjectsAsync(vararg files: VirtualFile) {
  importProjectsAsync(files.toList())
}

suspend fun MavenDomTestFixture.importProjectsAsync(files: List<VirtualFile>) {
  files.forEach { checkNoFixtureTags(it) }
  projectsManager.addManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE, null, null, true)
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
  awaitConfiguration()
  // Mirror MavenDomWithIndicesTestCase: wait for the project's GAV index so reference resolution/highlighting is stable.
  if (null != indices) {
    MavenIndicesManager.getInstance(project).waitForGavUpdateCompleted()
  }
}

/** Imports [files] tolerating reading errors and `<caret>`/`<error>` markers left in the poms. */
suspend fun MavenDomTestFixture.importProjectsWithErrors(vararg files: VirtualFile) {
  projectsManager.addManagedFilesWithProfiles(files.toList(), MavenExplicitProfiles.NONE, null, null, true)
  awaitConfiguration()
}

suspend fun MavenDomTestFixture.importProjectWithProfiles(vararg profiles: String) {
  projectsManager.addManagedFilesWithProfiles(listOf(projectPom), MavenExplicitProfiles(profiles.toList(), emptyList()), null, null, true)
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
  awaitConfiguration()
}

suspend fun MavenDomTestFixture.updateAllProjects() {
  projectsManager.updateAllMavenProjects(MavenSyncSpec.incremental("MavenDomTestFixture incremental sync"))
}

suspend fun MavenDomTestFixture.configureProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String) {
  val file = createProjectPom(xml)
  configTest(file)
}

fun MavenDomTestFixture.removeFromLocalRepository(relativePath: String) {
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
suspend fun MavenDomTestFixture.withoutSync(test: suspend () -> Unit) {
  val preventionFixture = RealMavenPreventionFixture(project)
  preventionFixture.setUp()
  try {
    test()
  }
  finally {
    preventionFixture.tearDown()
  }
}

fun MavenDomTestFixture.runBlockingNoSync(test: suspend () -> Unit) {
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
