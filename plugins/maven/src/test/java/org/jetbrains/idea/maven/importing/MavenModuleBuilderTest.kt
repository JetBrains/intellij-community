// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.moduleTag
import com.intellij.maven.testFramework.fixtures.modulesTag
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaModuleBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource


@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModuleBuilderTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private lateinit var myFixture: MavenProjectJDKTestFixture
  private lateinit var myBuilder: MavenJavaModuleBuilder

  @BeforeEach
  fun setUp() {
    myBuilder = MavenJavaModuleBuilder()

    myFixture = MavenProjectJDKTestFixture(maven.project, "MAVEN_TEST_JDK")
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.setUp() })
    })

    setModuleNameAndRoot("module", maven.projectPath.toString())
  }

  public @AfterEach
  fun tearDown() {
    RunAll.runAll(
      {
        EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
          WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { myFixture.tearDown() })

        })
      },
    )
  }

  @Test
  fun testModuleRecreation() = runBlocking {
    maven.assumeMaven3()
    val id = MavenId("org.foo", "module", "1.0")

    createNewModule(id)
    maven.assertModules(id.artifactId!!)
    deleteModule(id.artifactId)
    createNewModule(id)
    maven.assertModules(id.artifactId!!)

    maven.updateAllProjects()
    withContext(Dispatchers.EDT){
      writeIntentReadAction {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
  }

  @Test
  fun testCreatingBlank() = runBlocking {
    val id = MavenId("org.foo", "module", "1.0")
    createNewModule(id)

    val projects = MavenProjectsManager.getInstance(maven.project).getProjects()
    assertEquals(1, projects.size)

    val mavenProject = projects[0]
    assertEquals(id, mavenProject.mavenId)

    maven.assertModules("module")
    MavenProjectsManager.getInstance(maven.project).isMavenizedModule(maven.getModule("module"))
    assertSame(mavenProject, MavenProjectsManager.getInstance(maven.project).findProject(maven.getModule("module")))

    assertNotNull(maven.projectRoot.findFileByRelativePath("src/main/java"))
    assertNotNull(maven.projectRoot.findFileByRelativePath("src/test/java"))

    maven.assertSources("module", "src/main/java")
    maven.assertTestSources("module", "src/test/java")
  }

  @Test
  fun testInheritJdkFromProject() = runBlocking {
    createNewModule(MavenId("org.foo", "module", "1.0"))
    val manager = ModuleRootManager.getInstance(maven.getModule("module"))
    assertTrue(manager.isSdkInherited())
  }

  @Test
  fun testCreatingFromArchetype() = runBlocking {
    setArchetype(MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null))
    val id = MavenId("org.foo", "module", "1.0")
    createNewModule(id)

    val projects = MavenProjectsManager.getInstance(maven.project).projects
    assertEquals(1, projects.size)
    val project = projects[0]
    assertEquals(id, project.mavenId)
    assertTrue(java.nio.file.Files.exists(maven.projectRoot.toNioPath().resolve("src/main/java/org/foo/App.java")))
    assertTrue(java.nio.file.Files.exists(maven.projectRoot.toNioPath().resolve("src/test/java/org/foo/AppTest.java")))

    maven.assertSources("module", "src/main/java")
    maven.assertTestSources("module", "src/test/java")
  }

  @Test
  fun testAddingNewlyCreatedModuleToTheAggregator() = runBlocking {

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "${maven.projectPath}/module")
    setAggregatorProject(maven.projectPom)
    createNewModule(MavenId("org.foo", "module", "1.0"))

    assertEquals("""
      <?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/${maven.modelVersion}"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/${maven.modelVersion} http://maven.apache.org/xsd/maven-${maven.modelVersion}.xsd">
        <modelVersion>${maven.modelVersion}</modelVersion>
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
          <packaging>pom</packaging>
          <${maven.modulesTag}>
              <${maven.moduleTag}>module</${maven.moduleTag}>
          </${maven.modulesTag}>
      </project>
    """.trimIndent(),
                 StringUtil.convertLineSeparators(VfsUtil.loadText(maven.projectPom)))
  }

  @Test
  fun testAddingManagedProjectIfNoArrgerator() = runBlocking {

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(1, maven.projectsManager.state.originalFiles.size)

    setModuleNameAndRoot("module", "${maven.projectPath}/module")
    setAggregatorProject(null)
    createNewModule(MavenId("org.foo", "module", "1.0"))
    maven.projectRoot.findFileByRelativePath("module/pom.xml")

    assertEquals(2, maven.projectsManager.state.originalFiles.size)
  }

  @Test
  fun testDoNotAddManagedProjectIfAddingAsModuleToAggregator() = runBlocking {

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEquals(1, maven.projectsManager.state.originalFiles.size)

    setModuleNameAndRoot("module", "${maven.projectPath}/module")
    setAggregatorProject(maven.projectPom)
    createNewModule(MavenId("org.foo", "module", "1.0"))
    maven.projectRoot.findFileByRelativePath("module/pom.xml")

    assertEquals(1, maven.projectsManager.state.originalFiles.size)
  }

  @Test
  fun testAddingParent() = runBlocking {

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "${maven.projectPath}/module")
    setParentProject(maven.projectPom)
    createNewModule(MavenId("org.foo", "module", "1.0"))
    val sdk = ProjectRootManager.getInstance(maven.project).projectSdk
    val version = JavaSdk.getInstance().getVersion(sdk!!)!!.maxLanguageLevel.feature()

    assertEquals("""
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <parent>
              <groupId>test</groupId>
              <artifactId>project</artifactId>
              <version>1</version>
          </parent>

          <groupId>org.foo</groupId>
          <artifactId>module</artifactId>
          <version>1.0</version>

          <properties>
              <maven.compiler.source>$version</maven.compiler.source>
              <maven.compiler.target>$version</maven.compiler.target>
              <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
          </properties>

      </project>
    """.trimIndent(),
                 VfsUtil.loadText(maven.projectRoot.findFileByRelativePath("module/pom.xml")!!))
  }

  @Test
  fun testAddingParentWithInheritedProperties() = runBlocking {

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "${maven.projectPath}/module")
    setParentProject(maven.projectPom)
    setInheritedOptions(true, true)
    createNewModule(MavenId("org.foo", "module", "1.0"))
    val sdk = ProjectRootManager.getInstance(maven.project).projectSdk
    val version = JavaSdk.getInstance().getVersion(sdk!!)!!.maxLanguageLevel.feature()

    assertEquals("""
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <parent>
              <groupId>test</groupId>
              <artifactId>project</artifactId>
              <version>1</version>
          </parent>

          <artifactId>module</artifactId>

          <properties>
              <maven.compiler.source>$version</maven.compiler.source>
              <maven.compiler.target>$version</maven.compiler.target>
              <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
          </properties>

      </project>
    """.trimIndent(),
                 VfsUtil.loadText(maven.projectRoot.findFileByRelativePath("module/pom.xml")!!))
  }

  @Test
  fun testAddingParentAndInheritWhenGeneratingFromArchetype() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    setModuleNameAndRoot("module", "${maven.projectPath}/module")
    setParentProject(maven.projectPom)
    setInheritedOptions(true, true)
    setArchetype(MavenArchetype("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null))
    createNewModule(MavenId("org.foo", "module", "1.0"))

    val expectedModulePom = """
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>
    <artifactId>module</artifactId>
    <packaging>jar</packaging>
    <name>module</name>
    <url>http://maven.apache.org</url>
    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>3.8.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>

""".trimIndent()

    val actualModulePom = VfsUtil.loadText(maven.projectRoot.findFileByRelativePath("module/pom.xml")!!)

    assertEquals(expectedModulePom.normalizeLineEndings(), actualModulePom.normalizeLineEndings())
  }

  private fun String.normalizeLineEndings(): String {
    return this.replace("\r\n", "\n")
  }

  private fun deleteModule(name: String?) {
    val moduleManger = ModuleManager.getInstance(maven.project)
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
    myBuilder.name = name
    myBuilder.moduleFilePath = "$root/$name.iml"
    myBuilder.setContentEntryPath(root)
  }

  private fun setAggregatorProject(pom: VirtualFile?) {
    myBuilder.aggregatorProject = if (pom == null) null else maven.projectsManager.findProject(pom)
  }

  private fun setParentProject(pom: VirtualFile) {
    myBuilder.parentProject = maven.projectsManager.findProject(pom)
  }

  private fun setInheritedOptions(groupId: Boolean, version: Boolean) {
    myBuilder.setInheritedOptions(groupId, version)
  }

  private fun setArchetype(archetype: MavenArchetype) {
    myBuilder.archetype = archetype
  }

  private suspend fun createNewModule(id: MavenId) {
    myBuilder.projectId = id

    if (myBuilder.archetype != null) {
      myBuilder.propertiesToCreateByArtifact = LinkedHashMap<String, String>().apply {
        id.groupId?.let { put("groupId", it) }
        id.artifactId?.let { put("artifactId", it) }
        id.version?.let { put("version", it) }
        put("archetypeGroupId", myBuilder.archetype.groupId)
        put("archetypeArtifactId", myBuilder.archetype.artifactId)
        put("archetypeVersion", myBuilder.archetype.version)
        myBuilder.archetype.repository?.let { repository ->
          put("archetypeRepository", repository)
        }
      }
    }

    maven.waitForImportWithinTimeout {
      val model = ModuleManager.getInstance(maven.project).getModifiableModel()
      myBuilder.createModule(model)
      backgroundWriteAction {
        model.commit()
      }
    }
  }
}
