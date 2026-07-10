// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createFile
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.envVar
import com.intellij.maven.testFramework.fixtures.forMaven3
import com.intellij.maven.testFramework.fixtures.forMaven4
import com.intellij.maven.testFramework.fixtures.forModel40
import com.intellij.maven.testFramework.fixtures.forModel41
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectWithProfiles
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.pathFromBasedir
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.NullProjectLocator
import org.jetbrains.idea.maven.fixtures.assertProblems
import org.jetbrains.idea.maven.fixtures.readProject
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.model.MavenProfile
import org.jetbrains.idea.maven.model.MavenResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.pathString

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectReaderTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testBasics() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first().mavenId

    assertEquals("test", p.groupId)
    assertEquals("project", p.artifactId)
    assertEquals("1", p.version)
  }

  @Test
  fun testInvalidXml() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    maven.assertProblems(maven.readProject(maven.projectPom, NullProjectLocator()))

    maven.updateProjectPom("""
                       <foo>
                       </bar>
                       <<groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val result = maven.readProject(maven.projectPom, NullProjectLocator())
    maven.assertProblems(result, "'pom.xml' has syntax errors")
    val p = result.mavenModel.mavenId

    assertEquals("test", p.groupId)
    assertEquals("project", p.artifactId)
    assertEquals("1", p.version)
  }

  @Test
  fun testInvalidXmlCharData() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    maven.assertProblems(maven.readProject(maven.projectPom, NullProjectLocator()))

    maven.updateProjectPom("<name>a" + String(byteArrayOf(0x0), StandardCharsets.UTF_8) +
                     "a</name><fo" + String(byteArrayOf(0x0),
                                            StandardCharsets.UTF_8) +
                     "o></foo>\n")

    val result = maven.readProject(maven.projectPom, NullProjectLocator())
    maven.assertProblems(result, "'pom.xml' has syntax errors")
    val p = result.mavenModel

    assertEquals("a0x0a", p.name)
  }

  @Test
  fun testInvalidParentXml() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <foo
                       """.trimIndent())

    val module = maven.createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    maven.assertProblems(maven.readProject(module, NullProjectLocator()), "Parent 'test:parent:1' has problems")
  }

  @Test
  fun testProjectWithAbsentParentXmlIsValid() = runBlocking {
    maven.createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())
    maven.assertProblems(maven.readProject(maven.projectPom, NullProjectLocator()))
  }

  @Test
  fun testProjectWithSelfParentIsInvalid() = runBlocking {
    maven.createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.assertProblems(maven.readProject(maven.projectPom, NullProjectLocator()), "Self-inheritance found")
  }

  @Test
  fun testInvalidSettingsXml() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    maven.updateSettingsXml("<settings")

    maven.assertProblems(maven.readProject(maven.projectPom, NullProjectLocator()), "'settings.xml' has syntax errors")
  }

  @Test
  fun testInvalidXmlWithNotClosedTag() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1<name>foo</name>
                       """.trimIndent())

    val readResult = maven.readProject(maven.projectPom, NullProjectLocator())
    maven.assertProblems(readResult, "'pom.xml' has syntax errors")
    val p = readResult.mavenModel

    assertEquals("test", p.mavenId.groupId)
    assertEquals("project", p.mavenId.artifactId)
    assertEquals("Unknown", p.mavenId.version)
    assertEquals("foo", p.name)
  }

  // These tests fail until issue https://youtrack.jetbrains.com/issue/IDEA-272809 is fixed
  @Test
  fun testInvalidXmlWithWrongClosingTag() = runBlocking {
    //waiting for IDEA-272809
    Assumptions.assumeTrue(false)
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</vers>
                       <name>foo</name>
                       """.trimIndent())

    val readResult = maven.readProject(maven.projectPom, NullProjectLocator())
    maven.assertProblems(readResult, "'pom.xml' has syntax errors")
    val p = readResult.mavenModel

    assertEquals("test", p.mavenId.groupId)
    assertEquals("project", p.mavenId.artifactId)
    assertEquals("1", p.mavenId.version)
    assertEquals("foo", p.name)
  }

  @Test
  fun testEmpty() = runBlocking {
    maven.createProjectPom("")

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()

    assertEquals("Unknown", p.mavenId.groupId)
    assertEquals("Unknown", p.mavenId.artifactId)
    assertEquals("Unknown", p.mavenId.version)
  }

  @Test
  fun testSpaces() = runBlocking {
    maven.createProjectPom("<name>foo bar</name>")

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()
    assertEquals("foo bar", p.name)
  }

  @Test
  fun testNewLines() = runBlocking {
    maven.createProjectPom("""
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

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()
    assertEquals(MavenId("group", "artifact", "1"), p.mavenId)
  }

  @Test
  fun testCommentsWithNewLinesInTags() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test<!--a-->
                       </groupId><artifactId>
                       <!--a-->project</artifactId><version>1
                       <!--a--></version><name>
                       <!--a-->
                       </name>
                       """.trimIndent())

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()
    val id = p.mavenId

    assertEquals("test", id.groupId)
    assertEquals("project", id.artifactId)
    assertEquals("1", id.version)
    assertEmpty(p.name)
  }

  @Test
  fun testTextInContainerTag() = runBlocking {
    maven.createProjectPom("foo <name>name</name> bar")

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()
    assertEquals("name", p.name)
  }

  @Test
  fun testDefaults() = runBlocking {
    maven.createProjectPom("""
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        """.trimIndent())

    maven.importProjectAsync()
    val p = maven.projectsTree.findProject(maven.projectPom)!!

    assertEquals("jar", p.packaging)

    maven.forMaven3 {
      assertNull(p.name)
    }
    maven.forMaven4 {
      assertEquals("project", p.name)
    }
    assertNull(p.parentId)

    assertEquals("project-1", p.finalName)
    assertEquals(null, p.defaultGoal)
    assertSize(1, p.sources)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("src/main/java"), p.sources[0])
    assertSize(1, p.testSources)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("src/test/java"), p.testSources[0])

    maven.forModel40 {
      assertEquals(1, p.resources.size)
      assertResource(p.resources[0], maven.pathFromBasedir("src/main/resources"),
                     false, null, emptyList(), emptyList())
      assertEquals(1, p.testResources.size)
      assertResource(p.testResources[0], maven.pathFromBasedir("src/test/resources"),
                     false, null, emptyList(), emptyList())
    }

    maven.forModel41 {
      assertEquals(2, p.resources.size)
      assertResource(p.resources[0], maven.pathFromBasedir("src/main/resources"),
                     false, null, emptyList(), emptyList())
      assertResource(p.resources[1], maven.pathFromBasedir("src/main/resources-filtered"),
                     true, null, emptyList(), emptyList())
      assertEquals(2, p.testResources.size)
      assertResource(p.testResources[0], maven.pathFromBasedir("src/test/resources"),
                     false, null, emptyList(), emptyList())
      assertResource(p.testResources[1], maven.pathFromBasedir("src/test/resources-filtered"),
                     true, null, emptyList(), emptyList())
    }

    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("target"), p.buildDirectory)

    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("target/classes"), p.outputDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("target/test-classes"), p.testOutputDirectory)
  }

  @Test
  fun testDefaultsForParent() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         dummy</parent>
                       """.trimIndent())

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()

    assertParent(p, "Unknown", "Unknown", "Unknown")
  }

  @Test
  fun testTakingCoordinatesFromParent() = runBlocking {
    maven.createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())

    val id = maven.readProject(maven.projectPom).mavenId

    assertEquals("test", id.groupId)
    assertEquals("Unknown", id.artifactId)
    assertEquals("1", id.version)
  }

  @Test
  fun testTakingVersionFromParentAutomaticallyDisabledInMaven3() = runBlocking {
    maven.assumeMaven3()
    maven.createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())
    val subprojectPom = maven.createModulePom("sub/subproject", """
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <relativePath>../../pom.xml</relativePath>
                       </parent>
                       """.trimIndent())

    val id = maven.readProject(subprojectPom).mavenId

    assertEquals("test", id.groupId)
    assertEquals("Unknown", id.artifactId)
    assertEquals("Unknown", id.version)
    // TODO add a similar testcase for Maven 4: the version must be found in the parent POM using <relativePath>
    //assertEquals("1", id.version)
  }

  @Test
  fun testCustomSettings() = runBlocking {
    val parent = maven.createModulePom("../parent", """
                <groupId>testParent</groupId>
                <artifactId>projectParent</artifactId>
                <version>2</version>
                <packaging>pom</packaging>
""".trimIndent())
    maven.createProjectPom("""
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
        """.trimIndent())
    maven.importProjectsAsync(maven.projectPom, parent)
    val p = maven.projectsTree.findProject(maven.projectPom)!!

    assertEquals("pom", p.packaging)
    assertEquals("foo", p.name)

    assertParent(p, "testParent", "projectParent", "2")

    assertEquals("xxx", p.finalName)
    assertEquals("someGoal", p.defaultGoal)
    assertSize(1, p.sources)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("mySrc"), p.sources[0])
    assertSize(1, p.testSources)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("myTestSrc"), p.testSources[0])
    assertEquals(1, p.resources.size)
    assertResource(p.resources[0], maven.pathFromBasedir("myRes"),
                   true, "dir", listOf("**.properties"), listOf("**.xml"))
    assertEquals(1, p.testResources.size)
    assertResource(p.testResources[0], maven.pathFromBasedir("myTestRes"),
                   false, null, listOf("**.properties"), emptyList())
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("myOutput"), p.buildDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("myClasses"), p.outputDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("myTestClasses"), p.testOutputDirectory)
  }

  @Test
  fun testOutputPathsAreBasedOnTargetPath() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <directory>my-target</directory>
                       </build>
                       """.trimIndent())

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()

    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("my-target"), p.buildDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("my-target/classes"), p.outputDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("my-target/test-classes"), p.testOutputDirectory)
  }

  @Test
  fun testPathsWithProperties() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>subChild</artifactId>
                       <version>1</version>
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

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()

    assertSize(1, p.sources)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("subDir/mySrc"), p.sources[0])
    assertSize(1, p.testSources)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("subDir/myTestSrc"), p.testSources[0])
    assertEquals(2, p.resources.size)
    assertResource(p.resources[0], maven.pathFromBasedir("subDir/myRes"),
                   false, null, emptyList(), emptyList())
    assertResource(p.resources[1], maven.pathFromBasedir("aaa/\${unexistingProperty}"),
                   false, null, emptyList(), emptyList())
    assertEquals(1, p.testResources.size)
    assertResource(p.testResources[0], maven.pathFromBasedir("subDir/myTestRes"),
                   false, null, emptyList(), emptyList())
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("subDir/myOutput"), p.buildDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("subDir/myClasses"), p.outputDirectory)
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir("subDir/myTestClasses"), p.testOutputDirectory)
  }

  @Test
  fun testExpandingProperties() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>module</artifactId>
                       <version>1</version>
                       <properties>
                         <prop1>value1</prop1>
                         <prop2>value2</prop2>
                       </properties>
                       <name>${'$'}{prop1}</name>
                       <packaging>${'$'}{prop2}</packaging>
                       """.trimIndent())
    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()

    assertEquals("value1", p.name)
    assertEquals("value2", p.packaging)
  }

  @Test
  fun testExpandingPropertiesRecursively() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <prop1>value1</prop1>
                         <prop2>${'$'}{prop1}2</prop2>
                       </properties>
                       <name>${'$'}{prop1}</name>
                       <packaging>${'$'}{prop2}</packaging>
                       """.trimIndent())
    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()

    assertEquals("value1", p.name)
    assertEquals("value12", p.packaging)
  }

  @Test
  fun testHandlingRecursiveProperties() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <prop1>${'$'}{prop2}</prop1>
                         <prop2>${'$'}{prop1}</prop2>
                       </properties>
                       <name>${'$'}{prop1}</name>
                       <packaging>${'$'}{prop2}</packaging>
                       """.trimIndent())
    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()

    assertEquals("\${prop1}", p.name)
    assertEquals("\${prop2}", p.packaging)
  }

  @Test
  fun testHandlingRecursionProprielyAndDoNotForgetCoClearRecursionGuard() = runBlocking {
    val repoPath = maven.dir.resolve("repository")
    maven.repositoryPath = repoPath

    val parentFile = repoPath.resolve("test/parent/1/parent-1.pom")
    maven.createFile(parentFile, maven.createPomXml("""
                                                    <groupId>test</groupId>
                                                    <artifactId>parent</artifactId>
                                                    <version>1</version>
                                                    """.trimIndent()))

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>not-a-project</artifactId>
                       <version>1</version>
                       <parent>
                        <groupId>test</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                       </parent>
                       """.trimIndent())

    val child = maven.createModulePom("child",
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

    val readResult = maven.readProject(child, NullProjectLocator())
    maven.assertProblems(readResult)
  }

  @Test
  fun testDoNotGoIntoRecursionWhenTryingToResolveParentInDefaultPath() = runBlocking {
    val child = maven.createModulePom("child",
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

    maven.createProjectPom("""
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

    val readResult = maven.readProject(child, NullProjectLocator())
    maven.assertProblems(readResult)
  }

  @Test
  fun testExpandingSystemAndEnvProperties() = runBlocking {
    maven.createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>      
  <name>${"$"}{java.home}</name>
  <packaging>${"$"}{env.${maven.envVar}}</packaging>
  """.trimIndent())

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()
    assertEquals(System.getProperty("java.home"), p.name)
    assertEquals(System.getenv(maven.envVar), p.packaging)
  }

  @Test
  fun testExpandingPropertiesFromProfiles() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()
    assertEquals("value1", p.name)
    assertEquals("\${prop2}", p.packaging)
  }

  @Test
  fun testExpandingPropertiesFromManuallyActivatedProfiles() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

    maven.importProjectAsync()
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("two"), listOf("one"))
    maven.updateAllProjects()
    val p = maven.projectsTree.findProject(maven.projectPom)!!
    assertEquals("\${prop1}", p.name)
    assertEquals("value2", p.packaging)
  }

  @Test
  fun testExpandingPropertiesFromParent() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """.trimIndent())

    val module = maven.createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())
    maven.importProjectsAsync(maven.projectPom, module)
    val p = maven.projectsTree.findProject(module)!!
    assertEquals("value", p.name)
  }

  @Test
  fun testDoNotExpandPropertiesFromParentWithWrongCoordinates() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """.trimIndent())

    val module = maven.createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>invalid</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    val p = maven.readProject(module)
    assertEquals("\${prop}", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentNotInVfs() = runBlocking {
    maven.createProjectPom("""
                  <groupId>test</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <properties>
                    <prop>value</prop>
                  </properties>
                  """.trimIndent())

    val module = maven.createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())
    maven.importProjectsAsync(maven.projectPom, module)
    val p = maven.projectsTree.findProject(module)!!
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromIndirectParent() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """.trimIndent())

    val module = maven.createModulePom("module",
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

    val subModule = maven.createModulePom("module/subModule",
                                    """
                                              <groupId>test</groupId>
                                              <artifactId>subModule</artifactId>
                                              <version>1</version>
                                              <parent>
                                                <groupId>test</groupId>
                                                <artifactId>module</artifactId>
                                                <version>1</version>
                                              </parent>
                                              <name>${'$'}{prop}</name>
                                              """.trimIndent())

    maven.importProjectsAsync(maven.projectPom, module, subModule)
    val p = maven.projectsTree.projects.first { it.mavenId.artifactId == "subModule" }
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentInSpecifiedLocation() = runBlocking {
    val parent = maven.createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <properties>
                        <prop>value</prop>
                      </properties>
                      """.trimIndent())

    val module = maven.createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                             <relativePath>../parent/pom.xml</relativePath>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    maven.importProjectsAsync(parent, module)
    val p = maven.projectsTree.projects.first { it.mavenId.artifactId == "module" }
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentInSpecifiedLocationWithoutFile() = runBlocking {
    val parent = maven.createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <properties>
                        <prop>value</prop>
                      </properties>
                      """.trimIndent())

    val module = maven.createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                             <relativePath>../parent</relativePath>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())
    maven.importProjectsAsync(parent, module)
    val p = maven.projectsTree.projects.first { it.mavenId.artifactId == "module" }
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentInRepository() = runBlocking {
    val repoPath = maven.dir.resolve("repository")
    maven.repositoryPath = repoPath

    val parentFile = repoPath.resolve("org/test/parent/1/parent-1.pom")
    maven.createFile(parentFile, maven.createPomXml("""
                                        <groupId>org.test</groupId>
                                        <artifactId>parent</artifactId>
                                        <version>1</version>
                                        <properties>
                                          <prop>value</prop>
                                        </properties>
                                        """.trimIndent()))

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>org.test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${'$'}{prop}</name>
                       """.trimIndent())

    maven.importProjectAsync()
    val p = maven.projectsTree.projects.first()
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentInInvalidLocation() = runBlocking {
    val parent = maven.createModulePom("parent",
                                 """
                                                 <groupId>test</groupId>
                                                 <artifactId>parent</artifactId>
                                                 <version>1</version>
                                                 <properties>
                                                   <prop>value</prop>
                                                 </properties>
                                                 """.trimIndent())

    val module = maven.createModulePom("module",
                                 """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${'$'}{prop}</name>
                                           """.trimIndent())

    maven.importProjectsAsync(parent, module)
    val p = maven.projectsTree.projects.first { it.mavenId.artifactId == "module" }
    assertEquals("value", p.name)
  }

  @Test
  fun testPropertiesFromParentInParentSection() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'groupId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>${'$'}{groupProp}</groupId>
                       <artifactId>parent</artifactId>
                       <version>${'$'}{versionProp}</version>
                       <properties>
                         <groupProp>test</groupProp>
                         <versionProp>1</versionProp>
                       </properties>
                       """.trimIndent())

    val module = maven.createModulePom("module",
                                 """
                                           <parent>
                                             <groupId>${'$'}{groupProp}</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>${'$'}{versionProp}</version>
                                           </parent>
                                           <artifactId>module</artifactId>
                                           """.trimIndent())

    maven.importProjectsAsync(maven.projectPom, module)
    val id = maven.projectsTree.findProject(module)!!.mavenId
    assertEquals("test:module:1", id.groupId + ":" + id.artifactId + ":" + id.version)
  }

  @Test
  fun testInheritingSettingsFromParentAndAlignCorrectly() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <build>
                         <directory>custom</directory>
                       </build>
                       """.trimIndent())

    val module = maven.createModulePom("module",
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

    maven.importProjectsAsync(maven.projectPom, module)
    val p = maven.projectsTree.findProject(module)!!
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir(module.parent, "custom"), p.buildDirectory)
  }

  @Test
  fun testExpandingPropertiesAfterInheritingSettingsFromParent() = runBlocking {
    maven.createProjectPom("""
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

    val module = maven.createModulePom("module",
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

    maven.importProjectsAsync(maven.projectPom, module)
    val p = maven.projectsTree.findProject(module)!!
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir(module.parent, "subDir/custom"), p.buildDirectory)
  }

  @Test
  fun testExpandingPropertiesAfterInheritingSettingsFromParentProfiles() = runBlocking {
    maven.createProjectPom("""
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

    val module = maven.createModulePom("module",
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

    maven.importProjectsAsync(maven.projectPom, module)
    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("one"))
    maven.updateAllProjects()
    val p = maven.projectsTree.findProject(module)!!
    PlatformTestUtil.assertPathsEqual(maven.pathFromBasedir(module.parent, "subDir/custom"), p.buildDirectory)
  }

  @Test
  fun testPropertiesFromSettingsXml() = runBlocking {
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>      
      <name>${'$'}{prop}</name>
      """.trimIndent())

    maven.updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <properties>
                              <prop>foo</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """.trimIndent())

    maven.importProjectAsync()
    var mavenProject = maven.projectsTree.findProject(maven.projectPom)!!
    assertEquals("\${prop}", mavenProject.name)

    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("one"))
    maven.updateAllProjects()
    mavenProject = maven.projectsTree.findProject(maven.projectPom)!!
    assertEquals("foo", mavenProject.name)
  }

  @Test
  fun testDoNoInheritParentFinalNameIfUnspecified() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       """.trimIndent())

    val module = maven.createModulePom("module",
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

    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("one"))
    maven.importProjectsAsync(maven.projectPom, module)
    val p = maven.projectsTree.projects.first { it.mavenId.artifactId == "module" }
    assertEquals("module-2", p.finalName)
  }

  @Test
  fun testDoInheritingParentFinalNameIfSpecified() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <build>
                         <finalName>xxx</finalName>
                       </build>
                       """.trimIndent())

    val module = maven.createModulePom("module",
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

    maven.projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("one"))
    maven.importProjectAsync(module)
    val p = maven.projectsTree.findProject(module)!!
    assertEquals("xxx", p.finalName)
  }


  @Test
  fun testInheritingParentProfiles() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>profileFromParent</id>
                         </profile>
                       </profiles>
                       """.trimIndent())

    val module = maven.createModulePom("module",
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

    val p = maven.readProject(module)
    assertOrderedElementsAreEqual(ContainerUtil.map(p.profiles, Function<MavenProfile, Any> { profile: MavenProfile -> profile.id }),
                                  "profileFromChild", "profileFromParent")
  }

  @Test
  fun testCorrectlyCollectProfilesFromDifferentSources() = runBlocking {
    maven.createProjectPom("""
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

    val module = maven.createModulePom("module",
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

    maven.updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>profile</id>
                            <modules><module>settings</module></modules>
                          </profile>
                        </profiles>
                        """.trimIndent())

    maven.importProjectAsync(module)
    var p = maven.projectsTree.findProject(module)!!

    assertEquals(1, p.profilesIds.size)

    maven.updateModulePom("module",
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

    maven.updateAllProjects()
    p = maven.projectsTree.findProject(module)!!
    assertEquals(1, p.profilesIds.size)

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       """.trimIndent())

    maven.updateAllProjects()
    p = maven.projectsTree.findProject(module)!!
    assertEquals(1, p.profilesIds.size)
  }

  @Test
  fun testModulesAreNotInheritedFromParentsProfiles() = runBlocking {
    val p = maven.createProjectPom("""
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

    val m = maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
       <groupId>test</groupId>
       <artifactId>project</artifactId>
       <version>1</version>
      </parent>
      """.trimIndent())

    maven.importProjectWithProfiles("one")
    val mavenProject = maven.projectsTree.findProject(p)!!
    val module = maven.projectsTree.findProject(m)!!
    assertSize(1, mavenProject.modulePaths)
    assertSize(0, module.modulePaths)
  }

  @Test
  fun testActivatingProfilesByDefault() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActivatingProfilesAfterResolvingInheritance() = runBlocking {
    maven.createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <packaging>pom</packaging>
                      """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActivatingProfilesByOS() = runBlocking {
    val os = if (SystemInfo.isWindows) "windows" else if (SystemInfo.isMac) "mac" else "unix"

    maven.createProjectPom("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<profiles>
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

  @Test
  fun testActivatingProfilesByJdk() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActivatingProfilesByStrictJdkVersion() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActivatingProfilesByProperty() = runBlocking {
    maven.createProjectPom("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<profiles>
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

  @Test
  fun testActivatingProfilesByEnvProperty() = runBlocking {
    val value = System.getenv(maven.envVar)

    maven.createProjectPom("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<profiles>
  <profile>
    <id>one</id>
    <activation>
      <property>
        <name>env.${maven.envVar}</name>
        <value>$value</value>
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

  @Test
  fun testActivatingProfilesByFile() = runBlocking {
    maven.createProjectSubFile("dir/file.txt")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActivateDefaultProfileEventIfThereAreExplicitOnesButAbsent() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testDoNotActivateDefaultProfileIfThereAreActivatedImplicit() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActivatingImplicitProfilesEventWhenThereAreExplicitOnes() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testAlwaysActivatingActiveProfilesInSettingsXml() = runBlocking {
    maven.updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActivatingBothActiveProfilesInSettingsXmlAndImplicitProfiles() = runBlocking {
    maven.updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testDoNotActivateDefaultProfilesWhenThereAreAlwaysOnProfilesInPomXml() = runBlocking {
    maven.updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActivateDefaultProfilesWhenThereAreActiveProfilesInSettingsXml() = runBlocking {
    maven.updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>settings</id>
                          </profile>
                        </profiles>
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

  @Test
  fun testActiveProfilesInSettingsXmlThroughInheritance() = runBlocking {
    maven.updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    maven.createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <packaging>pom</packaging>
                      """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
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

    assertActiveProfiles("settings")
  }

  fun `test custom source directories`() = runBlocking {
    maven.createProjectPom("""
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

    val submodulePom = maven.createModulePom("submodule",
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

    val submoduleModelBuild = maven.readProject(submodulePom).build

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

  fun `test custom source directories with maven wrapper`() = runBlocking {
    maven.createProjectSubDirs(".mvn")

    maven.createProjectPom("""
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

    val submodulePom = maven.createModulePom("submodule",
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

    val submoduleModelBuild = maven.readProject(submodulePom).build

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

  private suspend fun assertActiveProfiles(vararg expected: String) {
    assertActiveProfiles(emptyList(), *expected)
  }

  private suspend fun assertActiveProfiles(explicitProfiles: List<String>, vararg expected: String) {
    maven.importProjectWithProfiles(maven.projectPom.toNioPath().toString(), *explicitProfiles.toTypedArray())
    val result = maven.projectsTree.projects.first()
    assertUnorderedElementsAreEqual(result.activatedProfilesIds.enabledProfiles, *expected)
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

  private fun assertParent(p: MavenProject,
                           groupId: String,
                           artifactId: String,
                           version: String) {
    val parent = p.parentId!!
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

  fun `test custom source directories 410 model`() = runBlocking {
    maven.assumeMaven4()
    maven.assumeModel_4_1_0("test requires model 4.1.0")
    val submodulePom = maven.createModulePom("submodule",
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
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <subprojects>
        <subproject>submodule</subproject>
      </subprojects>
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


    val submoduleModelBuild = maven.readProject(submodulePom).build

    val submodulePath = submodulePom.parent.path
    val srcPaths = listOf(Path.of(submodulePath, "my/src").pathString)
    val testPaths = listOf(Path.of(submodulePath, "my/testsrc").pathString)
    val resourcePaths = listOf(Path.of(submodulePath, "my/res").pathString)
    val testResourcePaths = listOf(Path.of(submodulePath, "my/testsrc").pathString)

    assertEquals(srcPaths, submoduleModelBuild.sources)
    assertEquals(testPaths, submoduleModelBuild.testSources)
    assertEquals(resourcePaths, submoduleModelBuild.resources.map { it.directory })
    assertEquals(testResourcePaths, submoduleModelBuild.testResources.map { it.directory })
  }
}
