// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ArrayUtilRt
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.model.MavenProfile
import org.jetbrains.idea.maven.model.MavenResource
import org.junit.Assume
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.pathString

class MavenProjectReaderTest : MavenProjectReaderTestCase() {
  fun testBasics() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val p = readProject(projectPom).mavenId

    assertEquals("test", p.groupId)
    assertEquals("project", p.artifactId)
    assertEquals("1", p.version)
  }

  fun testInvalidXml() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    assertProblems(readProject(projectPom, NullProjectLocator()))

    createProjectPom("""
                       <foo>
                       </bar>
                       <<groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val result = readProject(projectPom, NullProjectLocator())
    assertProblems(result, "'pom.xml' has syntax errors")
    val p = result.mavenModel.mavenId

    assertEquals("test", p.groupId)
    assertEquals("project", p.artifactId)
    assertEquals("1", p.version)
  }

  fun testInvalidXmlCharData() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    assertProblems(readProject(projectPom, NullProjectLocator()))

    createProjectPom("<name>a" + String(byteArrayOf(0x0), StandardCharsets.UTF_8) +
                     "a</name><fo" + String(byteArrayOf(0x0),
                                            StandardCharsets.UTF_8) +
                     "o></foo>\n")

    val result = readProject(projectPom, NullProjectLocator())
    assertProblems(result, "'pom.xml' has syntax errors")
    val p = result.mavenModel

    assertEquals("a0x0a", p.name)
  }

  fun testInvalidParentXml() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <foo
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    assertProblems(readProject(module, NullProjectLocator()), "Parent 'test:parent:1' has problems")
  }

  fun testProjectWithAbsentParentXmlIsValid() {
    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())
    assertProblems(readProject(projectPom, NullProjectLocator()))
  }

  fun testProjectWithSelfParentIsInvalid() {
    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       """.trimIndent())
    assertProblems(readProject(projectPom, NullProjectLocator()), "Self-inheritance found")
  }

  fun testInvalidProfilesXml() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    createProfilesXml("<profiles")

    assertProblems(readProject(projectPom, NullProjectLocator()), "'profiles.xml' has syntax errors")
  }

  fun testInvalidSettingsXml() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    updateSettingsXml("<settings")

    assertProblems(readProject(projectPom, NullProjectLocator()), "'settings.xml' has syntax errors")
  }

  fun testInvalidXmlWithNotClosedTag() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1<name>foo</name>
                       """.trimIndent())

    val readResult = readProject(projectPom, NullProjectLocator())
    assertProblems(readResult, "'pom.xml' has syntax errors")
    val p = readResult.mavenModel

    assertEquals("test", p.mavenId.groupId)
    assertEquals("project", p.mavenId.artifactId)
    assertEquals("Unknown", p.mavenId.version)
    assertEquals("foo", p.name)
  }

  // These tests fail until issue https://youtrack.jetbrains.com/issue/IDEA-272809 is fixed
  fun testInvalidXmlWithWrongClosingTag() {
    //waiting for IDEA-272809
    Assume.assumeTrue(false)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</vers>
                       <name>foo</name>
                       """.trimIndent())

    val readResult = readProject(projectPom, NullProjectLocator())
    assertProblems(readResult, "'pom.xml' has syntax errors")
    val p = readResult.mavenModel

    assertEquals("test", p.mavenId.groupId)
    assertEquals("project", p.mavenId.artifactId)
    assertEquals("1", p.mavenId.version)
    assertEquals("foo", p.name)
  }

  fun testEmpty() {
    createProjectPom("")

    val p = readProject(projectPom)

    assertEquals("Unknown", p.mavenId.groupId)
    assertEquals("Unknown", p.mavenId.artifactId)
    assertEquals("Unknown", p.mavenId.version)
  }

  fun testSpaces() {
    createProjectPom("<name>foo bar</name>")

    val p = readProject(projectPom)
    assertEquals("foo bar", p.name)
  }

  fun testNewLines() {
    createProjectPom("""
                       <groupId>
                         group
                       </groupId>
                       <artifactId>
                         artifact
                       </artifactId>
                       <version>
                         1
                       </version>
                       """.trimIndent())

    val p = readProject(projectPom)
    assertEquals(MavenId("group", "artifact", "1"), p.mavenId)
  }

  fun testCommentsWithNewLinesInTags() {
    createProjectPom("""
                       <groupId>test<!--a-->
                       </groupId><artifactId>
                       <!--a-->project</artifactId><version>1
                       <!--a--></version><name>
                       <!--a-->
                       </name>
                       """.trimIndent())

    val p = readProject(projectPom)
    val id = p.mavenId

    assertEquals("test", id.groupId)
    assertEquals("project", id.artifactId)
    assertEquals("1", id.version)
    assertNull(p.name)
  }

  fun testTextInContainerTag() {
    createProjectPom("foo <name>name</name> bar")

    val p = readProject(projectPom)
    assertEquals("name", p.name)
  }

  fun testDefaults() {
    val file = WriteAction.compute<VirtualFile, IOException> {
      val res = projectRoot.createChildData(this, "pom.xml")
      VfsUtil.saveText(res, """
        <project>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </project>
        """.trimIndent())
      res
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val p = readProject(file)

    assertEquals("jar", p.packaging)
    assertNull(p.name)
    assertNull(p.parent)

    assertEquals("project-1", p.build.finalName)
    assertEquals(null, p.build.defaultGoal)
    UsefulTestCase.assertSize(1, p.build.sources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("src/main/java"), p.build.sources[0])
    UsefulTestCase.assertSize(1, p.build.testSources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("src/test/java"), p.build.testSources[0])
    assertEquals(1, p.build.resources.size)
    assertResource(p.build.resources[0], pathFromBasedir("src/main/resources"),
                   false, null, emptyList(), emptyList())
    assertEquals(1, p.build.testResources.size)
    assertResource(p.build.testResources[0], pathFromBasedir("src/test/resources"),
                   false, null, emptyList(), emptyList())
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target"), p.build.directory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target/classes"), p.build.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target/test-classes"), p.build.testOutputDirectory)
  }

  fun testDefaultsForParent() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         dummy</parent>
                       """.trimIndent())

    val p = readProject(projectPom)

    assertParent(p, "Unknown", "Unknown", "Unknown")
  }

  fun testTakingCoordinatesFromParent() {
    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())

    val id = readProject(projectPom).mavenId

    assertEquals("test", id.groupId)
    assertEquals("Unknown", id.artifactId)
    assertEquals("1", id.version)
  }

  fun testCustomSettings() {
    val file = WriteAction.compute<VirtualFile, IOException> {
      val res = projectRoot.createChildData(this, "pom.xml")
      VfsUtil.saveText(res, """
        <project>
          <modelVersion>1.2.3</modelVersion>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
          <name>foo</name>
          <packaging>pom</packaging>
          <parent>
            <groupId>testParent</groupId>
            <artifactId>projectParent</artifactId>
            <version>2</version>
            <relativePath>../parent/pom.xml</relativePath>
          </parent>
          <build>
            <finalName>xxx</finalName>
            <defaultGoal>someGoal</defaultGoal>
            <sourceDirectory>mySrc</sourceDirectory>
            <testSourceDirectory>myTestSrc</testSourceDirectory>
            <scriptSourceDirectory>myScriptSrc</scriptSourceDirectory>
            <resources>
              <resource>
                <directory>myRes</directory>
                <filtering>true</filtering>
                <targetPath>dir</targetPath>
                <includes><include>**.properties</include></includes>
                <excludes><exclude>**.xml</exclude></excludes>
              </resource>
            </resources>
            <testResources>
              <testResource>
                <directory>myTestRes</directory>
                <includes><include>**.properties</include></includes>
              </testResource>
            </testResources>
            <directory>myOutput</directory>
            <outputDirectory>myClasses</outputDirectory>
            <testOutputDirectory>myTestClasses</testOutputDirectory>
          </build>
        </project>
        """.trimIndent())
      res
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val p = readProject(file)

    assertEquals("pom", p.packaging)
    assertEquals("foo", p.name)

    assertParent(p, "testParent", "projectParent", "2")

    assertEquals("xxx", p.build.finalName)
    assertEquals("someGoal", p.build.defaultGoal)
    UsefulTestCase.assertSize(1, p.build.sources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("mySrc"), p.build.sources[0])
    UsefulTestCase.assertSize(1, p.build.testSources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myTestSrc"), p.build.testSources[0])
    assertEquals(1, p.build.resources.size)
    assertResource(p.build.resources[0], pathFromBasedir("myRes"),
                   true, "dir", listOf("**.properties"), listOf("**.xml"))
    assertEquals(1, p.build.testResources.size)
    assertResource(p.build.testResources[0], pathFromBasedir("myTestRes"),
                   false, null, listOf("**.properties"), emptyList())
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myOutput"), p.build.directory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myClasses"), p.build.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myTestClasses"), p.build.testOutputDirectory)
  }

  fun testOutputPathsAreBasedOnTargetPath() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <directory>my-target</directory>
                       </build>
                       """.trimIndent())

    val p = readProject(projectPom)

    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target"), p.build.directory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/classes"), p.build.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/test-classes"), p.build.testOutputDirectory)
  }

  fun testDoesNotIncludeResourcesWithoutDirectory() {
    createProjectPom("""
                       <build>
                         <resources>
                           <resource>
                             <directory></directory>
                           </resource>
                           <resource>
                             <directory>myRes</directory>
                           </resource>
                         </resources>
                         <testResources>
                           <testResource>
                             <filtering>true</filtering>
                           </testResource>
                           <testResource>
                             <directory>myTestRes</directory>
                           </testResource>
                         </testResources>
                       </build>
                       """.trimIndent())

    val p = readProject(projectPom)

    assertEquals(1, p.build.resources.size)
    assertResource(p.build.resources[0], pathFromBasedir("myRes"),
                   false, null, emptyList(), emptyList())

    assertEquals(1, p.build.testResources.size)
    assertResource(p.build.testResources[0], pathFromBasedir("myTestRes"),
                   false, null, emptyList(), emptyList())
  }

  fun testRepairResourcesWithoutDirectory() {
    createProjectPom("""
                    <build>
                       <resources>
                         <resource>
                         </resource>
                       </resources>
                       <testResources>
                         <testResource>
                         </testResource>
                       </testResources>
                    </build>
                    """.trimIndent())

    val p = readProject(projectPom)

    assertEquals(1, p.build.resources.size)
    assertResource(p.build.resources[0], pathFromBasedir("src/main/resources"),
                   false, null, emptyList(), emptyList())

    assertEquals(1, p.build.testResources.size)
    assertResource(p.build.testResources[0], pathFromBasedir("src/test/resources"),
                   false, null, emptyList(), emptyList())
  }

  fun testRepairResourcesWithEmptyDirectory() {
    createProjectPom("""
                       <build>
                         <resources>
                           <resource>
                             <directory></directory>
                           </resource>
                         </resources>
                         <testResources>
                           <testResource>
                             <directory></directory>
                           </testResource>
                         </testResources>
                       </build>
                       """.trimIndent())

    val p = readProject(projectPom)

    assertEquals(1, p.build.resources.size)
    assertResource(p.build.resources[0], pathFromBasedir("src/main/resources"),
                   false, null, emptyList(), emptyList())

    assertEquals(1, p.build.testResources.size)
    assertResource(p.build.testResources[0], pathFromBasedir("src/test/resources"),
                   false, null, emptyList(), emptyList())
  }

  fun testPathsWithProperties() {
    createProjectPom("""
                       <properties>
                         <foo>subDir</foo>
                         <emptyProperty />
                       </properties>
                       <build>
                         <sourceDirectory>${'$'}{foo}/mySrc</sourceDirectory>
                         <testSourceDirectory>${'$'}{foo}/myTestSrc</testSourceDirectory>
                         <scriptSourceDirectory>${'$'}{foo}/myScriptSrc</scriptSourceDirectory>
                         <resources>
                           <resource>
                             <directory>${'$'}{foo}/myRes</directory>
                           </resource>
                           <resource>
                             <directory>aaa/${'$'}{emptyProperty}/${'$'}{unexistingProperty}</directory>
                           </resource>
                         </resources>
                         <testResources>
                           <testResource>
                             <directory>${'$'}{foo}/myTestRes</directory>
                           </testResource>
                         </testResources>
                         <directory>${'$'}{foo}/myOutput</directory>
                         <outputDirectory>${'$'}{foo}/myClasses</outputDirectory>
                         <testOutputDirectory>${'$'}{foo}/myTestClasses</testOutputDirectory>
                       </build>
                       """.trimIndent())

    val p = readProject(projectPom)

    UsefulTestCase.assertSize(1, p.build.sources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/mySrc"), p.build.sources[0])
    UsefulTestCase.assertSize(1, p.build.testSources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myTestSrc"), p.build.testSources[0])
    assertEquals(2, p.build.resources.size)
    assertResource(p.build.resources[0], pathFromBasedir("subDir/myRes"),
                   false, null, emptyList(), emptyList())
    assertResource(p.build.resources[1], pathFromBasedir("aaa/\${unexistingProperty}"),
                   false, null, emptyList(), emptyList())
    assertEquals(1, p.build.testResources.size)
    assertResource(p.build.testResources[0], pathFromBasedir("subDir/myTestRes"),
                   false, null, emptyList(), emptyList())
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myOutput"), p.build.directory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myClasses"), p.build.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myTestClasses"), p.build.testOutputDirectory)
  }

  fun testExpandingProperties() {
    createProjectPom("""
                       <properties>
                         <prop1>value1</prop1>
                         <prop2>value2</prop2>
                       </properties>
                       <name>${'$'}{prop1}</name>
                       <packaging>${'$'}{prop2}</packaging>
                       """.trimIndent())
    val p = readProject(projectPom)

    assertEquals("value1", p.name)
    assertEquals("value2", p.packaging)
  }

  fun testExpandingPropertiesRecursively() {
    createProjectPom("""
                       <properties>
                         <prop1>value1</prop1>
                         <prop2>${'$'}{prop1}2</prop2>
                       </properties>
                       <name>${'$'}{prop1}</name>
                       <packaging>${'$'}{prop2}</packaging>
                       """.trimIndent())
    val p = readProject(projectPom)

    assertEquals("value1", p.name)
    assertEquals("value12", p.packaging)
  }

  fun testHandlingRecursiveProperties() {
    createProjectPom("""
                       <properties>
                         <prop1>${'$'}{prop2}</prop1>
                         <prop2>${'$'}{prop1}</prop2>
                       </properties>
                       <name>${'$'}{prop1}</name>
                       <packaging>${'$'}{prop2}</packaging>
                       """.trimIndent())
    val p = readProject(projectPom)

    assertEquals("\${prop1}", p.name)
    assertEquals("\${prop2}", p.packaging)
  }

  fun testHandlingRecursionProprielyAndDoNotForgetCoClearRecursionGuard() {
    val repoPath = File(dir, "repository")
    repositoryPath = repoPath.path

    val parentFile = File(repoPath, "test/parent/1/parent-1.pom")
    parentFile.parentFile.mkdirs()
    FileUtil.writeToFile(parentFile, createPomXml("""
                                                    <groupId>test</groupId>
                                                    <artifactId>parent</artifactId>
                                                    <version>1</version>
                                                    """.trimIndent()).toByteArray(StandardCharsets.UTF_8))

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>not-a-project</artifactId>
                       <version>1</version>
                       <parent>
                        <groupId>test</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                       </parent>
                       """.trimIndent())

    val child = createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <parent>
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                          </parent>
                                          """.trimIndent())

    val readResult = readProject(child, NullProjectLocator())
    assertProblems(readResult)
  }

  fun testDoNotGoIntoRecursionWhenTryingToResolveParentInDefaultPath() {
    val child = createModulePom("child",
                                """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>subChild</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>child</artifactId>
                         <version>1</version>
                         <relativePath>child/pom.xml</relativePath>
                       </parent>
                       """.trimIndent())

    val readResult = readProject(child, NullProjectLocator())
    assertProblems(readResult)
  }

  fun testExpandingSystemAndEnvProperties() {
    createProjectPom("""
  <name>${"$"}{java.home}</name>
  <packaging>${"$"}{env.${envVar}}</packaging>
  """.trimIndent())

    val p = readProject(projectPom)
    assertEquals(System.getProperty("java.home"), p.name)
    assertEquals(System.getenv(envVar), p.packaging)
  }

  fun testExpandingPropertiesFromProfiles() {
    createProjectPom("""
                       <name>${'$'}{prop1}</name>
                       <packaging>${'$'}{prop2}</packaging>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                           <properties>
                             <prop1>value1</prop1>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <prop2>value2</prop2>
                           </properties>
                         </profile>
                       </profiles>
                       """.trimIndent())

    val p = readProject(projectPom)
    assertEquals("value1", p.name)
    assertEquals("\${prop2}", p.packaging)
  }

  fun testExpandingPropertiesFromManuallyActivatedProfiles() {
    createProjectPom("""
                       <name>${'$'}{prop1}</name>
                       <packaging>${'$'}{prop2}</packaging>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                           <properties>
                             <prop1>value1</prop1>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <prop2>value2</prop2>
                           </properties>
                         </profile>
                       </profiles>
                       """.trimIndent())

    val p = readProject(projectPom, "two")
    assertEquals("\${prop1}", p.name)
    assertEquals("value2", p.packaging)
  }

  fun testExpandingPropertiesFromParent() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    val p = readProject(module)
    assertEquals("value", p.name)
  }

  fun testDoNotExpandPropertiesFromParentWithWrongCoordinates() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>invalid</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    val p = readProject(module)
    assertEquals("\${prop}", p.name)
  }

  fun testExpandingPropertiesFromParentNotInVfs() {
    FileUtil.writeToFile(File(projectRoot.path, "pom.xml"),
                         createPomXml("""
                                        <groupId>test</groupId>
                                        <artifactId>parent</artifactId>
                                        <version>1</version>
                                        <properties>
                                          <prop>value</prop>
                                        </properties>
                                        """.trimIndent()).toByteArray(StandardCharsets.UTF_8))

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    val p = readProject(module)
    assertEquals("value", p.name)
  }

  fun testExpandingPropertiesFromIndirectParent() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """.trimIndent())

    createModulePom("module",
                    """
                      <groupId>test</groupId>
                      <artifactId>module</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                      </parent>
                      """.trimIndent())

    val subModule = createModulePom("module/subModule",
                                    """
                                              <parent>
                                                <groupId>test</groupId>
                                                <artifactId>module</artifactId>
                                                <version>1</version>
                                              </parent>
                                              <name>${'$'}{prop}</name>
                                              """.trimIndent())

    val p = readProject(subModule)
    assertEquals("value", p.name)
  }

  fun testExpandingPropertiesFromParentInSpecifiedLocation() {
    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <properties>
                        <prop>value</prop>
                      </properties>
                      """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                             <relativePath>../parent/pom.xml</relativePath>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    val p = readProject(module)
    assertEquals("value", p.name)
  }

  fun testExpandingPropertiesFromParentInSpecifiedLocationWithoutFile() {
    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <properties>
                        <prop>value</prop>
                      </properties>
                      """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                             <relativePath>../parent</relativePath>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    val p = readProject(module)
    assertEquals("value", p.name)
  }

  fun testExpandingPropertiesFromParentInRepository() {
    val repoPath = File(dir, "repository")
    repositoryPath = repoPath.path

    val parentFile = File(repoPath, "org/test/parent/1/parent-1.pom")
    parentFile.parentFile.mkdirs()
    FileUtil.writeToFile(parentFile,
                         createPomXml("""
                                        <groupId>org.test</groupId>
                                        <artifactId>parent</artifactId>
                                        <version>1</version>
                                        <properties>
                                          <prop>value</prop>
                                        </properties>
                                        """.trimIndent()).toByteArray(StandardCharsets.UTF_8))

    createProjectPom("""
                       <parent>
                         <groupId>org.test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{prop}</name>
                       """.trimIndent())

    val p = readProject(projectPom)
    assertEquals("value", p.name)
  }

  fun testExpandingPropertiesFromParentInInvalidLocation() {
    val parent = createModulePom("parent",
                                 """
                                                 <groupId>test</groupId>
                                                 <artifactId>parent</artifactId>
                                                 <version>1</version>
                                                 <properties>
                                                   <prop>value</prop>
                                                 </properties>
                                                 """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    val p = readProject(module, { coordinates -> if (MavenId("test", "parent", "1") == coordinates) parent else null }).mavenModel
    assertEquals("value", p.name)
  }

  fun testPropertiesFromParentInParentSection() {
    createProjectPom("""
                       <groupId>${'$'}{groupProp}</groupId>
                       <artifactId>parent</artifactId>
                       <version>${'$'}{versionProp}</version>
                       <properties>
                         <groupProp>test</groupProp>
                         <versionProp>1</versionProp>
                       </properties>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>${'$'}{groupProp}</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>${'$'}{versionProp}</version>
                                           </parent>
                                           <artifactId>module</artifactId>
                                           """.trimIndent())

    val id = readProject(module).mavenId
    assertEquals("test:module:1", id.groupId + ":" + id.artifactId + ":" + id.version)
  }

  fun testInheritingSettingsFromParentAndAlignCorrectly() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <build>
                         <directory>custom</directory>
                       </build>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    val p = readProject(module)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.parent, "custom"), p.build.directory)
  }

  fun testExpandingPropertiesAfterInheritingSettingsFromParent() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>subDir</prop>
                       </properties>
                       <build>
                         <directory>${'$'}{basedir}/${'$'}{prop}/custom</directory>
                       </build>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    val p = readProject(module)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.parent, "subDir/custom"), p.build.directory)
  }

  fun testExpandingPropertiesAfterInheritingSettingsFromParentProfiles() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <prop>subDir</prop>
                           </properties>
                           <build>
                             <directory>${'$'}{basedir}/${'$'}{prop}/custom</directory>
                           </build>
                         </profile>
                       </profiles>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    val p = readProject(module, "one")
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.parent, "subDir/custom"), p.build.directory)
  }

  fun testPropertiesFromProfilesXmlOldStyle() {
    createProjectPom("<name>\${prop}</name>")
    createProfilesXmlOldStyle("""
                                <profile>
                                  <id>one</id>
                                  <properties>
                                    <prop>foo</prop>
                                  </properties>
                                </profile>
                                """.trimIndent())

    var mavenProject = readProject(projectPom)
    assertEquals("\${prop}", mavenProject.name)

    mavenProject = readProject(projectPom, "one")
    assertEquals("foo", mavenProject.name)
  }

  fun testPropertiesFromProfilesXmlNewStyle() {
    createProjectPom("<name>\${prop}</name>")
    createProfilesXml("""
                        <profile>
                          <id>one</id>
                          <properties>
                            <prop>foo</prop>
                          </properties>
                        </profile>
                        """.trimIndent())

    var mavenProject = readProject(projectPom)
    assertEquals("\${prop}", mavenProject.name)

    mavenProject = readProject(projectPom, "one")
    assertEquals("foo", mavenProject.name)
  }

  fun testPropertiesFromSettingsXml() {
    createProjectPom("<name>\${prop}</name>")
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <properties>
                              <prop>foo</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())

    var mavenProject = readProject(projectPom)
    assertEquals("\${prop}", mavenProject.name)

    mavenProject = readProject(projectPom, "one")
    assertEquals("foo", mavenProject.name)
  }

  fun testDoNoInheritParentFinalNameIfUnspecified() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>2</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    val p = readProject(module, "one")
    assertEquals("module-2", p.build.finalName)
  }

  fun testDoInheritingParentFinalNameIfSpecified() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <build>
                         <finalName>xxx</finalName>
                       </build>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>2</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    val p = readProject(module, "one")
    assertEquals("xxx", p.build.finalName)
  }


  fun testInheritingParentProfiles() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>profileFromParent</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <profiles>
                                             <profile>
                                               <id>profileFromChild</id>
                                             </profile>
                                           </profiles>
                                           """.trimIndent())

    val p = readProject(module)
    assertOrderedElementsAreEqual(ContainerUtil.map(p.profiles, Function<MavenProfile, Any> { profile: MavenProfile -> profile.id }),
                                  "profileFromChild", "profileFromParent")
  }

  fun testCorrectlyCollectProfilesFromDifferentSources() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>profile</id>
                           <modules><module>parent</module></modules>
                         </profile>
                       </profiles>
                       """.trimIndent())

    val parentProfiles = createProfilesXml("""
                                                           <profile>
                                                             <id>profile</id>
                                                             <modules><module>parentProfiles</module></modules>
                                                           </profile>
                                                           """.trimIndent())

    val module = createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <profiles>
                                             <profile>
                                               <id>profile</id>
                                               <modules><module>pom</module></modules>
                                             </profile>
                                           </profiles>
                                           """.trimIndent())

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>profile</id>
                            <modules><module>settings</module></modules>
                          </profile>
                        </profiles>
                        """.trimIndent())

    val profiles = createProfilesXml("module",
                                     """
                                                     <profile>
                                                       <id>profile</id>
                                                       <modules><module>profiles</module></modules>
                                                     </profile>
                                                     """.trimIndent())

    var p = readProject(module)
    assertEquals(1, p.profiles.size)
    assertEquals("pom", p.profiles[0].modules[0])
    assertEquals("pom", p.profiles[0].source)

    createModulePom("module",
                    """
                      <groupId>test</groupId>
                      <artifactId>module</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                      </parent>
                      """.trimIndent())

    p = readProject(module)
    assertEquals(1, p.profiles.size)
    assertEquals("profiles", p.profiles[0].modules[0])
    assertEquals("profiles.xml", p.profiles[0].source)

    WriteCommandAction.writeCommandAction(project).run<IOException> { profiles.delete(this) }


    p = readProject(module)
    assertEquals(1, p.profiles.size)
    UsefulTestCase.assertEmpty("parent", p.profiles[0].modules)
    assertEquals("pom", p.profiles[0].source)

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       """.trimIndent())

    p = readProject(module)
    assertEquals(1, p.profiles.size)
    UsefulTestCase.assertEmpty("parentProfiles", p.profiles[0].modules)
    assertEquals("profiles.xml", p.profiles[0].source)

    WriteCommandAction.writeCommandAction(project).run<IOException> { parentProfiles.delete(null) }


    p = readProject(module)
    assertEquals(1, p.profiles.size)
    UsefulTestCase.assertEmpty("settings", p.profiles[0].modules)
    assertEquals("settings.xml", p.profiles[0].source)
  }

  fun testModulesAreNotInheritedFromParentsProfiles() {
    val p = createProjectPom("""
                                       <groupId>test</groupId>
                                       <artifactId>project</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <profiles>
                                        <profile>
                                         <id>one</id>
                                          <modules>
                                           <module>m</module>
                                          </modules>
                                        </profile>
                                       </profiles>
                                       """.trimIndent())

    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
       <groupId>test</groupId>
       <artifactId>project</artifactId>
       <version>1</version>
      </parent>
      """.trimIndent())

    UsefulTestCase.assertSize(1, readProject(p, "one").modules)
    UsefulTestCase.assertSize(0, readProject(m, "one").modules)
  }

  fun testActivatingProfilesByDefault() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <activeByDefault>false</activeByDefault>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("one")
  }

  fun testActivatingProfilesAfterResolvingInheritance() {
    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      """.trimIndent())

    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>parent/pom.xml</relativePath>
                       </parent>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("one")
  }

  fun testActivatingProfilesByOS() {
    val os = if (SystemInfo.isWindows) "windows" else if (SystemInfo.isMac) "mac" else "unix"

    createProjectPom("""<profiles>
  <profile>
    <id>one</id>
    <activation>
      <os><family>
$os</family></os>
    </activation>
  </profile>
  <profile>
    <id>two</id>
    <activation>
      <os><family>xxx</family></os>
    </activation>
  </profile>
</profiles>
""")

    assertActiveProfiles("one")
  }

  fun testActivatingProfilesByJdk() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <jdk>(,1.5)</jdk>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("one")
  }

  fun testActivatingProfilesByStrictJdkVersion() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <jdk>1.4</jdk>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles()
  }

  fun testActivatingProfilesByProperty() {
    createProjectPom("""<profiles>
  <profile>
    <id>one</id>
    <activation>
      <property>
        <name>os.name</name>
        <value>
${System.getProperty("os.name")}</value>
      </property>
    </activation>
  </profile>
  <profile>
    <id>two</id>
    <activation>
      <property>
        <name>os.name</name>
        <value>xxx</value>
      </property>
    </activation>
  </profile>
</profiles>
""")

    assertActiveProfiles("one")
  }

  fun testActivatingProfilesByEnvProperty() {
    val value = System.getenv(envVar)

    createProjectPom("""<profiles>
  <profile>
    <id>one</id>
    <activation>
      <property>
        <name>env.${envVar}</name>
        <value>
$value</value>
      </property>
    </activation>
  </profile>
  <profile>
    <id>two</id>
    <activation>
      <property>
        <name>ffffff</name>
        <value>ffffff</value>
      </property>
    </activation>
  </profile>
</profiles>
""")

    assertActiveProfiles("one")
  }

  fun testActivatingProfilesByFile() {
    createProjectSubFile("dir/file.txt")

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <file>
                               <exists>${'$'}{basedir}/dir/file.txt</exists>
                             </file>
                           </activation>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <file>
                               <missing>${'$'}{basedir}/dir/file.txt</missing>
                             </file>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("one")
  }

  fun testActivateDefaultProfileEventIfThereAreExplicitOnesButAbsent() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                         <profile>
                           <id>explicit</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles(mutableListOf("foofoofoo"), "default")
  }

  fun testDoNotActivateDefaultProfileIfThereAreActivatedImplicit() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                         <profile>
                           <id>implicit</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("implicit")
  }

  fun testActivatingImplicitProfilesEventWhenThereAreExplicitOnes() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>explicit</id>
                         </profile>
                         <profile>
                           <id>implicit</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles(mutableListOf("explicit"), "explicit", "implicit")
  }

  fun testAlwaysActivatingActiveProfilesInSettingsXml() {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>explicit</id>
                         </profile>
                         <profile>
                           <id>settings</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("settings")
    assertActiveProfiles(mutableListOf("explicit"), "explicit", "settings")
  }

  fun testAlwaysActivatingActiveProfilesInProfilesXml() {
    createFullProfilesXml("""
                            <?xml version="1.0"?>
                            <profilesXml>
                              <activeProfiles>
                                <activeProfile>profiles</activeProfile>
                              </activeProfiles>
                            </profilesXml>
                            """.trimIndent())

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>explicit</id>
                         </profile>
                         <profile>
                           <id>profiles</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("profiles")
    assertActiveProfiles(mutableListOf("explicit"), "explicit", "profiles")
  }

  fun testActivatingBothActiveProfilesInSettingsXmlAndImplicitProfiles() {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>implicit</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                         </profile>
                         <profile>
                           <id>settings</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("settings", "implicit")
  }

  fun testDoNotActivateDefaultProfilesWhenThereAreAlwaysOnProfilesInPomXml() {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                         <profile>
                           <id>settings</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("settings")
  }

  fun testActivateDefaultProfilesWhenThereAreActiveProfilesInSettingsXml() {
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>settings</id>
                          </profile>
                        </profiles>
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("default", "settings")
  }

  fun testActivateDefaultProfilesWhenThereAreActiveProfilesInProfilesXml() {
    createFullProfilesXml("""
                            <?xml version="1.0"?>
                            <profilesXml>
                              <profiles>
                                <profile>
                                  <id>profiles</id>
                                </profile>
                              </profiles>
                              <activeProfiles>
                                <activeProfile>profiles</activeProfile>
                              </activeProfiles>
                            </profilesXml>
                            """.trimIndent())

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("default", "profiles")
  }

  fun testActiveProfilesInSettingsXmlOrProfilesXmlThroughInheritance() {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createFullProfilesXml("parent",
                          """
                            <?xml version="1.0"?>
                            <profilesXml>
                              <activeProfiles>
                                <activeProfile>parent</activeProfile>
                              </activeProfiles>
                            </profilesXml>
                            """.trimIndent())

    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      """.trimIndent())

    createFullProfilesXml("""
                            <?xml version="1.0"?>
                            <profilesXml>
                              <activeProfiles>
                                <activeProfile>project</activeProfile>
                              </activeProfiles>
                            </profilesXml>
                            """.trimIndent())


    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>parent/pom.xml</relativePath>
                       </parent>
                       <profiles>
                         <profile>
                           <id>project</id>
                         </profile>
                         <profile>
                           <id>parent</id>
                         </profile>
                         <profile>
                           <id>settings</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    assertActiveProfiles("project", "settings")
  }

  fun `test custom source directories`() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>submodule</module>
                       </modules>
                       <build>
                         <sourceDirectory>src</sourceDirectory>
                         <testSourceDirectory>test</testSourceDirectory>
                         <resources>
                           <resource>
                             <directory>resources</directory>
                           </resource>
                         </resources>
                         <testResources>
                           <testResource>
                             <directory>testResources</directory>
                           </testResource>
                         </testResources>
                       </build>
                       """.trimIndent())

    val submodulePom = createModulePom("submodule",
                                       """
                      <groupId>test</groupId>
                      <artifactId>submodule</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      """.trimIndent())

    val submoduleModelBuild = readProject(submodulePom).build

    val submodulePath = submodulePom.parent.path
    val srcPaths = listOf(Path.of(submodulePath, "src").pathString)
    val testPaths = listOf(Path.of(submodulePath, "test").pathString)
    val resourcePaths = listOf(Path.of(submodulePath, "resources").pathString)
    val testResourcePaths = listOf(Path.of(submodulePath, "testResources").pathString)

    assertEquals(srcPaths, submoduleModelBuild.sources)
    assertEquals(testPaths, submoduleModelBuild.testSources)
    assertEquals(resourcePaths, submoduleModelBuild.resources.map { it.directory })
    assertEquals(testResourcePaths, submoduleModelBuild.testResources.map { it.directory })
  }

  fun `test custom source directories with maven wrapper`() {
    createProjectSubDirs(".mvn")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>submodule</module>
                       </modules>
                       <build>
                         <sourceDirectory>src</sourceDirectory>
                         <testSourceDirectory>test</testSourceDirectory>
                         <resources>
                           <resource>
                             <directory>resources</directory>
                           </resource>
                         </resources>
                         <testResources>
                           <testResource>
                             <directory>testResources</directory>
                           </testResource>
                         </testResources>
                       </build>
                       """.trimIndent())

    val submodulePom = createModulePom("submodule",
                                       """
                      <groupId>test</groupId>
                      <artifactId>submodule</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      """.trimIndent())

    val submoduleModelBuild = readProject(submodulePom).build

    val submodulePath = submodulePom.parent.path
    val srcPaths = listOf(Path.of(submodulePath, "src").pathString)
    val testPaths = listOf(Path.of(submodulePath, "test").pathString)
    val resourcePaths = listOf(Path.of(submodulePath, "resources").pathString)
    val testResourcePaths = listOf(Path.of(submodulePath, "testResources").pathString)

    assertEquals(srcPaths, submoduleModelBuild.sources)
    assertEquals(testPaths, submoduleModelBuild.testSources)
    assertEquals(resourcePaths, submoduleModelBuild.resources.map { it.directory })
    assertEquals(testResourcePaths, submoduleModelBuild.testResources.map { it.directory })
  }

  private fun assertActiveProfiles(vararg expected: String) {
    assertActiveProfiles(emptyList(), *expected)
  }

  private fun assertActiveProfiles(explicitProfiles: List<String>, vararg expected: String) {
    val result =
      readProject(projectPom, NullProjectLocator(), *ArrayUtilRt.toStringArray(explicitProfiles))
    assertUnorderedElementsAreEqual(result.activatedProfiles.enabledProfiles, *expected)
  }

  private fun assertParent(p: MavenModel,
                           groupId: String,
                           artifactId: String,
                           version: String) {
    val parent = p.parent.mavenId
    assertEquals(groupId, parent.groupId)
    assertEquals(artifactId, parent.artifactId)
    assertEquals(version, parent.version)
  }

  private fun assertResource(resource: MavenResource,
                             dir: String,
                             filtered: Boolean,
                             targetPath: String?,
                             includes: List<String>,
                             excludes: List<String>) {
    PlatformTestUtil.assertPathsEqual(dir, resource.directory)
    assertEquals(filtered, resource.isFiltered)
    PlatformTestUtil.assertPathsEqual(targetPath, resource.targetPath)
    assertOrderedElementsAreEqual(resource.includes, includes)
    assertOrderedElementsAreEqual(resource.excludes, excludes)
  }
}
