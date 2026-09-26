// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.fixtures.MavenImportingTestFixture
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImporterSettings
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.buildtool.MavenSyncSession
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.preimport.MavenProjectStaticImporter
import org.jetbrains.idea.maven.project.preimport.SimpleStructureProjectVisitor

// Ported from AbstractMavenStaticSyncTest.importProjectsAsync: import via the static pre-importer instead of real Maven.
suspend fun MavenImportingTestFixture.importProjectsStaticSync(files: List<VirtualFile>) {
  val activity = ProjectImportCollector.IMPORT_ACTIVITY.started(project)
  try {
    val result = MavenProjectStaticImporter.getInstance(project)
      .syncStatic(MavenSyncSession(project, MavenSyncSpec.incremental("test"), projectsManager.projectsTree),
                  files,
                  null,
                  mavenImporterSettings,
                  mavenGeneralSettings,
                  true,
                  SimpleStructureProjectVisitor(),
                  activity,
                  true)
    projectsManager.initForTests()
    projectsManager.projectsTree.updater().copyFrom(result.projectTree)
  }
  finally {
    activity.finished()
  }
}

suspend fun MavenImportingTestFixture.importProjectStaticSync(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  val pom = createProjectPom(xml)
  importProjectStaticSync()
  return pom
}

suspend fun MavenImportingTestFixture.importProjectStaticSync() {
  importProjectsStaticSync(listOf(projectPom))
}

suspend fun MavenImportingTestFixture.importProjectStaticSync(file: VirtualFile) {
  importProjectsStaticSync(listOf(file))
}

suspend fun MavenImportingTestFixture.importProjectsStaticSync(vararg files: VirtualFile) {
  importProjectsStaticSync(files.toList())
}
