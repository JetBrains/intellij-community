// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.utils.RealMavenPreventionFixture
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import org.jetbrains.idea.maven.project.preimport.MavenProjectStaticImporter
import org.jetbrains.idea.maven.project.preimport.SimpleStructureProjectVisitor

abstract class AbstractMavenStaticSyncTest : MavenMultiVersionImportingTestCase() {

  private lateinit var noRealMaven: RealMavenPreventionFixture
  override fun setUp() {
    super.setUp()
    noRealMaven = RealMavenPreventionFixture(project)
    noRealMaven.setUp()

  }

  override fun tearDown() {
    runAll(
      { noRealMaven.tearDown() },
      { super.tearDown() }
    )
  }

  override suspend fun importProjectsAsync(files: List<VirtualFile>) {
    val activity = ProjectImportCollector.IMPORT_ACTIVITY.started(project)
    try {
      val result = MavenProjectStaticImporter.getInstance(project)
        .syncStatic(files, null, mavenImporterSettings, mavenGeneralSettings, true, SimpleStructureProjectVisitor(), activity, true)
      projectsManager.initForTests()
      projectsManager.projectsTree.updater().copyFrom(result.projectTree)
    }
    finally {
      activity.finished()
    }
  }
}