// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.actions

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixtureIndices
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.awaitConfiguration
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.defaultLanguageLevel
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModifier
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.findTag
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.regex.Pattern

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectModelModifierTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion, modelVersion = modelVersion,
    indices = MavenDomTestFixtureIndices("local1", listOf("local2")),
  )

  @Test
  fun testAddExternalLibraryDependency() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    addExternalLibraryDependency(listOf(maven.getModule("project")),
                                                  ExternalLibraryDescriptor("junit", "junit"),
                                                  DependencyScope.COMPILE)

    assertHasDependency(maven.projectPom, "junit", "junit")
  }


  @Test
  fun testAddExternalLibraryDependencyWithEqualMinAndMaxVersions() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    addExternalLibraryDependency(listOf(maven.getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)

    assertHasDependency(maven.projectPom, "commons-io", "commons-io")
  }

  @Test
  fun testAddManagedLibraryDependency() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>commons-io</groupId>
                                <artifactId>commons-io</artifactId>
                                <version>2.4</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    """.trimIndent())

    addExternalLibraryDependency(listOf(maven.getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)

    assertHasManagedDependency(maven.projectPom, "commons-io", "commons-io")
  }

  @Test
  fun testAddManagedLibraryDependencyWithDifferentScope() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>commons-io</groupId>
                                <artifactId>commons-io</artifactId>
                                <version>2.4</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    """.trimIndent())

    addExternalLibraryDependency(listOf(maven.getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)
  }

  @Test
  fun testAddLibraryDependencyReleaseVersion() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    addExternalLibraryDependency(
      listOf(maven.getModule("project")), ExternalLibraryDescriptor("commons-io", "commons-io", "999.999", "999.999"),
      DependencyScope.COMPILE)

    assertHasDependency(maven.projectPom, "commons-io", "commons-io", "RELEASE")
  }

  @Test
  fun testAddModuleDependency() = runBlocking {
    createTwoModulesPom("m1", "m2")
    val m1 = maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.importProjectAsync()

    addModuleDependency(maven.getModule("m1"), maven.getModule("m2"), DependencyScope.COMPILE, false)

    LocalFileSystem.getInstance().refreshFiles(listOf(m1))
    assertHasDependency(m1, "test", "m2")
  }

  @Test
  fun testAddLibraryDependency() = runBlocking {
    createTwoModulesPom("m1", "m2")
    val m1 = maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
      """.trimIndent())
    maven.importProjectAsync()

    val libName = "Maven: junit:junit:4.0"
    maven.assertModuleLibDep("m2", libName)
    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(maven.project).getLibraryByName(libName)
    assertNotNull(library)
    addLibraryDependency(maven.getModule("m1"), library!!, DependencyScope.COMPILE, false)

    LocalFileSystem.getInstance().refreshFiles(listOf(m1))
    assertHasDependency(m1, "junit", "junit")
  }

  @Test
  fun testChangeLanguageLevel() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val module = maven.getModule("project")
    readAction {
      assertEquals(maven.defaultLanguageLevel, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
    changeLanguageLevel(module, LanguageLevel.JDK_1_8)
    val tag = maven.findTag("project.build.plugins.plugin")
    assertNotNull(tag)
    readAction {
      assertEquals("maven-compiler-plugin", tag.getSubTagText("artifactId"))
      val configuration = tag.findFirstSubTag("configuration")
      assertNotNull(configuration)
      assertEquals("8", configuration!!.getSubTagText("source"))
      assertEquals("8", configuration.getSubTagText("target"))
    }
  }

  @Test
  fun testChangeLanguageLevelPreview() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    val module = maven.getModule("project")
    readAction {
      assertEquals(maven.defaultLanguageLevel, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
    changeLanguageLevel(module, LanguageLevel.entries[LanguageLevel.HIGHEST.ordinal + 1])
    val tag = maven.findTag("project.build.plugins.plugin")
    readAction {
      assertEquals("--enable-preview",
                   tag.findFirstSubTag("configuration")!!
                     .getSubTagText("compilerArgs"))
    }
  }

  private fun createTwoModulesPom(m1: String, m2: String) {
    maven.createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<packaging>pom</packaging>
<version>1</version>
<modules>  
  <module>$m1</module>
  <module>$m2</module>
</modules>
""")
  }

  private suspend fun assertHasDependency(pom: VirtualFile, groupId: String, artifactId: String) = readAction {
    val pomText = PsiManager.getInstance(maven.project).findFile(pom)!!.getText()
    val pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" + groupId + "</groupId>\\s*<artifactId>" +
                                  artifactId + "</artifactId>\\s*<version>(.*)</version>\\s*<scope>(.*)</scope>\\s*</dependency>.*")
    val matcher = pattern.matcher(pomText)
    assertTrue(matcher.matches())
    matcher.group(1)
    return@readAction
  }

  private suspend fun assertHasDependency(pom: VirtualFile, groupId: String, artifactId: String, version: String) = readAction {
    val pomText = PsiManager.getInstance(maven.project).findFile(pom)!!.getText()
    val pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" +
                                  groupId +
                                  "</groupId>\\s*<artifactId>" +
                                  artifactId +
                                  "</artifactId>\\s*<version>" +
                                  version +
                                  "</version>\\s*<scope>(.*)</scope>\\s*</dependency>.*")
    val matcher = pattern.matcher(pomText)
    assertTrue(matcher.matches())
    matcher.group(1)
    return@readAction
  }

  private suspend fun assertHasManagedDependency(pom: VirtualFile, groupId: String, artifactId: String) = readAction {
    val pomText = PsiManager.getInstance(maven.project).findFile(pom)!!.getText()
    val pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" + groupId + "</groupId>\\s*<artifactId>" +
                                  artifactId + "</artifactId>\\s*</dependency>.*")
    val matcher = pattern.matcher(pomText)
    assertTrue(matcher.matches())
  }

  private suspend fun addExternalLibraryDependency(modules: Collection<Module>,
                                                   descriptor: ExternalLibraryDescriptor,
                                                   scope: DependencyScope) {
    extension.addExternalLibraryDependency(modules, descriptor, scope)
    maven.awaitConfiguration()
  }

  private suspend fun addLibraryDependency(from: Module, library: Library, scope: DependencyScope, exported: Boolean) {
    extension.addLibraryDependency(from, library, scope, exported)
    maven.awaitConfiguration()
  }

  private suspend fun addModuleDependency(from: Module, to: Module, scope: DependencyScope, exported: Boolean) {
    extension.addModuleDependency(from, to, scope, exported)
    maven.awaitConfiguration()
  }

  private suspend fun changeLanguageLevel(module: Module, level: LanguageLevel) {
    extension.changeLanguageLevel(module, level)
    maven.awaitConfiguration()
  }

  private val extension: MavenProjectModelModifier
    get() = ContainerUtil.findInstance(JavaProjectModelModifier.EP_NAME.getExtensions(maven.project), MavenProjectModelModifier::class.java)

  companion object {
    private val COMMONS_IO_LIBRARY_DESCRIPTOR_2_4 = ExternalLibraryDescriptor("commons-io", "commons-io", "2.4", "2.4")
  }
}
