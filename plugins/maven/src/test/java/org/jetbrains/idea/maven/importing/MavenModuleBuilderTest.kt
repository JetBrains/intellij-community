// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.AbstractMavenModuleBuilder
import org.jetbrains.idea.maven.wizards.MavenJavaModuleBuilder
import org.junit.Test

class MavenModuleBuilderTest : MavenMultiVersionImportingTestCase() {
  private var myBuilder: AbstractMavenModuleBuilder? = null

  override fun setUp() {
    super.setUp()
    myBuilder = MavenJavaModuleBuilder()

    createJdk()
    setModuleNameAndRoot("module", projectPath)
  }

  @Test
  fun testModuleRecreation() = runBlocking {
    val id = MavenId("org.foo", "module", "1.0")

    createNewModule(id)
    assertModules(id.artifactId!!)
    deleteModule(id.artifactId)
    createNewModule(id)
    assertModules(id.artifactId!!)

    updateAllProjects()
    withContext(Dispatchers.EDT){
      writeIntentReadAction {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
  }

  @Test
  fun testCreatingBlank() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    val id = MavenId("org.foo", "module", "1.0")
    createNewModule(id)

    val projects = MavenProjectsManager.getInstance(project).getProjects()
    assertEquals(1, projects.size)

    val mavenProject = projects[0]
    assertEquals(id, mavenProject.mavenId)

    assertModules("module")
    MavenProjectsManager.getInstance(project).isMavenizedModule(getModule("module"))
    assertSame(project, MavenProjectsManager.getInstance(project).findProject(getModule("module")))

    assertNotNull(projectRoot.findFileByRelativePath("src/main/java"))
    assertNotNull(projectRoot.findFileByRelativePath("src/test/java"))

    assertSources("module", "src/main/java")
    assertTestSources("module", "src/test/java")
  }

  @Test
  fun testInheritJdkFromProject() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    createNewModule(MavenId("org.foo", "module", "1.0"))
    val manager = ModuleRootManager.getInstance(getModule("module"))
    assertTrue(manager.isSdkInherited())
  }

  @Test
  fun testCreatingFromArchetype() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    setArchetype(MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null))
    val id = MavenId("org.foo", "module", "1.0")
    createNewModule(id)

    val projects = MavenProjectsManager.getInstance(project).getProjects()
    assertEquals(1, projects.size)

    val project = projects[0]
    assertEquals(id, project.mavenId)

    assertNotNull(projectRoot.findFileByRelativePath("src/main/java/org/foo/App.java"))
    assertNotNull(projectRoot.findFileByRelativePath("src/test/java/org/foo/AppTest.java"))

    assertSources("module", "src/main/java")
    assertTestSources("module", "src/test/java")
  }

  @Test
  fun testAddingNewlyCreatedModuleToTheAggregator() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "$projectPath/module")
    setAggregatorProject(projectPom)
    createNewModule(MavenId("org.foo", "module", "1.0"))

    assertEquals(createPomXml("""
                                <groupId>test</groupId><artifactId>project</artifactId>
                                    <packaging>pom</packaging>
                                    <version>1</version>
                                    <modules>
                                        <module>module</module>
                                    </modules>
                                """.trimIndent()),
                 StringUtil.convertLineSeparators(VfsUtil.loadText(projectPom)))
  }

  @Test
  fun testAddingManagedProjectIfNoArrgerator() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(1, projectsManager.projectsTreeForTests.managedFilesPaths.size)

    setModuleNameAndRoot("module", "$projectPath/module")
    setAggregatorProject(null)
    createNewModule(MavenId("org.foo", "module", "1.0"))
    projectRoot.findFileByRelativePath("module/pom.xml")

    assertEquals(2, projectsManager.projectsTreeForTests.managedFilesPaths.size)
  }

  @Test
  fun testDoNotAddManagedProjectIfAddingAsModuleToAggregator() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(1, projectsManager.projectsTreeForTests.managedFilesPaths.size)

    setModuleNameAndRoot("module", "$projectPath/module")
    setAggregatorProject(projectPom)
    createNewModule(MavenId("org.foo", "module", "1.0"))
    projectRoot.findFileByRelativePath("module/pom.xml")

    assertEquals(1, projectsManager.projectsTreeForTests.managedFilesPaths.size)
  }

  @Test
  fun testAddingParent() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "$projectPath/module")
    setParentProject(projectPom)
    createNewModule(MavenId("org.foo", "module", "1.0"))

    assertEquals("""
                   <?xml version="1.0" encoding="UTF-8"?>
                   <project xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                       <parent>
                           <artifactId>project</artifactId>
                           <groupId>test</groupId>
                           <version>1</version>
                       </parent>
                       <modelVersion>4.0.0</modelVersion>

                       <groupId>org.foo</groupId>
                       <artifactId>module</artifactId>
                       <version>1.0</version>


                   </project>


                   """.trimIndent(),
                 VfsUtil.loadText(projectRoot.findFileByRelativePath("module/pom.xml")!!))
  }

  @Test
  fun testAddingParentWithInheritedProperties() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "$projectPath/module")
    setParentProject(projectPom)
    setInheritedOptions(true, true)
    createNewModule(MavenId("org.foo", "module", "1.0"))

    assertEquals("""
                   <?xml version="1.0" encoding="UTF-8"?>
                   <project xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                       <parent>
                           <artifactId>project</artifactId>
                           <groupId>test</groupId>
                           <version>1</version>
                       </parent>
                       <modelVersion>4.0.0</modelVersion>

                       <artifactId>module</artifactId>


                   </project>


                   """.trimIndent(),
                 VfsUtil.loadText(projectRoot.findFileByRelativePath("module/pom.xml")!!))
  }

  @Test
  fun testAddingParentAndInheritWhenGeneratingFromArchetype() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "$projectPath/module")
    setParentProject(projectPom)
    setInheritedOptions(true, true)
    setArchetype(MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null))
    createNewModule(MavenId("org.foo", "module", "1.0"))

    assertEquals("""
                   <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                       <parent>
                           <artifactId>project</artifactId>
                           <groupId>test</groupId>
                           <version>1</version>
                       </parent>
                       <modelVersion>4.0.0</modelVersion>

                       <artifactId>module</artifactId>
                       <packaging>jar</packaging>

                       <name>module</name>
                       <url>http://maven.apache.org</url>

                       <properties>
                           <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                       </properties>

                       <dependencies>
                           <dependency>
                               <groupId>junit</groupId>
                               <artifactId>junit</artifactId>
                               <version>3.8.1</version>
                               <scope>test</scope>
                           </dependency>
                       </dependencies>
                   </project>
                   
                   """.trimIndent(),
                 VfsUtil.loadText(projectRoot.findFileByRelativePath("module/pom.xml")!!))
  }

  @Test
  fun testAddingParentWithRelativePath() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "$projectPath/subDir/module")
    setParentProject(projectPom)
    createNewModule(MavenId("org.foo", "module", "1.0"))

    assertEquals("""
                   <?xml version="1.0" encoding="UTF-8"?>
                   <project xmlns="http://maven.apache.org/POM/4.0.0"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                       <parent>
                           <artifactId>project</artifactId>
                           <groupId>test</groupId>
                           <version>1</version>
                           <relativePath>../../pom.xml</relativePath>
                       </parent>
                       <modelVersion>4.0.0</modelVersion>

                       <groupId>org.foo</groupId>
                       <artifactId>module</artifactId>
                       <version>1.0</version>


                   </project>


                   """.trimIndent(),
                 VfsUtil.loadText(projectRoot.findFileByRelativePath("subDir/module/pom.xml")!!))
  }

  private fun deleteModule(name: String?) {
    val moduleManger = ModuleManager.getInstance(project)
    val module = moduleManger.findModuleByName(name!!)
    val modifiableModuleModel = moduleManger.getModifiableModel()
    WriteAction.runAndWait<RuntimeException> {
      try {
        modifiableModuleModel.disposeModule(module!!)
      }
      finally {
        modifiableModuleModel.commit()
      }
    }
  }

  private fun setModuleNameAndRoot(name: String, root: String) {
    myBuilder!!.name = name
    myBuilder!!.moduleFilePath = "$root/$name.iml"
    myBuilder!!.setContentEntryPath(root)
  }

  private fun setAggregatorProject(pom: VirtualFile?) {
    myBuilder!!.aggregatorProject = if (pom == null) null else projectsManager.findProject(pom)
  }

  private fun setParentProject(pom: VirtualFile) {
    myBuilder!!.parentProject = projectsManager.findProject(pom)
  }

  private fun setInheritedOptions(groupId: Boolean, version: Boolean) {
    myBuilder!!.setInheritedOptions(groupId, version)
  }

  private fun setArchetype(archetype: MavenArchetype) {
    myBuilder!!.archetype = archetype
  }

  private suspend fun createNewModule(id: MavenId) {
    myBuilder!!.projectId = id

    waitForImportWithinTimeout {
      writeAction {
        val model = ModuleManager.getInstance(project).getModifiableModel()
        myBuilder!!.createModule(model)
        model.commit()
      }
    }
  }
}
