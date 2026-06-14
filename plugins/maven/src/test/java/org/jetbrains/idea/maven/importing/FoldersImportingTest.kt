/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.arrayOfNotNull
import com.intellij.maven.testFramework.fixtures.assertContentRootResources
import com.intellij.maven.testFramework.fixtures.assertContentRootSources
import com.intellij.maven.testFramework.fixtures.assertContentRootTestResources
import com.intellij.maven.testFramework.fixtures.assertContentRootTestSources
import com.intellij.maven.testFramework.fixtures.assertContentRoots
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertExcludes
import com.intellij.maven.testFramework.fixtures.assertModuleOutput
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertProjectOutput
import com.intellij.maven.testFramework.fixtures.assertResources
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestResources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.assumeOnLocalEnvironmentOnly
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.createProjectSubDirsWithFile
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.createStdProjectFolders
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.isModel410
import com.intellij.maven.testFramework.fixtures.mavenImporterSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.parentPath
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.resolveFoldersAndImport
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateAllProjectsFullSync
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ArrayUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenPathWrapper
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class FoldersImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  @BeforeEach
  fun setUpExternalChanges() {
    maven.projectsManager.listenForExternalChanges()
  }

  @Test
  fun testSimpleProjectStructure() = runBlocking {
    maven.createStdProjectFolders()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertContentRoots("project", maven.projectPath)
    maven.assertSources("project", "src/main/java")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project", "src/test/java")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testInvalidProjectHasContentRoot() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1
                       """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project")
    maven.assertContentRoots("project", maven.projectPath)
  }

  @Test
  fun testDoNotResetFoldersAfterResolveIfProjectIsInvalid() = runBlocking {
    maven.createStdProjectFolders()
    maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>""")
    maven.assertModules("project")
    maven.assertSources("project", "src/main/java")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project", "src/test/java")
    maven.assertDefaultTestResources("project")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension>
                             <groupId>xxx</groupId>
                             <artifactId>xxx</artifactId>
                             <version>xxx</version>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project")
    maven.assertSources("project", "src/main/java")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project", "src/test/java")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testDoesNotResetUserFolders() = runBlocking {
    val dir1 = maven.createProjectSubDir("userSourceFolder")
    val dir2 = maven.createProjectSubDir("userExcludedFolder")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    edtWriteAction {
      val adapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(
        maven.projectsTree.findProject(maven.projectPom)!!,
        maven.getModule("project"),
        ProjectDataManager.getInstance().createModifiableModelsProvider(maven.project)))
      adapter.addSourceFolder(dir1.getPath(), JavaSourceRootType.SOURCE)
      adapter.addExcludedFolder(dir2.getPath())
      adapter.rootModel.commit()
    }
    maven.assertSources("project", "userSourceFolder", "src/main/java")
    maven.assertExcludes("project", "target", "userExcludedFolder")

    // incremental sync doesn't support updating source folders if effective pom dependencies haven't changed
    maven.updateAllProjectsFullSync()
    maven.assertSources("project", "src/main/java")
    maven.assertExcludes("project", "target", "userExcludedFolder")
    maven.resolveFoldersAndImport()
    maven.assertSources("project", "src/main/java")
    maven.assertExcludes("project", "target", "userExcludedFolder")
  }

  @Test
  fun testClearParentAndSubFoldersOfNewlyImportedFolders() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project", "src/main/java")
    maven.assertDefaultResources("project")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src</sourceDirectory>
                       </build>
                       """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertSources("project", "src")
    maven.createProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.updateAllProjects()
    maven.assertSources("project", "src/main/java")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testSourceFoldersOnReimport() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createProjectSubDirs("src1", "src2")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>src1</sourceDirectory>
                    </build>
                    """.trimIndent())
    maven.assertSources("project", "src1")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src2</sourceDirectory>
                       </build>
                       """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertSources("project", "src2")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src1</sourceDirectory>
                       </build>
                       """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertSources("project", "src1")
  }

  @Test
  fun testCustomSourceFolders() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("src", "test", "res1", "res2", "testRes1", "testRes2")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>src</sourceDirectory>
                      <testSourceDirectory>test</testSourceDirectory>
                      <resources>
                        <resource><directory>res1</directory></resource>
                        <resource><directory>res2</directory></resource>
                      </resources>
                      <testResources>
                        <testResource><directory>testRes1</directory></testResource>
                        <testResource><directory>testRes2</directory></testResource>
                      </testResources>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertContentRoots("project", maven.projectPath)
    maven.assertSources("project", "src")
    maven.assertResources("project", "res1", "res2")
    maven.assertTestSources("project", "test")
    maven.assertTestResources("project", "testRes1", "testRes2")
  }

  @Test
  fun testCustomSourceFoldersOutsideOfContentRoot() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("m",
                         "src",
                         "test",
                         "res",
                         "testRes")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <build>
        <sourceDirectory>../src</sourceDirectory>
        <testSourceDirectory>../test</testSourceDirectory>
        <resources>
          <resource><directory>../res</directory></resource>
        </resources>
        <testResources>
          <testResource><directory>../testRes</directory></testResource>
        </testResources>
      </build>
      """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project", "m")
    maven.assertContentRoots("project",
                       maven.projectPath)
    maven.assertContentRoots("m",
                       "${maven.projectPath}/m",
                       "${maven.projectPath}/src",
                       "${maven.projectPath}/test",
                       "${maven.projectPath}/res",
                       "${maven.projectPath}/testRes")
  }

  @Test
  fun testSourceFolderPointsToProjectRoot() = runBlocking {
    maven.createStdProjectFolders()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${'$'}{basedir}</sourceDirectory>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertContentRoots("project", maven.projectPath)
    maven.assertSources("project", "")
    maven.assertTestSources("project")
    maven.assertResources("project")
    maven.assertTestResources("project")
  }

  @Test
  fun testResourceFolderPointsToProjectRoot() = runBlocking {
    maven.createStdProjectFolders()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource><directory>${'$'}{basedir}</directory></resource>
                      </resources>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertContentRoots("project", maven.projectPath)
    maven.assertSources("project", "src/main/java")
    maven.assertTestSources("project", "src/test/java")
    maven.assertResources("project")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testResourceFolderPointsToProjectRootParent() = runBlocking {
    maven.createStdProjectFolders()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource><directory>${'$'}{basedir}/..</directory></resource>
                      </resources>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertContentRoots("project", maven.projectPath)
    maven.assertSources("project", "src/main/java")
    maven.assertTestSources("project", "src/test/java")
    maven.assertResources("project")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSources() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubFile("target/generated-sources/src1/com/A.java", "package com; class A {}")
    maven.createProjectSubFile("target/generated-sources/src2/com/B.java", "package com; class B {}")
    maven.createProjectSubFile("target/generated-test-sources/test1/com/test/A.java", "package com.test; class A {}")
    maven.createProjectSubFile("target/generated-test-sources/test2/com/test/B.java", "package com.test; class B {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources/src1",
                  "target/generated-sources/src2")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/test1",
                      "target/generated-test-sources/test2")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSourcesInPerSourceTypeModules() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubFile("target/generated-sources/src1/com/A.java", "package com; class A {}")
    maven.createProjectSubFile("target/generated-sources/src2/com/B.java", "package com; class B {}")
    maven.createProjectSubFile("target/generated-test-sources/test1/com/test/A.java", "package com.test; class A {}")
    maven.createProjectSubFile("target/generated-test-sources/test2/com/test/B.java", "package com.test; class B {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <maven.compiler.source>8</maven.compiler.source>
                      <maven.compiler.target>8</maven.compiler.target>
                      <maven.compiler.testSource>11</maven.compiler.testSource>
                      <maven.compiler.testTarget>11</maven.compiler.testTarget>
                    </properties>
                    """.trimIndent())
    maven.assertModules("project", "project.main", "project.test")
    maven.assertContentRoots("project", maven.projectPath)
    maven.assertSources("project")
    maven.assertResources("project")
    maven.assertTestSources("project")
    maven.assertTestResources("project")
    maven.assertExcludes("project", "target")

    val mainSources = arrayOfNotNull(
      "${maven.projectPath}/src/main/java",
      "${maven.projectPath}/target/generated-sources/src1",
      "${maven.projectPath}/target/generated-sources/src2"
    )
    val testSources = arrayOfNotNull(
      "${maven.projectPath}/src/test/java",
      "${maven.projectPath}/target/generated-test-sources/test1",
      "${maven.projectPath}/target/generated-test-sources/test2"
    )

    maven.assertSources("project.main", *mainSources)
    maven.assertDefaultResources("project.main")
    maven.assertTestSources("project.main")
    maven.assertTestResources("project.main")

    maven.assertSources("project.test")
    maven.assertResources("project.test")
    maven.assertTestSources("project.test", *testSources)
    maven.assertDefaultTestResources("project.test")
  }

  @Test
  fun testContentRootOutsideOfModuleDirInPerSourceTypeImport() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>../custom-sources</sourceDirectory>
                      </build>
                      """.trimIndent())
    maven.createProjectSubFile("custom-sources/com/CustomSource.java", "package com; class CustomSource {}")
    maven.createProjectSubFile("m1/src/main/resources/test.txt", "resource")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                      <maven.compiler.source>8</maven.compiler.source>
                      <maven.compiler.target>8</maven.compiler.target>
                      <maven.compiler.testSource>11</maven.compiler.testSource>
                      <maven.compiler.testTarget>11</maven.compiler.testTarget>
                    </properties>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    maven.assertModules("project",
                  maven.mn("project", "m1"),
                  maven.mn("project", "m1.main"),
                  maven.mn("project", "m1.test"))
    maven.assertSources("m1.main", "../custom-sources")
    maven.assertDefaultResources("m1.main")
  }

  @Test
  fun testAddingExistingGeneratedSources2() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    maven.createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSources3() = runBlocking {
    maven.createStdProjectFolders()
    MavenProjectsManager.getInstance(maven.project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.SUBFOLDER)
    maven.createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources/com")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testOverrideAnnotationSources() = runBlocking {
    maven.createStdProjectFolders()
    MavenProjectsManager.getInstance(maven.project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.GENERATED_SOURCE_FOLDER)
    maven.createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}")
    maven.createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testOverrideAnnotationSourcesWhenAutodetect() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    MavenProjectsManager.getInstance(maven.project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.AUTODETECT)
    maven.createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}")
    maven.createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testOverrideTestAnnotationSourcesWhenAutodetect() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    MavenProjectsManager.getInstance(maven.project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.AUTODETECT)
    maven.createProjectSubFile("target/generated-test-sources/com/A.java", "package com; class A {}")
    maven.createProjectSubFile("target/generated-test-sources/test-annotations/com/B.java", "package com; class B {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project", "src/main/java")
    maven.assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testIgnoreGeneratedSources() = runBlocking {
    maven.createStdProjectFolders()
    MavenProjectsManager.getInstance(maven.project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.IGNORE)
    maven.createProjectSubFile("target/generated-sources/annotations/A.java", "package com; class A {}")
    maven.createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project", "src/main/java")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSources4() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    maven.createProjectSubFile("target/generated-sources/A1/B1/com/A1.java", "package com; class A1 {}")
    maven.createProjectSubFile("target/generated-sources/A1/B2/com/A2.java", "package com; class A2 {}")
    maven.createProjectSubFile("target/generated-sources/A2/com/A3.java", "package com; class A3 {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources/A1/B1",
                  "target/generated-sources/A1/B2",
                  "target/generated-sources/A2")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSources5() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    maven.createProjectSubFile("target/generated-sources/A1/B1/com/A1.java", "package com; class A1 {}")
    maven.createProjectSubFile("target/generated-sources/A2.java", "class A2 {}")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSourcesWithCustomTargetDir() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirsWithFile("targetCustom/generated-sources/src",
                                 "targetCustom/generated-test-sources/test")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>targetCustom</directory>
                    </build>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "targetCustom/generated-sources/src")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project",
                      "src/test/java",
                      "targetCustom/generated-test-sources/test")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testIgnoringFilesRightUnderGeneratedSources() = runBlocking {
    maven.createProjectSubFile("target/generated-sources/f.txt")
    maven.createProjectSubFile("target/generated-test-sources/f.txt")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project", "src/main/java")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project", "src/test/java")
    maven.assertDefaultTestResources("project")
    maven.assertExcludes("project", "target")
  }

  @Test
  fun testExcludingOutputDirectories() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertExcludes("project", "target")
    maven.assertModuleOutput("project",
                       "${maven.projectPath}/target/classes",
                       "${maven.projectPath}/target/test-classes")
  }

  @Test
  fun testExcludingOutputDirectoriesIfProjectOutputIsUsed() = runBlocking {
    maven.mavenImporterSettings.isUseMavenOutput = false
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>foo</directory>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertExcludes("project", "foo")
    maven.assertProjectOutput("project")
  }

  @Test
  fun testUnloadedModules() = runBlocking {
    maven.createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>")
    maven.createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>")
    maven.createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>")
    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")
    getInstance(maven.project).setUnloadedModulesSync(listOf("m2"))
    maven.assertModules("project", "m1")
    maven.importProjectAsync()
    maven.assertModules("project", "m1")
    val m2 = getInstance(maven.project).getUnloadedModuleDescription("m2")
    assertNotNull(m2)
    assertEquals("m2", m2!!.getName())
  }

  @Test
  fun testExcludingCustomOutputDirectories() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>targetCustom</directory>
                      <outputDirectory>outputCustom</outputDirectory>
                      <testOutputDirectory>testCustom</testOutputDirectory>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertExcludes("project",
                   "outputCustom",
                   "targetCustom",
                   "testCustom")
    maven.assertModuleOutput("project",
                       "${maven.projectPath}/outputCustom",
                       "${maven.projectPath}/testCustom")
  }

  @Test
  fun testExcludingCustomOutputUnderTargetUsingStandardVariable() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <outputDirectory>${'$'}{project.build.directory}/outputCustom</outputDirectory>
                      <testOutputDirectory>${'$'}{project.build.directory}/testCustom</testOutputDirectory>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertExcludes("project", "target")
    maven.assertModuleOutput("project",
                       "${maven.projectPath}/target/outputCustom",
                       "${maven.projectPath}/target/testCustom")
  }

  @Test
  fun testDoNotExcludeExcludeOutputDirectoryWhenItPointstoRoot() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <outputDirectory>.</outputDirectory>
                      <testOutputDirectory>.</testOutputDirectory>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertExcludes("project",
                   "target")
    maven.assertModuleOutput("project",
                       maven.projectPath,
                       maven.projectPath)
  }

  @Test
  fun testOutputDirsOutsideOfContentRoot() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>../target</directory>
                      <outputDirectory>../target/classes</outputDirectory>
                      <testOutputDirectory>../target/test-classes</testOutputDirectory>
                    </build>
                    """.trimIndent())
    val targetPath = "${maven.parentPath}/target"
    val targetUrl = MavenPathWrapper(targetPath).toUrl().url
    maven.assertContentRoots("project", maven.projectPath)
    maven.assertModuleOutput("project",
                       "${maven.parentPath}/target/classes",
                       "${maven.parentPath}/target/test-classes")
  }

  @Test
  fun testCustomPomFileNameDefaultContentRoots() = runBlocking {
    maven.createProjectSubFile("m1/customName.xml", maven.createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <build>
          <sourceDirectory>sources</sourceDirectory>
          <testSourceDirectory>tests</testSourceDirectory>
        </build>
        """.trimIndent()))
    File(maven.projectRoot.getPath(), "m1/sources").mkdirs()
    File(maven.projectRoot.getPath(), "m1/tests").mkdirs()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1/customName.xml</module>
                    </modules>
                    """.trimIndent())
    maven.assertContentRoots(maven.mn("project", "m1"), "${maven.projectPath}/m1")
  }

  @Test
  fun testCustomPomFileNameCustomContentRoots() = runBlocking {
    maven.createProjectSubFile("m1/pom.xml", maven.createPomXml(
      """
        <artifactId>m1-pom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))
    maven.createProjectSubFile("m1/custom.xml", maven.createPomXml(
      """
        <artifactId>m1-custom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <build>
          <resources><resource><directory>sources/resources</directory></resource></resources>
          <sourceDirectory>sources</sourceDirectory>
          <testSourceDirectory>tests</testSourceDirectory>
        </build>
        """.trimIndent()))
    maven.createStdProjectFolders("m1")
    maven.createProjectSubDirs("m1/sources/resources",
                         "m1/tests")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1</module>
                      <module>m1/custom.xml</module>
                    </modules>
                    """.trimIndent())
    val m1_pom_module = maven.mn("project", "m1-pom")
    val m1_custom_module = maven.mn("project", "m1-custom")
    maven.assertModules("project", m1_pom_module, m1_custom_module)
    val m1_pom_root = "${maven.projectPath}/m1"
    maven.assertContentRoots(m1_pom_module, m1_pom_root)
    maven.assertContentRootSources(m1_pom_module, m1_pom_root, "src/main/java")
    val expectedResources = ArrayList<String>()
    expectedResources.add("src/main/resources")
    if (maven.isModel410()) {
      expectedResources.add("src/main/resources-filtered")
    }
    maven.assertContentRootResources(m1_pom_module, m1_pom_root, *ArrayUtil.toStringArray(expectedResources))
    maven.assertContentRootTestSources(m1_pom_module, m1_pom_root, "src/test/java")
    val expectedTestResources = ArrayList<String>()
    expectedTestResources.add("src/test/resources")
    if (maven.isModel410()) {
      expectedTestResources.add("src/test/resources-filtered")
    }
    maven.assertContentRootTestResources(m1_pom_module, m1_pom_root, *ArrayUtil.toStringArray(expectedTestResources))
    val m1_custom_sources_root = "${maven.projectPath}/m1/sources"
    val m1_custom_tests_root = "${maven.projectPath}/m1/tests"
    val m1_standard_test_resources = "${maven.projectPath}/m1/src/test/resources"
    val m1_standard_test_resources_list = ArrayList<String>()

    // [anton] The next folder doesn't look correct, as it intersects with 'pom.xml' module folders,
    // but I'm testing the behavior as is in order to preserve it in the new Workspace import
    m1_standard_test_resources_list.add(m1_standard_test_resources)
    if (maven.isModel410()) {
      m1_standard_test_resources_list.add("$m1_standard_test_resources-filtered")
    }
    maven.assertSources(m1_custom_module, m1_custom_sources_root)
    maven.assertResources(m1_custom_module)
    maven.assertTestSources(m1_custom_module, m1_custom_tests_root)
    maven.assertTestResources(m1_custom_module, *m1_standard_test_resources_list.toTypedArray())
  }

  @Test
  fun testContentRootOutsideOfModuleDir() = runBlocking {
    maven.createProjectSubFile("m1/pom.xml", maven.createPomXml(
      """
        <artifactId>m1-pom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <build>
          <sourceDirectory>../pom-sources</sourceDirectory>
        </build>
        """.trimIndent()))
    maven.createProjectSubFile("m1/custom.xml", maven.createPomXml(
      """
        <artifactId>m1-custom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <build>
          <sourceDirectory>../custom-sources</sourceDirectory>
        </build>
        """.trimIndent()))
    File(maven.projectRoot.getPath(), "pom-sources").mkdirs()
    File(maven.projectRoot.getPath(), "custom-sources").mkdirs()
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1</module>
                      <module>m1/custom.xml</module>
                    </modules>
                    """.trimIndent())
    maven.assertModules("project", maven.mn("project", "m1-pom"), maven.mn("project", "m1-custom"))
    maven.assertContentRoots(maven.mn("project", "m1-pom"),
                       "${maven.projectPath}/m1", "${maven.projectPath}/pom-sources")
    maven.assertContentRootSources(maven.mn("project", "m1-pom"), "${maven.projectPath}/m1")
    maven.assertContentRootTestSources(maven.mn("project", "m1-pom"), "${maven.projectPath}/m1", "src/test/java")
    maven.assertContentRootSources(maven.mn("project", "m1-pom"), "${maven.projectPath}/pom-sources", "")
    maven.assertContentRootTestSources(maven.mn("project", "m1-pom"), "${maven.projectPath}/pom-sources")

    // this is not quite correct behavior, since we have both modules (m1-pom and m2-custom) pointing at the same folders
    // (Though, it somehow works in IJ, and it's a rare case anyway).
    // The assertions are only to make sure the behavior is 'stable'. Should be updates once the behavior changes intentionally
    maven.assertSources("m1-custom", "${maven.projectPath}/custom-sources")
    maven.assertTestSources("m1-custom", "${maven.projectPath}/m1/src/test/java")
    maven.assertDefaultResources("m1-custom")
    maven.assertDefaultTestResources("m1-custom")
  }

  @Test
  fun testDoesNotExcludeGeneratedSourcesUnderTargetDir() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirsWithFile("target/foo",
                                 "target/bar",
                                 "target/generated-sources/baz",
                                 "target/generated-test-sources/bazz")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertExcludes("project", "target")
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources/baz")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/bazz")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testDoesNotExcludeSourcesUnderTargetDir() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("target/src",
                         "target/test",
                         "target/xxx")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>target/src</sourceDirectory>
                      <testSourceDirectory>target/test</testSourceDirectory>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertExcludes("project", "target")
  }

  @Test
  fun testDoesNotExcludeSourcesUnderTargetDirWithProperties() = runBlocking {
    maven.createProjectSubDirs("target/src", "target/xxx")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${'$'}{project.build.directory}/src</sourceDirectory>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertSources("project", "target/src")
    maven.assertExcludes("project", "target")
  }

  @Test
  fun testDoesNotExcludeFoldersWithSourcesUnderTargetDir() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("target/src/main",
                         "target/foo")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>target/src/main</sourceDirectory>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertExcludes("project", "target")
    maven.assertSources("project", "target/src/main")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testDoesNotUnExcludeFoldersOnRemoval() = runBlocking {
    maven.createStdProjectFolders()
    val subDir = maven.createProjectSubDir("target/foo")
    maven.createProjectSubDirsWithFile("target/generated-sources/baz")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertExcludes("project", "target")
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources/baz")
    maven.assertDefaultResources("project")
    edtWriteAction {
      try {
        subDir.delete(this)
      }
      catch (e: IOException) {
        Assertions.fail("Unable to delete the file: " + e.message)
      }
    }
    maven.importProjectAsync()
    maven.assertExcludes("project", "target")
  }

  @Test
  fun testUnexcludeNewSources() = runBlocking {
    maven.createProjectSubDirs("target/foo")
    maven.createProjectSubDirs("target/src")
    maven.createProjectSubDirs("target/test/subFolder")
    maven.importProjectAsync("""
                   <groupId>test</groupId>
                   <artifactId>project</artifactId>
                   <version>1</version>
                   """.trimIndent())
    maven.assertExcludes("project", "target")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>target/src</sourceDirectory>
                         <testSourceDirectory>target/test/subFolder</testSourceDirectory>
                       </build>
                       """.trimIndent())
    maven.importProjectAsync()
    //resolveFoldersAndImport();
    maven.assertSources("project", "target/src")
    maven.assertTestSources("project", "target/test/subFolder")
    maven.assertExcludes("project", "target")
  }

  @Test
  fun testUnexcludeNewSourcesUnderCompilerOutputDir() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createProjectSubDirs("target/classes/src")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertExcludes("project", "target")
    //assertTrue(getCompilerExtension("project").isExcludeOutput());
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>target/classes/src</sourceDirectory>
                       </build>
                       """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertSources("project", "target/classes/src")
    maven.assertExcludes("project", "target")

    //assertFalse(getCompilerExtension("project").isExcludeOutput());
  }

  @Test
  fun testAnnotationProcessorSources() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirsWithFile("target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-test-sources/test-annotations",
                                 "target/generated-test-sources/foo")
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources/annotations",
                  "target/generated-sources/foo")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/foo",
                      "target/generated-test-sources/test-annotations")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testModuleWorkingDirWithMultiplyContentRoots() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>AA</module>
                         <module>BB</module>
                       </modules>
                       """.trimIndent())
    maven.createModulePom("AA", """
      <parent>
              <artifactId>project</artifactId>
              <groupId>test</groupId>
              <version>1</version>
          </parent>
      <artifactId>AA</artifactId>
      """.trimIndent())
    val pomBB = maven.createModulePom("BB", """
      <parent>
              <artifactId>project</artifactId>
              <groupId>test</groupId>
              <version>1</version>
          </parent>
      <artifactId>BB</artifactId>
       <build>
              <testResources>
                  <testResource>
                      <targetPath>${'$'}{project.build.testOutputDirectory}</targetPath>
                      <directory>
                          ${'$'}{project.basedir}/src/test/resources                </directory>
                  </testResource>
                  <testResource>
                      <targetPath>${'$'}{project.build.testOutputDirectory}</targetPath>
                      <directory>
                           ${'$'}{project.basedir}/../AA/src/test/resources                </directory>
                  </testResource>
              </testResources>
          </build>
          """
      .trimIndent()
    )
    maven.createProjectSubDirs("AA/src/test/resources")
    maven.createProjectSubDirs("BB/src/test/resources")
    maven.importProjectAsync()
    val parameters: CommonProgramRunConfigurationParameters = object : CommonProgramRunConfigurationParameters {
      override fun getProject(): Project {
        return project
      }

      override fun setProgramParameters(value: String?) {}
      override fun getProgramParameters(): String? {
        return null
      }

      override fun setWorkingDirectory(value: String?) {}
      override fun getWorkingDirectory(): String? {
        return "\$MODULE_WORKING_DIR$"
      }

      override fun setEnvs(envs: Map<String, String>) {}
      override fun getEnvs(): Map<String, String> {
        return HashMap()
      }

      override fun setPassParentEnvs(passParentEnvs: Boolean) {}
      override fun isPassParentEnvs(): Boolean {
        return false
      }
    }
    maven.assertModules("project", maven.mn("project", "AA"), maven.mn("project", "BB"))
    val workingDir = ProgramParametersUtil.getWorkingDir(parameters, maven.project, maven.getModule(maven.mn("project", "BB")))
    assertEquals(pomBB.canonicalFile!!.getParent().getPath(), workingDir)
  }

  @Test
  fun testExcludeTargetForAggregator() = runBlocking {
    maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.assertModules("project")
    maven.assertExcludes("project", "target")
  }


  @Test
  fun testImportingSourcesTag() = runBlocking {
    maven.assumeMaven4()
    maven.assumeModel_4_1_0("test requires model 4.1.0")
    maven.createProjectSubDirs("my/src", "my/res", "my/testsrc", "my/testres")
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <sources>
            <source>
                <directory>my/src</directory>
                <lang>java</lang>
                <scope>main</scope>
            </source>
            <source>
                <directory>my/res</directory>
                <lang>resources</lang>
                <scope>main</scope>
            </source>
            <source>
                <directory>my/testsrc</directory>
                <lang>java</lang>
                <scope>test</scope>
            </source>
             <source>
                <directory>my/testres</directory>
                <lang>resources</lang>
                <scope>test</scope>
            </source>
        </sources>
      </build>
      """);
    maven.assertModules("project")
    maven.assertSources("project", "my/src")
    maven.assertTestSources("project", "my/testsrc")
    maven.assertResources("project", "my/res")
    maven.assertTestResources("project", "my/testres")
  }

  @Test
  fun testImportingSourcesTagWithoutDirectory() = runBlocking {
    maven.assumeMaven4()
    maven.assumeModel_4_1_0("test requires model 4.1.0")
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <sources>
            <source>
                <lang>java</lang>
                <scope>main</scope>
            </source>
            <source>
                <scope>test</scope>
            </source>
        </sources>
      </build>
      """);
    maven.assertModules("project")
    maven.assertSources("project", "src/main/java")
    maven.assertTestSources("project", "src/test/java")
  }

  @Test
  fun testImportingSourcesTagWithAbsoluteDirectory() = runBlocking {
    maven.assumeMaven4()
    maven.assumeModel_4_1_0("test requires model 4.1.0")
    maven.createProjectSubDirs("my/src", "my/res", "my/testsrc", "my/testres")
    val dir = createTempDirectory()
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <sources>
            <source>
               <directory>$dir</directory>
            </source>
        </sources>
      </build>
      """);
    maven.assertModules("project")
    maven.assertSources("project", maven.projectRoot.toNioPath().relativize(maven.dir).toString())
  }

  @Test
  fun testImportingSourcesTagWithModule() = runBlocking {
    maven.assumeMaven4()
    maven.assumeModel_4_1_0("test requires model 4.1.0")
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <sources>
            <source>
               <module>org.example.data</module>
            </source>
        </sources>
      </build>
      """);
    maven.assertModules("project")
    maven.assertSources("project", "src/org.example.data/main/java")

  }
}
