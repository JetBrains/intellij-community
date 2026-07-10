// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.config.MavenConfigParser
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectRoot
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil.DOT_M2_DIR
import org.jetbrains.idea.maven.utils.MavenUtil.containsDeclaredExtension
import org.jetbrains.idea.maven.utils.MavenUtil.getRepositoryFromSettings
import org.jetbrains.idea.maven.utils.MavenUtil.getVFileBaseDir
import org.jetbrains.idea.maven.utils.MavenUtil.inferModelVersionFromNamespace
import org.jetbrains.idea.maven.utils.MavenUtil.isMaven410
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

@TestApplication
class MavenUtilTest {
  private val maven by mavenFixture()

  @Test
  fun testFindExtension() {
    val file = maven.createProjectSubFile(
      ".mvn/extensions.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <extensions>
          <extension>
              <groupId>group-id</groupId>
              <artifactId>artifact-id</artifactId>
              <version>1.0.42</version>
          </extension>
      </extensions>

      """.trimIndent()
    )
    assertTrue(containsDeclaredExtension(file.toNioPath(), MavenId("group-id:artifact-id:1.0.42")))
  }

  @Test
  fun testFindLocalRepoSchema12() {
    val file = maven.createProjectSubFile(
      "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
        <localRepository>mytestpath</localRepository></settings>
        """.trimIndent()
    )
    assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
  }

  @Test
  fun testFindLocalRepoSchema10() {
    val file = maven.createProjectSubFile(
      "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
        <localRepository>mytestpath</localRepository></settings>
        """.trimIndent()
    )
    assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
  }

  @Test
  fun testFindLocalRepoSchema11() {
    val file = maven.createProjectSubFile(
      "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd"
                xmlns="http://maven.apache.org/SETTINGS/1.1.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">  <localRepository>mytestpath</localRepository></settings>
                """.trimIndent()
    )
    assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
  }

  @Test
  fun testFindLocalRepoWithoutXmls() {
    val file = maven.createProjectSubFile(
      "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings>
        <localRepository>mytestpath</localRepository>
      </settings>

      """.trimIndent()
    )
    assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
  }

  @Test
  fun testFindLocalRepoWithNonTrimmed() {
    val file = maven.createProjectSubFile(
      "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings>  <localRepository>
      ${'\t'}     ${'\t'}mytestpath
         ${'\t'}</localRepository></settings>
         """.trimIndent()
    )
    assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
  }

  @Test
  fun testSystemProperties() = runBlocking {
    try {
      System.setProperty("MavenUtilTest.testSystemPropertiesRepoPath", "repopath")
      val file = maven.createProjectSubFile(
        "testsettings.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <settings>
          <localRepository>${'$'}{MavenUtilTest.testSystemPropertiesRepoPath}/testpath</localRepository>
        </settings>

        """.trimIndent()
      )
      assertEquals("repopath/testpath", getRepositoryFromSettings(file.toNioPath()))
    }
    finally {
      System.getProperties().remove("MavenUtilTest.testSystemPropertiesRepoPath")
    }
  }

  @Test
  fun testGetRepositoryFromSettingsWithBadSymbols() {
    val file = maven.createProjectSubFile("testsettings.xml")
    val str = """
      <settings> <!-- Bad UTF-8 symbol: ü -->
        <localRepository>mytestpath</localRepository>
      </settings>
      """.trimIndent()
    Files.writeString(file.toNioPath(), str, StandardCharsets.ISO_8859_1)
    assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
  }

  @Test
  fun testIsMaven41() {
    assertFalse(isMaven410(null, null))
    assertFalse(isMaven410(null, "http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd"))
    assertFalse(isMaven410("http://maven.apache.org/POM/4.1.0", null))
    assertFalse(
      isMaven410(
        "http://maven.apache.org/POM/4.1.0",
        "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
      )
    )
    assertFalse(
      isMaven410(
        "http://maven.apache.org/POM/4.0.0",
        "http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd"
      )
    )
    assertTrue(
      isMaven410(
        "http://maven.apache.org/POM/4.1.0",
        "http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd"
      )
    )
    assertTrue(
      isMaven410(
        "https://maven.apache.org/POM/4.1.0",
        "https://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd"
      )
    )
    assertTrue(
      isMaven410(
        "https://maven.apache.org/POM/4.1.0",
        "https://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd"
      )
    )
    assertTrue(
      isMaven410(
        "https://maven.apache.org/POM/4.1.0",
        "https://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd https://maven.apache.org/maven-v4_1_0.xsd"
      )
    )
    assertTrue(
      isMaven410(
        "https://maven.apache.org/POM/4.1.0",
        "https://maven.apache.org/POM/4.1.0\nhttps://maven.apache.org/xsd/maven-4.1.0.xsd\n https://maven.apache.org/maven-v4_1_0.xsd"
      )
    )
    assertTrue(
      isMaven410(
        "https://maven.apache.org/POM/4.1.0",
        "https://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0-with-some-additional-pref.xsd"
      )
    )
  }

  @Test
  fun testBaseDir() {
    maven.createProjectSubDir(".mvn")
    val subDir1 = maven.createProjectSubDir("subdir1")
    val subDir2 = maven.createProjectSubDir("subdir2")
    maven.createProjectSubDir("subdir2/.mvn")
    val subDir3 = maven.createProjectSubDir("subdir3")
    maven.createProjectSubFile("subdir3/pom.xml", "bad pom xml syntax")
    val subDir4 = maven.createProjectSubDir("subdir4")
    maven.createProjectSubFile(
      "subdir4/pom.xml",
      """
                           <?xml version="1.0" encoding="UTF-8"?>
                           <project xmlns="http://maven.apache.org/POM/4.1.0"
                                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                    xsi:schemaLocation="http://maven.apache.org/POM/4.1.0
                                                        http://maven.apache.org/xsd/maven-4.1.0.xsd" root="true">
                             <modelVersion>4.1.0</modelVersion>
                             <groupId>test</groupId>
                             <artifactId>test</artifactId>
                             <version>1.0-SNAPSHOT</version>
                             </project>
                             """.trimIndent()
    )

    assertEquals(maven.projectRoot, getVFileBaseDir(subDir1))
    assertEquals(subDir2, getVFileBaseDir(subDir2))
    assertEquals(maven.projectRoot, getVFileBaseDir(subDir3))
    assertEquals(subDir4, getVFileBaseDir(subDir4))
  }

  @Test
  fun testEelToolchainsOverrideWithEmptyString() = runBlocking {
    maven.createProjectSubFile(".mvn/maven.config", "-t\ntoolchains-path.xml")
    maven.createProjectSubFile("toolchains-path.xml")
    val config = MavenConfigParser.parse(maven.projectPath.toString())
    val toolchainsFile = MavenEelUtil.getToolchainsFile(maven.project, "", config)
    val expected = maven.projectPath.resolve("toolchains-path.xml")
    assertTrue(Files.isSameFile(expected, toolchainsFile), "Files $expected and $toolchainsFile should be the same")
  }

  @Test
  fun testEelToolchainsOverrideWithNullString() = runBlocking {
    maven.createProjectSubFile(".mvn/maven.config", "-t\ntoolchains-path.xml")
    maven.createProjectSubFile("toolchains-path.xml")
    val config = MavenConfigParser.parse(maven.projectPath.toString())
    val toolchainsFile = MavenEelUtil.getToolchainsFile(maven.project, null, config)
    val expected = maven.projectPath.resolve("toolchains-path.xml")
    assertTrue(Files.isSameFile(expected, toolchainsFile), "Files $expected and $toolchainsFile should be the same")
  }

  @Test
  fun testEelToolchainsOverrideWithNullStringNonExistingFile() = runBlocking {
    maven.createProjectSubFile(".mvn/maven.config", "-t\nnon-existent-toolchains-path.xml")
    //createProjectSubFile("non-existent-toolchains-path.xml")
    val config = MavenConfigParser.parse(maven.projectPath.toString())
    val toolchainsFile = MavenEelUtil.getToolchainsFile(maven.project, null, config)
    assertEquals(maven.projectPath.getEelDescriptor().toEelApi().userInfo.home.asNioPath()
                   .resolve(DOT_M2_DIR)
                   .resolve(MavenUtil.TOOLCHAINS_XML).toString(), toolchainsFile.toString())
  }

  @Test
  fun testEelToolchainsOverrideWithSettingsString() = runBlocking {
    maven.createProjectSubFile(".mvn/maven.config", "-t\ntoolchains-path.xml")
    maven.createProjectSubFile("toolchains-path.xml")
    val config = MavenConfigParser.parse(maven.projectPath.toString())
    val toolchainsFile = MavenEelUtil.getToolchainsFile(maven.project, "path/to/my-toolchains.xml", config)
    assertEquals(Paths.get("path", "to", "my-toolchains.xml"), toolchainsFile)
  }

  @Test
  fun testBaseDirIOfNoDotMvn() {
    val subDir1 = maven.createProjectSubDir("sub/dir1")
    assertEquals(subDir1, getVFileBaseDir(subDir1))
  }

  @Test
  fun testInferModelVersionFromNamespace() {
    // null namespace returns null
    assertNull(inferModelVersionFromNamespace(null))

    // non-Maven namespace returns null
    assertNull(inferModelVersionFromNamespace("http://example.com/POM/4.0.0"))
    assertNull(inferModelVersionFromNamespace(""))

    // http scheme
    assertEquals("4.0.0", inferModelVersionFromNamespace("http://maven.apache.org/POM/4.0.0"))
    assertEquals("4.1.0", inferModelVersionFromNamespace("http://maven.apache.org/POM/4.1.0"))

    // https scheme
    assertEquals("4.0.0", inferModelVersionFromNamespace("https://maven.apache.org/POM/4.0.0"))
    assertEquals("4.1.0", inferModelVersionFromNamespace("https://maven.apache.org/POM/4.1.0"))

    // future versions
    assertEquals("4.2.0", inferModelVersionFromNamespace("http://maven.apache.org/POM/4.2.0"))
    assertEquals("5.0.0", inferModelVersionFromNamespace("https://maven.apache.org/POM/5.0.0"))
  }
}
