// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.junit.Assert
import org.junit.Test
import java.io.IOException

class MavenProjectsManagerWatcherTest : MavenMultiVersionImportingTestCase() {

  private var myProjectsTreeTracker: MavenProjectTreeTracker? = null

  override fun setUp() = runBlocking {
    super.setUp()
    myProjectsTreeTracker = MavenProjectTreeTracker()
    projectsManager.addProjectsTreeListener(myProjectsTreeTracker!!, getTestRootDisposable())
    initProjectsManager(true)
    createProjectPom(createPomContent("test", "project"))
    importProjectAsync()
  }

  @Test
  fun testChangeConfigInAnotherProjectShouldNotUpdateOur() = runBlocking {
    assertNoPendingProjectForReload()
    createPomFile(createProjectSubDir("../another"), createPomContent("another", "another"))
    assertNoPendingProjectForReload()
    val mavenConfig = createProjectSubFile("../another/.mvn/maven.config")
    assertNoPendingProjectForReload()
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true")
    assertNoPendingProjectForReload()
  }

  @Test
  fun testChangeConfigInOurProjectShouldCallUpdatePomFile() = runBlocking {
    assertNoPendingProjectForReload()
    val mavenConfig = createProjectSubFile(".mvn/maven.config")
    updateAllProjects()
    assertNoPendingProjectForReload()
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true")
    assertHasPendingProjectForReload()
    scheduleProjectImportAndWait()
  }

  @Test
  fun testChangeConfigInAnotherProjectShouldCallItIfItWasAdded() = runBlocking {
    assertNoPendingProjectForReload()
    val anotherPom = createPomFile(createProjectSubDir("../another"), createPomContent("another", "another"))
    val mavenConfig = createProjectSubFile("../another/.mvn/maven.config")
    assertNoPendingProjectForReload()
    addManagedFiles(anotherPom)
    assertNoPendingProjectForReload()
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true")
    assertHasPendingProjectForReload()
    scheduleProjectImportAndWait()
  }

  @Test
  fun testSaveDocumentChangesBeforeAutoImport() = runBlocking {
    assertNoPendingProjectForReload()
    assertModules("project")
    replaceContent(projectPom, createPomXml(
      """
            ${createPomContent("test", "project")}<packaging>pom</packaging>
            <modules><module>module</module></modules>
            """.trimIndent()))
    createModulePom("module", createPomContent("test", "module"))
    scheduleProjectImportAndWait()
    assertModules("project", "module")
    replaceDocumentString(projectPom, "<modules><module>module</module></modules>", "")

    scheduleProjectImportAndWait()
    assertModules("project")
  }

  @Test
  fun testIncrementalAutoReload() = runBlocking {
    assertRootProjects("project")
    assertNoPendingProjectForReload()
    val module1 = createModulePom("module1", createPomContent("test", "module1"))
    val module2 = createModulePom("module2", createPomContent("test", "module2"))
    assertRootProjects("project")
    assertNoPendingProjectForReload()
    addManagedFiles(module1)
    addManagedFiles(module2)
    assertRootProjects("project", "module1", "module2")
    assertNoPendingProjectForReload()
    replaceDocumentString(module1, "test", "group.id")
    myProjectsTreeTracker!!.reset()
    scheduleProjectImportAndWait()
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("project").updateCounter)
    assertEquals(1, myProjectsTreeTracker!!.getProjectStatus("module1").updateCounter)
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("module2").updateCounter)
    replaceDocumentString(module2, "test", "group.id")
    myProjectsTreeTracker!!.reset()
    scheduleProjectImportAndWait()
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("project").updateCounter)
    assertEquals(0, myProjectsTreeTracker!!.getProjectStatus("module1").updateCounter)
    assertEquals(1, myProjectsTreeTracker!!.getProjectStatus("module2").updateCounter)
    replaceDocumentString(module1, "group.id", "test")
    replaceDocumentString(module2, "group.id", "test")
    myProjectsTreeTracker!!.reset()
    scheduleProjectImportAndWait()
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
    replaceContent(projectPom, createPomXml(xml))
    scheduleProjectImportAndWait()
    assertRootProjects("project")
    assertModules("project")
    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("junit4"), listOf("junit5"))
    assertHasPendingProjectForReload()
    scheduleProjectImportAndWait()
    assertMavenProjectDependencies("test:project:1", "junit:junit:4.12")
    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("junit5"), listOf("junit4"))
    assertHasPendingProjectForReload()
    scheduleProjectImportAndWait()
    assertMavenProjectDependencies("test:project:1", "org.junit.jupiter:junit-jupiter-engine:5.9.1")
  }

  private fun assertMavenProjectDependencies(projectMavenCoordinates: String, vararg expectedDependencies: String) {
    val mavenId = MavenId(projectMavenCoordinates)
    val mavenProject = projectsManager.getProjectsTree().findProject(mavenId)
    val actualDependencies = mavenProject!!.dependencyTree.map { it.artifact.mavenId.getKey() }
    Assert.assertEquals(java.util.List.of(*expectedDependencies), actualDependencies)
  }

  private suspend fun addManagedFiles(pom: VirtualFile) {
    waitForImportWithinTimeout {
      projectsManager.addManagedFiles(listOf(pom))
    }
  }

  private fun replaceContent(file: VirtualFile, content: String) {
    WriteCommandAction.runWriteCommandAction(project, ThrowableComputable<Any?, IOException?> {
      VfsUtil.saveText(file, content)
      null
    } as ThrowableComputable<*, IOException?>)
  }

  private fun replaceDocumentString(file: VirtualFile?, oldString: String, newString: String?) {
    val fileDocumentManager = FileDocumentManager.getInstance()
    WriteCommandAction.runWriteCommandAction(project) {
      val document = fileDocumentManager.getDocument(file!!)
      val text = document!!.text
      val startOffset = text.indexOf(oldString)
      val endOffset = startOffset + oldString.length
      document.replaceString(startOffset, endOffset, newString!!)
    }
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
