// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.maven.testFramework.MavenTestCase
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil.containsDeclaredExtension
import org.jetbrains.idea.maven.utils.MavenUtil.getRepositoryFromSettings
import org.jetbrains.idea.maven.utils.MavenUtil.getVFileBaseDir
import org.jetbrains.idea.maven.utils.MavenUtil.isMaven410
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Properties

class MavenUtilTest : MavenTestCase() {
    @Throws(IOException::class)
    fun testFindExtension() {
        val file = createProjectSubFile(
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

    @Throws(IOException::class)
    fun testFindLocalRepoSchema12() {
        val file = createProjectSubFile(
            "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
        <localRepository>mytestpath</localRepository></settings>
        """.trimIndent()
        )
        TestCase.assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
    }

    @Throws(IOException::class)
    fun testFindLocalRepoSchema10() {
        val file = createProjectSubFile(
            "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
        <localRepository>mytestpath</localRepository></settings>
        """.trimIndent()
        )
        TestCase.assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
    }

    @Throws(IOException::class)
    fun testFindLocalRepoSchema11() {
        val file = createProjectSubFile(
            "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd"
                xmlns="http://maven.apache.org/SETTINGS/1.1.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">  <localRepository>mytestpath</localRepository></settings>
                """.trimIndent()
        )
        TestCase.assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
    }

    @Throws(IOException::class)
    fun testFindLocalRepoWithoutXmls() {
        val file = createProjectSubFile(
            "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings>
        <localRepository>mytestpath</localRepository>
      </settings>
      
      """.trimIndent()
        )
        TestCase.assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
    }

    @Throws(IOException::class)
    fun testFindLocalRepoWithNonTrimmed() {
        val file = createProjectSubFile(
            "testsettings.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <settings>  <localRepository>
      ${'\t'}     ${'\t'}mytestpath
         ${'\t'}</localRepository></settings>
         """.trimIndent()
        )
        TestCase.assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
    }

    @Throws(IOException::class)
    fun testSystemProperties() = runBlocking {
        try {
            System.setProperty("MavenUtilTest.testSystemPropertiesRepoPath", "repopath")
            val file = createProjectSubFile(
                "testsettings.xml", """
        <?xml version="1.0" encoding="UTF-8"?>
        <settings>
          <localRepository>${'$'}{MavenUtilTest.testSystemPropertiesRepoPath}/testpath</localRepository>
        </settings>
        
        """.trimIndent()
            )
            assertEquals("repopath/testpath", getRepositoryFromSettings(file.toNioPath()))
        } finally {
            System.getProperties().remove("MavenUtilTest.testSystemPropertiesRepoPath")
        }
    }

    @Throws(IOException::class)
    fun testGetRepositoryFromSettingsWithBadSymbols() {
        val file = createProjectSubFile("testsettings.xml")
        val str = """
      <settings> <!-- Bad UTF-8 symbol: ü -->
        <localRepository>mytestpath</localRepository>
      </settings>
      """.trimIndent()
        Files.writeString(file.toNioPath(), str, StandardCharsets.ISO_8859_1)
        TestCase.assertEquals("mytestpath", getRepositoryFromSettings(file.toNioPath(), null as Properties?))
    }

    @Throws(IOException::class)
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

    fun testBaseDir() {
        createProjectSubDir(".mvn")
        val subDir1 = createProjectSubDir("subdir1")
        val subDir2 = createProjectSubDir("subdir2")
        createProjectSubDir("subdir2/.mvn")
        val subDir3 = createProjectSubDir("subdir3")
        createProjectSubFile("subdir3/pom.xml", "bad pom xml syntax")
        val subDir4 = createProjectSubDir("subdir4")
        createProjectSubFile(
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

        assertEquals(projectRoot, getVFileBaseDir(subDir1))
        assertEquals(subDir2, getVFileBaseDir(subDir2))
        assertEquals(projectRoot, getVFileBaseDir(subDir3))
        assertEquals(subDir4, getVFileBaseDir(subDir4))
    }

    fun testBaseDirIOfNoDotMvn() {
        val subDir1 = createProjectSubDir("sub/dir1")
        assertEquals(subDir1, getVFileBaseDir(subDir1))
    }
}