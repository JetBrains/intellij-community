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
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.function.Predicate

class MavenPreimportingTest : MavenMultiVersionImportingTestCase() {

  private lateinit var disposable: Disposable
  override fun setUp() {
    super.setUp()
    Assume.assumeTrue(isWorkspaceImport)

    disposable = Disposer.newDisposable("Real maven protector for MavenPreimportingTest")
    val syncViewManager = object : SyncViewManager(project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        noRealMavenAllowed()
      }
    }
    project.replaceService(SyncViewManager::class.java, syncViewManager, disposable)
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
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
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
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
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
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
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
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
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

  @Test
  fun testImportProjectWithCompilerConfig() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                          <configuration>
                            <source>14</source>
                            <target>14</target>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      val module = getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigWithoutGroupId() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                          <configuration>
                            <source>14</source>
                            <target>14</target>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      val module = getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigOfParent() = runBlocking {

    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                    </modules>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                          <configuration>
                            <source>14</source>
                            <target>14</target>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())




    readAction {
      val module = getModule("m1")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithTargetVersionOfParent() = runBlocking {

    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <maven.compiler.target>14</maven.compiler.target>
                        <maven.compiler.source>14</maven.compiler.source>
                    </properties>
                    <modules>
                        <module>m1</module>
                    </modules>
                    
                    """.trimIndent())




    readAction {
      val module = getModule("m1")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }


  @Test
  fun testImportProjectWithKotlinConfig() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <kotlin.version>1.9.21</kotlin.version>
                    </properties>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.jetbrains.kotlin</groupId>
                          <artifactId>kotlin-maven-plugin</artifactId>
                          <version>${'$'}{kotlin.version}</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      assertSources("project", "src/main/java", "src/main/kotlin")
    }
  }

  @Test
  fun testImportProjectWithBuildHelperPlugin() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
        
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>3.2.0</version>
                          <executions>
                            <execution>
                              <id>add-source-exec</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                    <source>src/main/anothersrc/</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-test-source-exec</id>
                              <phase>generate-test-sources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                    <source>src/main/sometestdir/</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      assertSources("project", "src/main/java", "src/main/anothersrc")
      assertTestSources("project", "src/test/java", "src/main/sometestdir")
    }
  }

  @Test
  fun testImportProjectWithKotlinConfigInParent() = runBlocking {

    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                    </modules>
                    <properties>
                        <kotlin.version>1.9.21</kotlin.version>
                    </properties>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.jetbrains.kotlin</groupId>
                          <artifactId>kotlin-maven-plugin</artifactId>
                          <version>${'$'}{kotlin.version}</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())




    readAction {
      assertSources("m1", "src/main/java", "src/main/kotlin")
    }
  }

  @Test
  fun testImportSourceDirectory() = runBlocking {

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>src/main/somedir</sourceDirectory>
                    </build>
                    """.trimIndent())




    readAction {
      assertSources("project", "src/main/somedir")
    }
  }

  @Test
  fun testImportSourceDirectoryWithBasedirProp() = runBlocking {

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${'$'}{basedir}/src/main/somedir</sourceDirectory>
                    </build>
                    """.trimIndent())




    readAction {
      assertSources("project", "src/main/somedir")
    }
  }

  @Test
  fun testImportSourceDirectoryWithDefinedProp() = runBlocking {

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <our.src.dir>src/main/somedir</our.src.dir>
                    </properties>

                    <build>
                      <sourceDirectory>${'$'}{our.src.dir}</sourceDirectory>
                    </build>
                    """.trimIndent())


    readAction {
      assertSources("project", "src/main/somedir")
    }
  }


  @Test
  fun testImportSourceDirectoryWithUndefinedPropShouldNotToAddRootPathAsASource() = runBlocking {

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>

                    <build>
                      <sourceDirectory>${'$'}{some.unknown.property}/some/path</sourceDirectory>
                    </build>
                    """.trimIndent())


    readAction {
      assertSources("project")
    }
  }

  @Test
  fun testImportSourceDirectoryWithSystemVariable() = runBlocking {

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>

                    <build>
                      <sourceDirectory>${'$'}{user.home}/some/path</sourceDirectory>
                    </build>
                    """.trimIndent())


    readAction {
      assertContentRootSources("project", "${System.getProperty("user.home")}/some/path", "")
    }
  }


  override suspend fun importProjectsAsync(files: List<VirtualFile>) {
    val activity = ProjectImportCollector.IMPORT_ACTIVITY.started(project)
    try {
      MavenProjectPreImporter.getInstance(project)
        .preimport(files, null, mavenImporterSettings, mavenGeneralSettings,true, activity)
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