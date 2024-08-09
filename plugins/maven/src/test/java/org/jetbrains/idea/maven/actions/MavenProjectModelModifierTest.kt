// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.actions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModifier
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase
import org.jetbrains.idea.maven.importing.MavenProjectModelModifier
import org.junit.Test
import java.util.regex.Pattern

class MavenProjectModelModifierTest : MavenDomWithIndicesTestCase() {

  @Test
  fun testAddExternalLibraryDependency() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    addExternalLibraryDependency(listOf(getModule("project")),
                                                  ExternalLibraryDescriptor("junit", "junit"),
                                                  DependencyScope.COMPILE)

    assertHasDependency(projectPom, "junit", "junit")
  }


  @Test
  fun testAddExternalLibraryDependencyWithEqualMinAndMaxVersions() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    addExternalLibraryDependency(listOf(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)
    assertHasDependency(projectPom, "commons-io", "commons-io")
  }

  @Test
  fun testAddManagedLibraryDependency() = runBlocking {
    importProjectAsync("""
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

    addExternalLibraryDependency(listOf(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)
    assertHasManagedDependency(projectPom, "commons-io", "commons-io")
  }

  @Test
  fun testAddManagedLibraryDependencyWithDifferentScope() = runBlocking {
    importProjectAsync("""
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

    addExternalLibraryDependency(listOf(getModule("project")), COMMONS_IO_LIBRARY_DESCRIPTOR_2_4,
                                                  DependencyScope.COMPILE)
  }

  @Test
  fun testAddLibraryDependencyReleaseVersion() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    addExternalLibraryDependency(
      listOf(getModule("project")), ExternalLibraryDescriptor("commons-io", "commons-io", "999.999", "999.999"),
      DependencyScope.COMPILE)
    assertHasDependency(projectPom, "commons-io", "commons-io", "RELEASE")
    return@runBlocking
  }

  @Test
  fun testAddModuleDependency() = runBlocking {
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
    importProjectAsync()

    addModuleDependency(getModule("m1"), getModule("m2"), DependencyScope.COMPILE, false)
    assertHasDependency(m1, "test", "m2")
  }

  @Test
  fun testAddLibraryDependency() = runBlocking {
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
    importProjectAsync()

    val libName = "Maven: junit:junit:4.0"
    assertModuleLibDep("m2", libName)
    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName(libName)
    assertNotNull(library)
    addLibraryDependency(getModule("m1"), library!!, DependencyScope.COMPILE, false)

    assertHasDependency(m1, "junit", "junit")
  }

  @Test
  fun testChangeLanguageLevel() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val module = getModule("project")
    readAction {
      assertEquals(getDefaultLanguageLevel(), LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
    changeLanguageLevel(module, LanguageLevel.JDK_1_8)
    val tag = findTag("project.build.plugins.plugin")
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
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    val module = getModule("project")
    readAction {
      assertEquals(getDefaultLanguageLevel(), LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
    changeLanguageLevel(module, LanguageLevel.entries[LanguageLevel.HIGHEST.ordinal + 1])
    val tag = findTag("project.build.plugins.plugin")
    readAction {
      assertEquals("--enable-preview",
                   tag.findFirstSubTag("configuration")!!
                     .getSubTagText("compilerArgs"))
    }
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

  private suspend fun assertHasDependency(pom: VirtualFile, groupId: String, artifactId: String) = readAction {
    val pomText = PsiManager.getInstance(project).findFile(pom)!!.getText()
    val pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" + groupId + "</groupId>\\s*<artifactId>" +
                                  artifactId + "</artifactId>\\s*<version>(.*)</version>\\s*<scope>(.*)</scope>\\s*</dependency>.*")
    val matcher = pattern.matcher(pomText)
    assertTrue(matcher.matches())
    matcher.group(1)
    return@readAction
  }

  private suspend fun assertHasDependency(pom: VirtualFile, groupId: String, artifactId: String, version: String) = readAction {
    val pomText = PsiManager.getInstance(project).findFile(pom)!!.getText()
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
    val pomText = PsiManager.getInstance(project).findFile(pom)!!.getText()
    val pattern = Pattern.compile("(?s).*<dependency>\\s*<groupId>" + groupId + "</groupId>\\s*<artifactId>" +
                                  artifactId + "</artifactId>\\s*</dependency>.*")
    val matcher = pattern.matcher(pomText)
    assertTrue(matcher.matches())
  }

  private suspend fun addExternalLibraryDependency(modules: Collection<Module?>,
                                                   descriptor: ExternalLibraryDescriptor,
                                                   scope: DependencyScope) {
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          extension.addExternalLibraryDependency(modules, descriptor, scope)
        }
      }
    }
    return
  }

  private suspend fun addLibraryDependency(from: Module, library: Library, scope: DependencyScope, exported: Boolean) {
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          extension.addLibraryDependency(from, library, scope, exported)
        }
      }
    }
    return
  }

  private suspend fun addModuleDependency(from: Module, to: Module, scope: DependencyScope, exported: Boolean) {
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          extension.addModuleDependency(from, to, scope, exported)
        }
      }
    }
    return
  }

  private suspend fun changeLanguageLevel(module: Module, level: LanguageLevel) {
    waitForImportWithinTimeout {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          extension.changeLanguageLevel(module, level)
        }
      }
    }
    return
  }

  private val extension: MavenProjectModelModifier
    get() = ContainerUtil.findInstance(JavaProjectModelModifier.EP_NAME.getExtensions(project), MavenProjectModelModifier::class.java)

  companion object {
    private val COMMONS_IO_LIBRARY_DESCRIPTOR_2_4 = ExternalLibraryDescriptor("commons-io", "commons-io", "2.4", "2.4")
  }
}