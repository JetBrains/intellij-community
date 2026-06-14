// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertHasPendingProjectForReload
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertNoPendingProjectForReload
import com.intellij.maven.testFramework.fixtures.assertRootProjects
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomFile
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.scheduleProjectImportAndWait
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsManagerWatcherTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  private var myProjectsTreeTracker: MavenProjectTreeTracker? = null

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    myProjectsTreeTracker = MavenProjectTreeTracker()
    maven.project.messageBus.connect(maven.disposable).subscribe(MavenProjectsTree.Listener.TOPIC, myProjectsTreeTracker!!)
    maven.initProjectsManager(true)
    maven.createProjectPom(createPomContent("test", "project"))
    maven.importProjectAsync()
  }

  @Test
  fun testChangeConfigInAnotherProjectShouldNotUpdateOur() = runBlocking {
    maven.assertNoPendingProjectForReload()
    maven.createPomFile(maven.createProjectSubDir("../another"), createPomContent("another", "another"))
    maven.assertNoPendingProjectForReload()
    val mavenConfig = maven.createProjectSubFile("../another/.mvn/maven.config")
    maven.assertNoPendingProjectForReload()
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true")
    maven.assertNoPendingProjectForReload()
  }

  @Test
  fun testChangeConfigInOurProjectShouldCallUpdatePomFile() = runBlocking {
    maven.assertNoPendingProjectForReload()
    val mavenConfig = maven.createProjectSubFile(".mvn/maven.config")
    maven.updateAllProjects()
    maven.assertNoPendingProjectForReload()
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true")
    maven.assertHasPendingProjectForReload()
    maven.scheduleProjectImportAndWait()
  }

  @Test
  fun testChangeConfigInAnotherProjectShouldCallItIfItWasAdded() = runBlocking {
    maven.assertNoPendingProjectForReload()
    val anotherPom = maven.createPomFile(maven.createProjectSubDir("../another"), createPomContent("another", "another"))
    val mavenConfig = maven.createProjectSubFile("../another/.mvn/maven.config")
    maven.assertNoPendingProjectForReload()
    addManagedFiles(anotherPom)
    maven.assertNoPendingProjectForReload()
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true")
    maven.assertHasPendingProjectForReload()
    maven.scheduleProjectImportAndWait()
  }
  private fun printDebugMessage(message: String) {
    println("Debug: $message")
  }

  @Test
  fun testSaveDocumentChangesBeforeAutoImport() = runBlocking {
    maven.assumeModel_4_0_0("IDEA-379195")
    maven.assertNoPendingProjectForReload()
    maven.assertModules("project")
    replaceContent(maven.projectPom, maven.createPomXml(
      """
            ${createPomContent("test", "project")}<packaging>pom</packaging>
            <modules><module>module</module></modules>
            """.trimIndent()))
    maven.createModulePom("module", createPomContent("test", "module"))
    maven.scheduleProjectImportAndWait()
    maven.assertModules("project", "module")
    replaceDocumentString(maven.projectPom, "<modules><module>module</module></modules>", "")

    maven.scheduleProjectImportAndWait()
    maven.assertModules("project")
  }

  @Test
  fun testIncrementalAutoReload() = runBlocking {
    maven.assertRootProjects("project")
    maven.assertNoPendingProjectForReload()
    val module1 = maven.createModulePom("module1", createPomContent("test", "module1"))
    val module2 = maven.createModulePom("module2", createPomContent("test", "module2"))
    maven.assertRootProjects("project")
    maven.assertNoPendingProjectForReload()
    addManagedFiles(module1)
    addManagedFiles(module2)
    maven.assertRootProjects("project", "module1", "module2")
    maven.assertNoPendingProjectForReload()
    replaceDocumentString(module1, "test", "group.id")
    myProjectsTreeTracker!!.reset()
    maven.scheduleProjectImportAndWait()
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("project").updateCounter)
    assertEquals(1, myProjectsTreeTracker!!.getProjectStatus("module1").updateCounter)
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("module2").updateCounter)
    replaceDocumentString(module2, "test", "group.id")
    myProjectsTreeTracker!!.reset()
    maven.scheduleProjectImportAndWait()
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("project").updateCounter)
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("module1").updateCounter)
    assertEquals(1, myProjectsTreeTracker!!.getProjectStatus("module2").updateCounter)
    replaceDocumentString(module1, "group.id", "test")
    replaceDocumentString(module2, "group.id", "test")
    myProjectsTreeTracker!!.reset()
    maven.scheduleProjectImportAndWait()
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("project").updateCounter)
    assertEquals(1, myProjectsTreeTracker!!.getProjectStatus("module1").updateCounter)
    assertEquals(1, myProjectsTreeTracker!!.getProjectStatus("module2").updateCounter)
  }

  @Test
  fun testProfilesAutoReload() = runBlocking {
    val xml = """
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                         
                         <profiles>
                             <profile>
                                 <id>junit4</id>
                                 
                                 <dependencies>
                                     <dependency>
                                         <groupId>junit</groupId>
                                         <artifactId>junit</artifactId>
                                         <version>4.12</version>
                                         <scope>test</scope>
                                     </dependency>
                                 </dependencies>
                             </profile>
                             <profile>
                                 <id>junit5</id>
                                 <dependencies>
                                     <dependency>
                                         <groupId>org.junit.jupiter</groupId>
                                         <artifactId>junit-jupiter-engine</artifactId>
                                         <version>5.9.1</version>
                                         <scope>test</scope>
                                     </dependency>
                                 </dependencies>
                             </profile>
                         </profiles>
                       """.trimIndent()
    replaceContent(maven.projectPom, maven.createPomXml(xml))
    maven.scheduleProjectImportAndWait()
    maven.assertRootProjects("project")
    maven.assertModules("project")
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("junit4"), listOf("junit5"))
    maven.assertHasPendingProjectForReload()
    maven.scheduleProjectImportAndWait()
    assertMavenProjectDependencies("test:project:1", "junit:junit:4.12")
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("junit5"), listOf("junit4"))
    maven.assertHasPendingProjectForReload()
    maven.scheduleProjectImportAndWait()
    assertMavenProjectDependencies("test:project:1", "org.junit.jupiter:junit-jupiter-engine:5.9.1")
  }

  private fun assertMavenProjectDependencies(projectMavenCoordinates: String, vararg expectedDependencies: String) {
    val mavenId = MavenId(projectMavenCoordinates)
    val mavenProject = maven.projectsManager.getProjectsTree().findProject(mavenId)
    val actualDependencies = mavenProject!!.dependencyTree.map { it.artifact.mavenId.getKey() }
    assertEquals(java.util.List.of(*expectedDependencies), actualDependencies)
  }

  private suspend fun addManagedFiles(pom: VirtualFile) {
    maven.waitForImportWithinTimeout {
      maven.projectsManager.addManagedFiles(listOf(pom))
    }
  }

  private fun replaceContent(file: VirtualFile, content: String) {
    WriteCommandAction.runWriteCommandAction(maven.project, ThrowableComputable<Any?, IOException?> {
      VfsUtil.saveText(file, content)
      null
    } as ThrowableComputable<*, IOException?>)
    printDebugMessage("Replaced content in file: ${file.path}")
  }

  private fun replaceDocumentString(file: VirtualFile?, oldString: String, newString: String?) {
    val fileDocumentManager = FileDocumentManager.getInstance()
    WriteCommandAction.runWriteCommandAction(maven.project) {
      val document = fileDocumentManager.getDocument(file!!)
      val text = document!!.text
      val startOffset = text.indexOf(oldString)
      val endOffset = startOffset + oldString.length
      document.replaceString(startOffset, endOffset, newString!!)
    }
    printDebugMessage("Replaced string in document at path: ${file!!.path}")
  }

  internal class MavenProjectTreeTracker : MavenProjectsTree.Listener {
    private val projects: MutableMap<String?, MavenProjectStatus> = HashMap()
    fun getProjectStatus(artifactId: String?): MavenProjectStatus {
      return projects.computeIfAbsent(artifactId) { MavenProjectStatus() }
    }

    fun reset() {
      projects.clear()
    }

    override fun projectsUpdated(updated: List<Pair<MavenProject, MavenProjectChanges>>, deleted: List<MavenProject>) {
      for (it in updated) {
        val artifactId = it.first.mavenId.artifactId
        val projectStatus = getProjectStatus(artifactId)
        projectStatus.updateCounter++
      }
      for (mavenProject in deleted) {
        val artifactId = mavenProject.mavenId.artifactId
        val projectStatus = getProjectStatus(artifactId)
        projectStatus.deleteCounter++
      }
    }
  }

  internal class MavenProjectStatus {
    var updateCounter = 0
    var deleteCounter = 0
  }

  companion object {
    private fun createPomContent(groupId: String, artifactId: String): String {
      return String.format("<groupId>%s</groupId>\n<artifactId>%s</artifactId>\n<version>1.0-SNAPSHOT</version>", groupId, artifactId)
    }
  }
}
