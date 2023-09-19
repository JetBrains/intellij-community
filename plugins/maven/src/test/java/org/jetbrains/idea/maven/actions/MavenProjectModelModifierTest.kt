// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.actions

import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModifier
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier
import org.jetbrains.idea.maven.project.importing.MavenImportingManager.Companion.getInstance
import org.junit.Test
import java.util.regex.Pattern

class MavenProjectModelModifierTest : MavenDomWithIndicesTestCase() {
  override fun tearDown() {
    runAll(
      ThrowableRunnable<Throwable> { stopMavenImportManager() },
      ThrowableRunnable<Throwable> { super.tearDown() }
    )
  }

  @Test
  fun testAddExternalLibraryDependency() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val result =
      this.extension.addExternalLibraryDependency(listOf(getModule("project")),
                                                  ExternalLibraryDescriptor("junit", "junit"),
                                                  DependencyScope.COMPILE)
    assertImportingIsInProgress(result)

    assertNotNull(result)
    assertHasDependency(myProjectPom, "junit", "junit")
  }

  private fun assertImportingIsInProgress(result: Promise<Void>?) {
    if (isNewImportingProcess) {
      assertTrue(getInstance(myProject).isImportingInProgress())
    }
  }

  @Test
  fun testAddExternalLibraryDependencyWithEqualMinAndMaxVersions() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val result =
      this.extension.addExternalLibraryDependency(listOf(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)
    assertImportingIsInProgress(result)
    assertNotNull(result)
    assertHasDependency(myProjectPom, "commons-io", "commons-io")
  }

  @Test
  fun testAddManagedLibraryDependency() {
    importProject("""
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

    val result =
      this.extension.addExternalLibraryDependency(listOf(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)
    assertNotNull(result)
    assertImportingIsInProgress(result)
    assertHasManagedDependency(myProjectPom, "commons-io", "commons-io")
  }

  @Test
  fun testAddManagedLibraryDependencyWithDifferentScope() {
    importProject("""
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

    val result =
      this.extension.addExternalLibraryDependency(listOf(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)
    assertNotNull(result)
    assertImportingIsInProgress(result)
  }

  @Test
  fun testAddLibraryDependencyReleaseVersion() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val result = this.extension.addExternalLibraryDependency(
      listOf(getModule("project")), ExternalLibraryDescriptor("commons-io", "commons-io", "999.999", "999.999"),
      DependencyScope.COMPILE)
    assertNotNull(result)
    assertHasDependency(myProjectPom, "commons-io", "commons-io", "RELEASE")
    assertImportingIsInProgress(result)
  }

  @Test
  fun testAddModuleDependency() {
    createTwoModulesPom("m1", "m2")
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    importProject()

    val result = this.extension.addModuleDependency(getModule("m1"), getModule("m2"), DependencyScope.COMPILE, false)
    assertNotNull(result)
    assertImportingIsInProgress(result)
    assertHasDependency(m1, "test", "m2")
  }

  @Test
  fun testAddLibraryDependency() {
    createTwoModulesPom("m1", "m2")
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    createModulePom("m2", """
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
    importProject()

    val libName = "Maven: junit:junit:4.0"
    assertModuleLibDep("m2", libName)
    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(libName)
    assertNotNull(library)
    val result = this.extension.addLibraryDependency(getModule("m1"), library!!, DependencyScope.COMPILE, false)

    assertNotNull(result)
    assertImportingIsInProgress(result)
    assertHasDependency(m1, "junit", "junit")
  }

  @Test
  fun testChangeLanguageLevel() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val module = getModule("project")
    assertEquals(getDefaultLanguageLevel(), LanguageLevelUtil.getEffectiveLanguageLevel(module))
    val result = this.extension.changeLanguageLevel(module, LanguageLevel.JDK_1_8)
    assertNotNull(result)
    assertImportingIsInProgress(result)
    val tag = findTag("project.build.plugins.plugin")
    assertNotNull(tag)
    assertEquals("maven-compiler-plugin", tag.getSubTagText("artifactId"))
    val configuration = tag.findFirstSubTag("configuration")
    assertNotNull(configuration)
    assertEquals("8", configuration!!.getSubTagText("source"))
    assertEquals("8", configuration.getSubTagText("target"))
  }

  @Test
  fun testChangeLanguageLevelPreview() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    val module = getModule("project")
    assertEquals(getDefaultLanguageLevel(), LanguageLevelUtil.getEffectiveLanguageLevel(module))
    val result = this.extension.changeLanguageLevel(module, LanguageLevel.entries[LanguageLevel.HIGHEST.ordinal + 1])
    assertImportingIsInProgress(result)
    assertEquals("--enable-preview",
                 findTag("project.build.plugins.plugin")
                   .findFirstSubTag("configuration")!!
                   .getSubTagText("compilerArgs"))
  }

  private fun createTwoModulesPom(m1: String, m2: String) {
    createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<packaging>pom</packaging>
<version>1</version>
<modules>  <module>$m1</module>
  <module>$m2</module>
</modules>
""")
  }

  private fun assertHasDependency(pom: VirtualFile, groupId: String, artifactId: String): String {
    val pomText = PsiManager.getInstance(myProject).findFile(pom)!!.getText()
    val pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" + groupId + "</groupId>\\s*<artifactId>" +
                                  artifactId + "</artifactId>\\s*<version>(.*)</version>\\s*<scope>(.*)</scope>\\s*</dependency>.*")
    val matcher = pattern.matcher(pomText)
    assertTrue(matcher.matches())
    return matcher.group(1)
  }

  private fun assertHasDependency(pom: VirtualFile, groupId: String, artifactId: String, version: String): String {
    val pomText = PsiManager.getInstance(myProject).findFile(pom)!!.getText()
    val pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" +
                                  groupId +
                                  "</groupId>\\s*<artifactId>" +
                                  artifactId +
                                  "</artifactId>\\s*<version>" +
                                  version +
                                  "</version>\\s*<scope>(.*)</scope>\\s*</dependency>.*")
    val matcher = pattern.matcher(pomText)
    assertTrue(matcher.matches())
    return matcher.group(1)
  }

  private fun assertHasManagedDependency(pom: VirtualFile, groupId: String, artifactId: String) {
    val pomText = PsiManager.getInstance(myProject).findFile(pom)!!.getText()
    val pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" + groupId + "</groupId>\\s*<artifactId>" +
                                  artifactId + "</artifactId>\\s*</dependency>.*")
    val matcher = pattern.matcher(pomText)
    assertTrue(matcher.matches())
  }


  private val extension: MavenProjectModelModifier
    get() = ContainerUtil.findInstance(JavaProjectModelModifier.EP_NAME.getExtensions(myProject), MavenProjectModelModifier::class.java)

  companion object {
    private val COMMONS_IO_LIBRARY_DESCRIPTOR_2_4 = ExternalLibraryDescriptor("commons-io", "commons-io", "2.4", "2.4")
  }
}