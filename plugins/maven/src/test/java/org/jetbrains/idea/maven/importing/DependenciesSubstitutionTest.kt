// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.setPomContent
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.jar.JarOutputStream

open @TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class DependenciesSubstitutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  @Test
  fun `simple library substitution`() = runBlocking {
    val value = Registry.get("external.system.substitute.library.dependencies")
    try {
      value.setValue(true)
      val p1Pom = maven.createProjectSubFile("p1/pom.xml")
      maven.setPomContent(p1Pom, "<groupId>test</groupId>" +
                                 "<artifactId>p1</artifactId>" +
                                 "<packaging>jar</packaging>" +
                                 "<version>1.0</version>")
      val p2Pom = maven.createProjectSubFile("p2/pom.xml")
      maven.setPomContent(p2Pom, "<groupId>test</groupId>" +
                                 "<artifactId>p2</artifactId>" +
                                 "<packaging>jar</packaging>" +
                                 "<version>1</version>" +
                                 "<dependencies>" +
                                 "  <dependency>" +
                                 "    <groupId>test</groupId>" +
                                 "    <artifactId>p1</artifactId>" +
                                 "    <version>1.0</version>" +
                                 "  </dependency>" +
                                 "</dependencies>")
      maven.refreshFiles(listOf(p1Pom, p2Pom))
      maven.importProjectAsync(p1Pom)
      maven.importProjectAsync(p2Pom)
      maven.assertModules("p1", "p2")
      maven.assertModuleModuleDeps("p2", "p1")
    }
    finally {
      value.resetToDefault()
    }
  }

  @Test
  fun `snapshot reactor module substitution with base version`() = runBlocking {
    importModuleTestReactor()
    maven.assertModuleModuleDeps("mod2", "mod1")
  }

  /**
   * Reproduces the broken case: `mod1` is present in the local repository in the "downloaded from a remote
   * repository" shape (timestamped unique snapshot `1.0-20231215.134111-250`, cached `maven-metadata-<repoId>.xml`
   * with `<timestamp>`/`<buildNumber>`, and no `maven-metadata-local.xml`).
   *
   * Maven then expands `org.example:mod1:1.0-SNAPSHOT` to the timestamped unique version, so the resolved library
   * coordinate (`version = 1.0-20231215.134111-250`) no longer equals the `mod1` module coordinate
   * (`version = 1.0-SNAPSHOT`). Dependency substitution matches on the full coordinate (including `version`), so it
   * does not fire and `mod2` keeps a plain library dependency on `mod1` instead of a module dependency.
   *
   * This test is expected to FAIL until the substitution matching ignores the timestamped snapshot version
   * (i.e. matches on `baseVersion`).
   */
  @Test
  fun `snapshot reactor module substitution with downloaded timestamped version`() = runBlocking {
    populateMod1AsDownloadedSnapshot()
    importModuleTestReactor()
    maven.assertModuleModuleDeps("mod2", "mod1")
  }

  /**
   * Same as `snapshot reactor module substitution with base version`, but `mod2` declares the dependency version
   * through a property (`<version>${'$'}{dep.version}</version>` + `<dep.version>1.0-SNAPSHOT</dep.version>`).
   * Maven interpolates the property while building the model, long before artifact resolution, so the artifact's
   * `baseVersion` is `1.0-SNAPSHOT` exactly as with a literal version, and substitution works.
   */
  @Test
  fun `snapshot reactor module substitution with base version, version via property`() = runBlocking {
    importModuleTestReactor(MOD2_MODULE_POM_VERSION_VIA_PROPERTY)
    maven.assertModuleModuleDeps("mod2", "mod1")
  }

  @Test
  fun `snapshot reactor module substitution with downloaded timestamped version, version via property`() = runBlocking {
    populateMod1AsDownloadedSnapshot()
    importModuleTestReactor(MOD2_MODULE_POM_VERSION_VIA_PROPERTY)
    maven.assertModuleModuleDeps("mod2", "mod1")
  }

  private suspend fun importModuleTestReactor(mod2Pom: String = MOD2_MODULE_POM) {
    setUpIsolatedLocalRepository()
    maven.createModulePom("mod1", MOD1_MODULE_POM)
    maven.createModulePom("mod2", mod2Pom)
    maven.createProjectPom(ROOT_POM)
    maven.importProjectAsync()
    maven.assertModules("module_test", "mod1", "mod2")
  }


  private fun setUpIsolatedLocalRepository() {
    val localRepo = localRepositoryPath()
    Files.createDirectories(localRepo)
    maven.repositoryPath = localRepo
    val settingsXml = maven.createProjectSubFile(
      "settings.xml",
      """
       <settings>
          <localRepository>$localRepo</localRepository>
       </settings>
       """.trimIndent())
    maven.mavenGeneralSettings.setUserSettingsFile(settingsXml.canonicalPath)
    maven.mavenGeneralSettings.isWorkOffline = true
    maven.mavenGeneralSettings.isAlwaysUpdateSnapshots = false
  }

  private fun populateMod1AsDownloadedSnapshot() {
    val dir = localRepositoryPath().resolve("org/example/mod1/1.0-SNAPSHOT")
    Files.createDirectories(dir)
    Files.writeString(dir.resolve("mod1-1.0-20231215.134111-250.pom"), MOD1_TIMESTAMPED_POM)
    Files.write(dir.resolve("mod1-1.0-20231215.134111-250.jar"), emptyJarBytes())
    Files.writeString(dir.resolve("maven-metadata-jfrog-maven.xml"), MOD1_REMOTE_METADATA)
    Files.writeString(dir.resolve("_remote.repositories"), MOD1_REMOTE_REPOSITORIES)
  }

  private fun localRepositoryPath() = maven.dir.resolve("local1")

  private fun emptyJarBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    JarOutputStream(out).use { /* empty jar */ }
    return out.toByteArray()
  }

  companion object {
    private val ROOT_POM = """
      <groupId>org.example</groupId>
      <artifactId>module_test</artifactId>
      <version>1.0-SNAPSHOT</version>
      <packaging>pom</packaging>
      <repositories>
        <repository>
          <id>jfrog-maven</id>
          <url>https://jfrog.example.com/artifactory/maven</url>
        </repository>
      </repositories>
      <profiles>
        <profile>
          <id>standard</id>
          <activation><property><name>!installer</name></property></activation>
          <modules>
            <module>mod1</module>
            <module>mod2</module>
          </modules>
        </profile>
      </profiles>
      """.trimIndent()

    private val MOD1_MODULE_POM = """
      <parent>
        <groupId>org.example</groupId>
        <artifactId>module_test</artifactId>
        <version>1.0-SNAPSHOT</version>
      </parent>
      <artifactId>mod1</artifactId>
      """.trimIndent()

    private val MOD2_MODULE_POM = """
      <parent>
        <groupId>org.example</groupId>
        <artifactId>module_test</artifactId>
        <version>1.0-SNAPSHOT</version>
      </parent>
      <artifactId>mod2</artifactId>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>mod1</artifactId>
          <version>1.0-SNAPSHOT</version>
        </dependency>
      </dependencies>
      """.trimIndent()

    private val MOD2_MODULE_POM_VERSION_VIA_PROPERTY = $$"""
      <parent>
        <groupId>org.example</groupId>
        <artifactId>module_test</artifactId>
        <version>1.0-SNAPSHOT</version>
      </parent>
      <artifactId>mod2</artifactId>
      <properties>
        <dep.version>1.0-SNAPSHOT</dep.version>
      </properties>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>mod1</artifactId>
          <version>${dep.version}</version>
        </dependency>
      </dependencies>
      """.trimIndent()

    private val MOD1_TIMESTAMPED_POM = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <parent>
              <groupId>org.example</groupId>
              <artifactId>module_test</artifactId>
              <version>1.0-SNAPSHOT</version>
          </parent>
          <artifactId>mod1</artifactId>
      </project>
      """.trimIndent()

    private val MOD1_REMOTE_METADATA = """
      <?xml version="1.0" encoding="UTF-8"?>
      <metadata modelVersion="1.1.0">
        <groupId>org.example</groupId>
        <artifactId>mod1</artifactId>
        <version>1.0-SNAPSHOT</version>
        <versioning>
          <snapshot>
            <timestamp>20231215.134111</timestamp>
            <buildNumber>250</buildNumber>
          </snapshot>
          <lastUpdated>20231215134624</lastUpdated>
          <snapshotVersions>
            <snapshotVersion>
              <extension>jar</extension>
              <value>1.0-20231215.134111-250</value>
              <updated>20231215134111</updated>
            </snapshotVersion>
            <snapshotVersion>
              <extension>pom</extension>
              <value>1.0-20231215.134111-250</value>
              <updated>20231215134111</updated>
            </snapshotVersion>
          </snapshotVersions>
        </versioning>
      </metadata>
      """.trimIndent()

    private val MOD1_REMOTE_REPOSITORIES = """
      #NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.
      mod1-1.0-20231215.134111-250.pom>jfrog-maven=
      mod1-1.0-20231215.134111-250.jar>jfrog-maven=
      """.trimIndent()
  }
}