// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.preimport.MavenProjectPreImporter
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.server.MavenIndexerWrapper
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.Test
import java.io.File
import java.util.function.Predicate

class MavenPreimportingTest : MavenMultiVersionImportingTestCase() {

  private lateinit var disposable: Disposable
  override fun setUp() {
    super.setUp()

    disposable = Disposer.newDisposable("Real maven protector for MavenPreimportingTest")
    val syncViewManager = object : SyncViewManager(myProject) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        noRealMavenAllowed()
      }
    }
    myProject.replaceService(SyncViewManager::class.java, syncViewManager, disposable)
    ApplicationManager.getApplication().replaceService(MavenServerManager::class.java, NoRealMavenServerManager(), disposable)


  }

  override fun tearDown() {
    runAll(
      { Disposer.dispose(disposable) },
      { super.tearDown() }
    )
  }

  @Test
  fun testImportLibraryDependency() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>somedep</groupId>
                        <artifactId>somedep</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    assertModules("project")
    assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportLibraryDependencyWithPropertyPlaceholder() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <somedep.version>4.0</somedep.version>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>somedep</groupId>
                        <artifactId>somedep</artifactId>
                        <version>${'$'}{somedep.version}</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    assertModules("project")
    assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportLibraryDependencyWithRecursicePropertyPlaceholder() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <somedep.version>${'$'}{another.property}</somedep.version>
                        <another.property>4.0</another.property>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>somedep</groupId>
                        <artifactId>somedep</artifactId>
                        <version>${'$'}{somedep.version}</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    assertModules("project")
    assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }


  @Test
  fun testImportLibraryDependencyWithPlaceholderInParent() = runBlocking {
    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        <dependencies>
            <dependency>
                <groupId>somedep</groupId>
                <artifactId>somedep</artifactId>
                <version>${'$'}{somedep.version}</version>
            </dependency>
        </dependencies>
        """)

    createProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <somedep.version>4.0</somedep.version>
                    </properties>
                    <modules>
                        <module>m1</module>
                    </modules>
""")
    importProjectAsync()

    assertModules("project", "m1")
    assertModuleLibDep("m1", "Maven: somedep:somedep:4.0",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportProjectWithTargetVersion() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.target>14</maven.compiler.target>
                        <maven.compiler.source>14</maven.compiler.source>
                    </properties>
                    """.trimIndent())


    readAction {
      val module = getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }


  override suspend fun importProjectsAsync(files: List<VirtualFile>) {
    val activity = ProjectImportCollector.IMPORT_ACTIVITY.started(myProject)
    try {
      MavenProjectPreImporter.getInstance(myProject)
        .preimport(files, null, mavenImporterSettings, mavenGeneralSettings, activity)
    }
    finally {
      activity.finished()
    }
  }
}


class NoRealMavenServerManager : MavenServerManager {
  override fun dispose() {
  }

  override fun getAllConnectors(): MutableCollection<MavenServerConnector> {
    noRealMavenAllowed()
  }

  override fun restartMavenConnectors(project: Project?, wait: Boolean, condition: Predicate<MavenServerConnector>?) {
    noRealMavenAllowed()
  }

  override fun getConnector(project: Project, workingDirectory: String): MavenServerConnector {
    noRealMavenAllowed()
  }

  override fun shutdownConnector(connector: MavenServerConnector?, wait: Boolean): Boolean {
    noRealMavenAllowed()
  }

  override fun shutdown(wait: Boolean) {
    noRealMavenAllowed()
  }

  override fun getMavenEventListener(): File {
    noRealMavenAllowed()
  }

  override fun createEmbedder(project: Project?, alwaysOnline: Boolean, multiModuleProjectDirectory: String): MavenEmbedderWrapper {
    noRealMavenAllowed()
  }

  @Deprecated("Deprecated in Java")
  override fun createIndexer(project: Project): MavenIndexerWrapper {
    noRealMavenAllowed()
  }

  override fun createIndexer(): MavenIndexerWrapper {
    noRealMavenAllowed()
  }

}

private fun noRealMavenAllowed(): Nothing {
  throw IllegalStateException("No real maven in this test class!")
}