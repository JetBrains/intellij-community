/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertExcludes
import com.intellij.maven.testFramework.fixtures.assertGeneratedSources
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.awaitConfiguration
import com.intellij.maven.testFramework.fixtures.createModule
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createStdProjectFolders
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.updateAllProjectsFullSync
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.importing.MavenEventsTestHelper
import org.jetbrains.idea.maven.importing.MavenProjectImporter.Companion.updateTargetFolders
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.importing.MavenRootModelAdapterLegacyImpl
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenFoldersUpdatingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  // Forwarders to keep the legacy bodies one-to-one (these were inherited members of the base test class).
  private val projectsTree get() = maven.projectsTree
  private fun createStdProjectFolders(subdir: String = "") = maven.createStdProjectFolders(subdir)
  private fun createModule(name: String) = maven.createModule(name)
  private suspend fun updateAllProjectsFullSync() = maven.updateAllProjectsFullSync()
  private fun assertSources(moduleName: String, vararg expectedSources: String) = maven.assertSources(moduleName, *expectedSources)
  private fun assertDefaultResources(moduleName: String, vararg additionalSources: String) = maven.assertDefaultResources(moduleName, *additionalSources)
  private fun assertTestSources(moduleName: String, vararg expectedSources: String) = maven.assertTestSources(moduleName, *expectedSources)
  private fun assertDefaultTestResources(moduleName: String, vararg additionalSources: String) = maven.assertDefaultTestResources(moduleName, *additionalSources)
  private fun assertExcludes(moduleName: String, vararg expectedExcludes: String) = maven.assertExcludes(moduleName, *expectedExcludes)
  private fun assertGeneratedSources(moduleName: String, vararg expectedSources: String) = maven.assertGeneratedSources(moduleName, *expectedSources)

    @Test
  fun testUpdatingExternallyCreatedFolders() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.projectRoot.getChildren() // make sure fs is cached

    File(maven.projectRoot.getPath(), "target/foo").mkdirs()
    File(maven.projectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs()
    updateTargetFolders()

    assertExcludes("project", "target")
    assertGeneratedSources("project", "target/generated-sources/xxx")
  }

  @Test
  fun testIgnoreTargetFolder() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    File(maven.projectRoot.getPath(), "target/classes").mkdirs()
    updateTargetFolders()

    assertExcludes("project", "target")
    maven.projectRoot.refresh(false, true)
    val target = maven.projectRoot.findChild("target")
    assertNotNull(target)
    if (!Registry.`is`("ide.hide.excluded.files")) {
      assertTrue(VcsIgnoreManager.getInstance(maven.project).isPotentiallyIgnoredFile(target!!))
    }
  }

  @Test
  fun testUpdatingFoldersForAllTheProjects() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.importProjectAsync()

    assertExcludes("m1", "target")
    assertExcludes("m2", "target")

    File(maven.projectRoot.getPath(), "m1/target/foo/z").mkdirs()
    File(maven.projectRoot.getPath(), "m1/target/generated-sources/xxx/z").mkdirs()
    File(maven.projectRoot.getPath(), "m2/target/bar").mkdirs()
    File(maven.projectRoot.getPath(), "m2/target/generated-sources/yyy/z").mkdirs()

    updateTargetFolders()

    assertExcludes("m1", "target")
    assertGeneratedSources("m1", "target/generated-sources/xxx")

    assertExcludes("m2", "target")
    assertGeneratedSources("m2", "target/generated-sources/yyy")
  }

  @Test
  fun testDoesNotTouchSourceFolders() = runBlocking {
    createStdProjectFolders()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertSources("project", "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project", "src/test/java")
    assertDefaultTestResources("project")

    updateTargetFolders()

    assertSources("project", "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project", "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testDoesNotExcludeRegisteredSources() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    File(maven.projectRoot.getPath(), "target/foo").mkdirs()
    val sourceDir = File(maven.projectRoot.getPath(), "target/src")
    sourceDir.mkdirs()

    edtWriteAction {
      val adapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(
        projectsTree.findProject(maven.projectPom)!!,
        maven.getModule("project"),
        ProjectDataManager.getInstance().createModifiableModelsProvider(maven.project)))
      adapter.addSourceFolder(sourceDir.path, JavaSourceRootType.SOURCE)
      adapter.rootModel.commit()
    }


    updateTargetFolders()

    assertSources("project", "src/main/java")
    assertExcludes("project", "target")
  }

  @Test
  fun testDoesNothingWithNonMavenModules() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createModule("userModule")
    updateTargetFolders() // shouldn't throw exceptions
  }

  @Test
  fun testDoNotUpdateOutputFoldersWhenUpdatingExcludedFolders() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    edtWriteAction {
      val adapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(
        projectsTree.findProject(maven.projectPom)!!,
        maven.getModule("project"),
        ProjectDataManager.getInstance().createModifiableModelsProvider(maven.project)))
        adapter.useModuleOutput(File(maven.projectRoot.getPath(), "target/my-classes").path,
                                File(maven.projectRoot.getPath(), "target/my-test-classes").path)
      adapter.rootModel.commit()
    }

    updateTargetFolders()

    val rootManager = ModuleRootManager.getInstance(maven.getModule("project"))
    val compiler = rootManager.getModuleExtension(CompilerModuleExtension::class.java)
    assertTrue(compiler.getCompilerOutputUrl()!!.endsWith("my-classes"), compiler.getCompilerOutputUrl())
    assertTrue(compiler.getCompilerOutputUrlForTests()!!.endsWith("my-test-classes"), compiler.getCompilerOutputUrlForTests())
  }

  @Test
  fun testDoNotCommitIfFoldersWasNotChanged() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val count = intArrayOf(0)
    maven.project.getMessageBus().connect().subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        count[0]++
      }
    })

    updateTargetFolders()
    assertEquals(0, count[0])
  }

  @Test
  fun testCommitOnlyOnceForAllModules() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())

    maven.importProjectAsync()

    val eventsTestHelper = MavenEventsTestHelper()
    eventsTestHelper.setUp(maven.project)
    try {
      updateTargetFolders()

      maven.awaitConfiguration()
      eventsTestHelper.assertRootsChanged(0)
      eventsTestHelper.assertWorkspaceModelChanges(0)

      // let's add some generated folders, what should be picked up on updateTargetFolders
      val files = listOf(
        Paths.get(maven.projectRoot.path, "target", "generated-sources", "foo", "z"),
        Paths.get(maven.projectRoot.path, "m1", "target", "generated-sources", "bar", "z"),
        Paths.get(maven.projectRoot.path, "m2", "target", "generated-sources", "baz", "z")
      )

      files.forEach {
        Files.createDirectories(it)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it)
      }

      maven.awaitConfiguration()
      eventsTestHelper.assertRootsChanged(3)
      eventsTestHelper.assertWorkspaceModelChanges(0)

      updateTargetFolders()

      maven.awaitConfiguration()
      eventsTestHelper.assertRootsChanged(1)
      eventsTestHelper.assertWorkspaceModelChanges(1)
    }
    finally {
      eventsTestHelper.tearDown()
    }
  }

  @Test
  fun testMarkSourcesAsGeneratedOnReImport() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    File(maven.projectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs()
    updateTargetFolders()

    assertGeneratedSources("project", "target/generated-sources/xxx")

    ModuleRootModificationUtil.updateModel(maven.getModule("project")) { model: ModifiableRootModel ->
      val folders = model.contentEntries[0].getSourceFolders()
      val generated = folders.find { it.getUrl().endsWith("target/generated-sources/xxx") }
      assertNotNull(generated, "Generated folder not found")

      val properties = generated!!.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES)
      assertNotNull(properties)
      properties!!.isForGeneratedSources = false
    }
    assertGeneratedSources("project")

    // incremental sync doesn't update module if effective pom dependencies haven't changed
    updateAllProjectsFullSync()
    assertGeneratedSources("project", "target/generated-sources/xxx")
  }

  private suspend fun updateTargetFolders() {
    updateTargetFolders(maven.project)
  }
}
