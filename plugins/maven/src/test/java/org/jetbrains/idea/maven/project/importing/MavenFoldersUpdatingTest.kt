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

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.maven.importing.MavenEventsTestHelper
import org.jetbrains.idea.maven.importing.MavenProjectImporter.Companion.tryUpdateTargetFolders
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.importing.MavenRootModelAdapterLegacyImpl
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.Test
import java.io.File

class MavenFoldersUpdatingTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testUpdatingExternallyCreatedFolders() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    myProjectRoot.getChildren() // make sure fs is cached

    File(myProjectRoot.getPath(), "target/foo").mkdirs()
    File(myProjectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs()
    updateTargetFolders()

    assertExcludes("project", "target")
    assertGeneratedSources("project", "target/generated-sources/xxx")

    assertNull(myProjectRoot.findChild("target"))
  }

  @Test
  fun testIgnoreTargetFolder() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    File(myProjectRoot.getPath(), "target/classes").mkdirs()
    updateTargetFolders()

    assertExcludes("project", "target")
    myProjectRoot.refresh(false, true)
    val target = myProjectRoot.findChild("target")
    assertNotNull(target)
    if (!Registry.`is`("ide.hide.excluded.files")) {
      assertTrue(VcsIgnoreManager.getInstance(myProject).isPotentiallyIgnoredFile(target!!))
    }
  }

  @Test
  fun testUpdatingFoldersForAllTheProjects() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())

    importProject()

    assertExcludes("m1", "target")
    assertExcludes("m2", "target")

    File(myProjectRoot.getPath(), "m1/target/foo/z").mkdirs()
    File(myProjectRoot.getPath(), "m1/target/generated-sources/xxx/z").mkdirs()
    File(myProjectRoot.getPath(), "m2/target/bar").mkdirs()
    File(myProjectRoot.getPath(), "m2/target/generated-sources/yyy/z").mkdirs()

    updateTargetFolders()

    assertExcludes("m1", "target")
    assertGeneratedSources("m1", "target/generated-sources/xxx")

    assertExcludes("m2", "target")
    assertGeneratedSources("m2", "target/generated-sources/yyy")
  }

  @Test
  fun testDoesNotTouchSourceFolders() {
    createStdProjectFolders()
    importProject("""
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
  fun testDoesNotExcludeRegisteredSources() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    File(myProjectRoot.getPath(), "target/foo").mkdirs()
    val sourceDir = File(myProjectRoot.getPath(), "target/src")
    sourceDir.mkdirs()

    ApplicationManager.getApplication().runWriteAction {
      val adapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(
        projectsTree.findProject(myProjectPom)!!,
        getModule("project"),
        ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)))
      adapter.addSourceFolder(sourceDir.path, JavaSourceRootType.SOURCE)
      adapter.rootModel.commit()
    }


    updateTargetFolders()

    if (supportsKeepingManualChanges()) {
      assertSources("project", "target/src")
    }
    else {
      assertSources("project", "src/main/java")
    }
    assertExcludes("project", "target")
  }

  @Test
  fun testDoesNothingWithNonMavenModules() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createModule("userModule")
    updateTargetFolders() // shouldn't throw exceptions
  }

  @Test
  fun testDoNotUpdateOutputFoldersWhenUpdatingExcludedFolders() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    ApplicationManager.getApplication().runWriteAction {
      val adapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(
        projectsTree.findProject(myProjectPom)!!,
        getModule("project"),
        ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)))
      adapter.useModuleOutput(File(myProjectRoot.getPath(), "target/my-classes").path,
                              File(myProjectRoot.getPath(), "target/my-test-classes").path)
      adapter.rootModel.commit()
    }


    updateTargetFolders()

    val rootManager = ModuleRootManager.getInstance(getModule("project"))
    val compiler = rootManager.getModuleExtension(CompilerModuleExtension::class.java)
    assertTrue(compiler.getCompilerOutputUrl(), compiler.getCompilerOutputUrl()!!.endsWith("my-classes"))
    assertTrue(compiler.getCompilerOutputUrlForTests(), compiler.getCompilerOutputUrlForTests()!!.endsWith("my-test-classes"))
  }

  @Test
  fun testDoNotCommitIfFoldersWasNotChanged() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val count = intArrayOf(0)
    myProject.getMessageBus().connect().subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        count[0]++
      }
    })

    updateTargetFolders()
    assertEquals(if (isWorkspaceImport) 0 else 1, count[0])
  }

  @Test
  fun testCommitOnlyOnceForAllModules() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())

    importProject()

    val eventsTestHelper = MavenEventsTestHelper()
    eventsTestHelper.setUp(myProject)
    try {
      updateTargetFolders()
      eventsTestHelper.assertRootsChanged(if (isWorkspaceImport) 0 else 1)
      eventsTestHelper.assertWorkspaceModelChanges(if (isWorkspaceImport) 0 else 1)

      // let's add some generated folders, what should be picked up on updateTargetFolders
      File(myProjectRoot.getPath(), "target/generated-sources/foo/z").mkdirs()
      File(myProjectRoot.getPath(), "m1/target/generated-sources/bar/z").mkdirs()
      File(myProjectRoot.getPath(), "m2/target/generated-sources/baz/z").mkdirs()
      updateTargetFolders()

      eventsTestHelper.assertRootsChanged(1)
      eventsTestHelper.assertWorkspaceModelChanges(1)
    }
    finally {
      eventsTestHelper.tearDown()
    }
  }

  @Test
  fun testMarkSourcesAsGeneratedOnReImport() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    File(myProjectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs()
    updateTargetFolders()

    assertGeneratedSources("project", "target/generated-sources/xxx")

    ModuleRootModificationUtil.updateModel(getModule("project")) { model: ModifiableRootModel ->
      val folders = model.getContentEntries()[0].getSourceFolders()
      val generated = ContainerUtil.find(folders) { it: SourceFolder -> it.getUrl().endsWith("target/generated-sources/xxx") }
      assertNotNull("Generated folder not found", generated)

      val properties = generated.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES)
      assertNotNull(properties)
      properties!!.isForGeneratedSources = false
    }
    assertGeneratedSources("project")

    importProject()
    assertGeneratedSources("project", "target/generated-sources/xxx")
  }

  private fun updateTargetFolders() {
    tryUpdateTargetFolders(myProject)
  }
}
