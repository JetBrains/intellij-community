// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.*
import org.junit.Assume
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.pathString

class MavenProjectReaderTest : MavenProjectReaderTestCase() {
  @Test
  fun testBasics() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    importProjectAsync()
    val p = projectsTree.projects.first().mavenId

    assertEquals("test", p.groupId)
    assertEquals("project", p.artifactId)
    assertEquals("1", p.version)
  }

  @Test
  fun testInvalidXml() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    assertProblems(readProject(projectPom, NullProjectLocator()))

    updateProjectPom("""
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

  @Test
  fun testInvalidXmlCharData() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    assertProblems(readProject(projectPom, NullProjectLocator()))

    updateProjectPom("<name>a" + String(byteArrayOf(0x0), StandardCharsets.UTF_8) +
                     "a</name><fo" + String(byteArrayOf(0x0),
                                            StandardCharsets.UTF_8) +
                     "o></foo>\n")

    val result = readProject(projectPom, NullProjectLocator())
    assertProblems(result, "'pom.xml' has syntax errors")
    val p = result.mavenModel

    assertEquals("a0x0a", p.name)
  }

  @Test
  fun testInvalidParentXml() = runBlocking {
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

  @Test
  fun testProjectWithAbsentParentXmlIsValid() = runBlocking {
    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())
    assertProblems(readProject(projectPom, NullProjectLocator()))
  }

  @Test
  fun testProjectWithSelfParentIsInvalid() = runBlocking {
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

  @Test
  fun testInvalidSettingsXml() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    updateSettingsXml("<settings")

    assertProblems(readProject(projectPom, NullProjectLocator()), "'settings.xml' has syntax errors")
  }

  @Test
  fun testInvalidXmlWithNotClosedTag() = runBlocking {
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
  @Test
  fun testInvalidXmlWithWrongClosingTag() = runBlocking {
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

  @Test
  fun testEmpty() = runBlocking {
    createProjectPom("")

    importProjectAsync()
    val p = projectsTree.projects.first()

    assertEquals("Unknown", p.mavenId.groupId)
    assertEquals("Unknown", p.mavenId.artifactId)
    assertEquals("Unknown", p.mavenId.version)
  }

  @Test
  fun testSpaces() = runBlocking {
    createProjectPom("<name>foo bar</name>")

    importProjectAsync()
    val p = projectsTree.projects.first()
    assertEquals("foo bar", p.name)
  }

  @Test
  fun testNewLines() = runBlocking {
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

    importProjectAsync()
    val p = projectsTree.projects.first()
    assertEquals(MavenId("group", "artifact", "1"), p.mavenId)
  }

  @Test
  fun testCommentsWithNewLinesInTags() = runBlocking {
    createProjectPom("""
                       <groupId>test<!--a-->
                       </groupId><artifactId>
                       <!--a-->project</artifactId><version>1
                       <!--a--></version><name>
                       <!--a-->
                       </name>
                       """.trimIndent())

    importProjectAsync()
    val p = projectsTree.projects.first()
    val id = p.mavenId

    assertEquals("test", id.groupId)
    assertEquals("project", id.artifactId)
    assertEquals("1", id.version)
    assertEmpty(p.name)
  }

  @Test
  fun testTextInContainerTag() = runBlocking {
    createProjectPom("foo <name>name</name> bar")

    importProjectAsync()
    val p = projectsTree.projects.first()
    assertEquals("name", p.name)
  }

  @Test
  fun testDefaults() = runBlocking {
    createProjectPom("""
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        """.trimIndent())

    importProjectAsync()
    val p = projectsTree.findProject(projectPom)!!

    assertEquals("jar", p.packaging)

    forMaven3 {
      assertNull(p.name)
    }
    forMaven4 {
      assertEquals("project", p.name)
    }
    assertNull(p.parentId)

    assertEquals("project-1", p.finalName)
    assertEquals(null, p.defaultGoal)
    assertSize(1, p.sources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("src/main/java"), p.sources[0])
    assertSize(1, p.testSources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("src/test/java"), p.testSources[0])

    forModel40 {
      assertEquals(1, p.resources.size)
      assertResource(p.resources[0], pathFromBasedir("src/main/resources"),
                     false, null, emptyList(), emptyList())
      assertEquals(1, p.testResources.size)
      assertResource(p.testResources[0], pathFromBasedir("src/test/resources"),
                     false, null, emptyList(), emptyList())
    }

    forModel41 {
      assertEquals(2, p.resources.size)
      assertResource(p.resources[0], pathFromBasedir("src/main/resources"),
                     false, null, emptyList(), emptyList())
      assertResource(p.resources[1], pathFromBasedir("src/main/resources-filtered"),
                     true, null, emptyList(), emptyList())
      assertEquals(2, p.testResources.size)
      assertResource(p.testResources[0], pathFromBasedir("src/test/resources"),
                     false, null, emptyList(), emptyList())
      assertResource(p.testResources[1], pathFromBasedir("src/test/resources-filtered"),
                     true, null, emptyList(), emptyList())
    }

    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target"), p.buildDirectory)

    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target/classes"), p.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target/test-classes"), p.testOutputDirectory)
  }

  @Test
  fun testDefaultsForParent() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         dummy</parent>
                       """.trimIndent())

    importProjectAsync()
    val p = projectsTree.projects.first()

    assertParent(p, "Unknown", "Unknown", "Unknown")
  }

  @Test
  fun testTakingCoordinatesFromParent() = runBlocking {
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

  @Test
  fun testTakingVersionFromParentAutomaticallyDisabledInMaven3() = runBlocking {
    assumeMaven3()
    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())
    val subprojectPom = createModulePom("sub/subproject", """
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <relativePath>../../pom.xml</relativePath>
                       </parent>
                       """.trimIndent())

    val id = readProject(subprojectPom).mavenId

    assertEquals("test", id.groupId)
    assertEquals("Unknown", id.artifactId)
    assertEquals("Unknown", id.version)
    // TODO add a similar testcase for Maven 4: the version must be found in the parent POM using <relativePath>
    //assertEquals("1", id.version)
  }

  @Test
  fun testCustomSettings() = runBlocking {
    val parent = createModulePom("../parent", """
                <groupId>testParent</groupId>
                <artifactId>projectParent</artifactId>
                <version>2</version>
                <packaging>pom</packaging>
""".trimIndent())
    createProjectPom("""
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
    importProjectsAsync(projectPom, parent)
    val p = projectsTree.findProject(projectPom)!!

    assertEquals("pom", p.packaging)
    assertEquals("foo", p.name)

    assertParent(p, "testParent", "projectParent", "2")

    assertEquals("xxx", p.finalName)
    assertEquals("someGoal", p.defaultGoal)
    assertSize(1, p.sources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("mySrc"), p.sources[0])
    assertSize(1, p.testSources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myTestSrc"), p.testSources[0])
    assertEquals(1, p.resources.size)
    assertResource(p.resources[0], pathFromBasedir("myRes"),
                   true, "dir", listOf("**.properties"), listOf("**.xml"))
    assertEquals(1, p.testResources.size)
    assertResource(p.testResources[0], pathFromBasedir("myTestRes"),
                   false, null, listOf("**.properties"), emptyList())
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myOutput"), p.buildDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myClasses"), p.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myTestClasses"), p.testOutputDirectory)
  }

  @Test
  fun testOutputPathsAreBasedOnTargetPath() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <directory>my-target</directory>
                       </build>
                       """.trimIndent())

    importProjectAsync()
    val p = projectsTree.projects.first()

    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target"), p.buildDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/classes"), p.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/test-classes"), p.testOutputDirectory)
  }

  @Test
  fun testPathsWithProperties() = runBlocking {
    createProjectPom("""
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

    importProjectAsync()
    val p = projectsTree.projects.first()

    assertSize(1, p.sources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/mySrc"), p.sources[0])
    assertSize(1, p.testSources)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myTestSrc"), p.testSources[0])
    assertEquals(2, p.resources.size)
    assertResource(p.resources[0], pathFromBasedir("subDir/myRes"),
                   false, null, emptyList(), emptyList())
    assertResource(p.resources[1], pathFromBasedir("aaa/\${unexistingProperty}"),
                   false, null, emptyList(), emptyList())
    assertEquals(1, p.testResources.size)
    assertResource(p.testResources[0], pathFromBasedir("subDir/myTestRes"),
                   false, null, emptyList(), emptyList())
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myOutput"), p.buildDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myClasses"), p.outputDirectory)
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myTestClasses"), p.testOutputDirectory)
  }

  @Test
  fun testExpandingProperties() = runBlocking {
    createProjectPom("""
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
    importProjectAsync()
    val p = projectsTree.projects.first()

    assertEquals("value1", p.name)
    assertEquals("value2", p.packaging)
  }

  @Test
  fun testExpandingPropertiesRecursively() = runBlocking {
    createProjectPom("""
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
    importProjectAsync()
    val p = projectsTree.projects.first()

    assertEquals("value1", p.name)
    assertEquals("value12", p.packaging)
  }

  @Test
  fun testHandlingRecursiveProperties() = runBlocking {
    createProjectPom("""
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
    importProjectAsync()
    val p = projectsTree.projects.first()

    assertEquals("\${prop1}", p.name)
    assertEquals("\${prop2}", p.packaging)
  }

  @Test
  fun testHandlingRecursionProprielyAndDoNotForgetCoClearRecursionGuard() = runBlocking {
    val repoPath = dir.resolve("repository")
    repositoryPath = repoPath

    val parentFile = repoPath.resolve("test/parent/1/parent-1.pom")
    createFile(parentFile, createPomXml("""
                                                    <groupId>test</groupId>
                                                    <artifactId>parent</artifactId>
                                                    <version>1</version>
                                                    """.trimIndent()))

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

  @Test
  fun testDoNotGoIntoRecursionWhenTryingToResolveParentInDefaultPath() = runBlocking {
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

  @Test
  fun testExpandingSystemAndEnvProperties() = runBlocking {
    createProjectPom("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>      
  <name>${"$"}{java.home}</name>
  <packaging>${"$"}{env.${envVar}}</packaging>
  """.trimIndent())

    importProjectAsync()
    val p = projectsTree.projects.first()
    assertEquals(System.getProperty("java.home"), p.name)
    assertEquals(System.getenv(envVar), p.packaging)
  }

  @Test
  fun testExpandingPropertiesFromProfiles() = runBlocking {
    createProjectPom("""
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

    importProjectAsync()
    val p = projectsTree.projects.first()
    assertEquals("value1", p.name)
    assertEquals("\${prop2}", p.packaging)
  }

  @Test
  fun testExpandingPropertiesFromManuallyActivatedProfiles() = runBlocking {
    createProjectPom("""
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

    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("two"))
    importProjectAsync()
    val p = projectsTree.findProject(projectPom)!!
    assertEquals("\${prop1}", p.name)
    assertEquals("value2", p.packaging)
  }

  @Test
  fun testExpandingPropertiesFromParent() = runBlocking {
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
    importProjectsAsync(projectPom, module)
    val p = projectsTree.findProject(module)!!
    assertEquals("value", p.name)
  }

  @Test
  fun testDoNotExpandPropertiesFromParentWithWrongCoordinates() = runBlocking {
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

  @Test
  fun testExpandingPropertiesFromParentNotInVfs() = runBlocking {
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
    importProjectsAsync(projectPom, module)
    val p = projectsTree.findProject(module)!!
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromIndirectParent() = runBlocking {
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

    importProjectsAsync(projectPom, module, subModule)
    val p = projectsTree.projects.first { it.mavenId.artifactId == "subModule" }
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentInSpecifiedLocation() = runBlocking {
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

    importProjectsAsync(parent, module)
    val p = projectsTree.projects.first { it.mavenId.artifactId == "module" }
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentInSpecifiedLocationWithoutFile() = runBlocking {
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
    importProjectsAsync(parent, module)
    val p = projectsTree.projects.first { it.mavenId.artifactId == "module" }
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentInRepository() = runBlocking {
    val repoPath = dir.resolve("repository")
    repositoryPath = repoPath

    val parentFile = repoPath.resolve("org/test/parent/1/parent-1.pom")
    createFile(parentFile, createPomXml("""
                                        <groupId>org.test</groupId>
                                        <artifactId>parent</artifactId>
                                        <version>1</version>
                                        <properties>
                                          <prop>value</prop>
                                        </properties>
                                        """.trimIndent()))

    createProjectPom("""
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

    importProjectAsync()
    val p = projectsTree.projects.first()
    assertEquals("value", p.name)
  }

  @Test
  fun testExpandingPropertiesFromParentInInvalidLocation() = runBlocking {
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

    importProjectsAsync(parent, module)
    val p = projectsTree.projects.first { it.mavenId.artifactId == "module" }
    assertEquals("value", p.name)
  }

  @Test
  fun testPropertiesFromParentInParentSection() = runBlocking {
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

    importProjectsAsync(projectPom, module)
    val id = projectsTree.findProject(module)!!.mavenId
    assertEquals("test:module:1", id.groupId + ":" + id.artifactId + ":" + id.version)
  }

  @Test
  fun testInheritingSettingsFromParentAndAlignCorrectly() = runBlocking {
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
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    importProjectsAsync(projectPom, module)
    val p = projectsTree.findProject(module)!!
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.parent, "custom"), p.buildDirectory)
  }

  @Test
  fun testExpandingPropertiesAfterInheritingSettingsFromParent() = runBlocking {
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
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    importProjectsAsync(projectPom, module)
    val p = projectsTree.findProject(module)!!
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.parent, "subDir/custom"), p.buildDirectory)
  }

  @Test
  fun testExpandingPropertiesAfterInheritingSettingsFromParentProfiles() = runBlocking {
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
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """.trimIndent())

    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("one"))
    importProjectsAsync(projectPom, module)
    val p = projectsTree.findProject(module)!!
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.parent, "subDir/custom"), p.buildDirectory)
  }

  @Test
  fun testPropertiesFromSettingsXml() = runBlocking {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>      
      <name>${'$'}{prop}</name>
      """.trimIndent())

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

    importProjectAsync()
    var mavenProject = projectsTree.findProject(projectPom)!!
    assertEquals("\${prop}", mavenProject.name)

    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("one"))
    updateAllProjects()
    mavenProject = projectsTree.findProject(projectPom)!!
    assertEquals("foo", mavenProject.name)
  }

  @Test
  fun testDoNoInheritParentFinalNameIfUnspecified() = runBlocking {
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

    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("one"))
    importProjectsAsync(projectPom, module)
    val p = projectsTree.projects.first { it.mavenId.artifactId == "module" }
    assertEquals("module-2", p.finalName)
  }

  @Test
  fun testDoInheritingParentFinalNameIfSpecified() = runBlocking {
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

    projectsManager.explicitProfiles = MavenExplicitProfiles(listOf("one"))
    importProjectAsync(module)
    val p = projectsTree.findProject(module)!!
    assertEquals("xxx", p.finalName)
  }


  @Test
  fun testInheritingParentProfiles() = runBlocking {
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

  @Test
  fun testCorrectlyCollectProfilesFromDifferentSources() = runBlocking {
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

    importProjectAsync(module)
    var p = projectsTree.findProject(module)!!

    assertEquals(1, p.profilesIds.size)

    updateModulePom("module",
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

    updateAllProjects()
    p = projectsTree.findProject(module)!!
    assertEquals(1, p.profilesIds.size)

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       """.trimIndent())

    updateAllProjects()
    p = projectsTree.findProject(module)!!
    assertEquals(1, p.profilesIds.size)
  }

  @Test
  fun testModulesAreNotInheritedFromParentsProfiles() = runBlocking {
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

    importProjectWithProfiles("one")
    val mavenProject = projectsTree.findProject(p)!!
    val module = projectsTree.findProject(m)!!
    assertSize(1, mavenProject.modulePaths)
    assertSize(0, module.modulePaths)
  }

  @Test
  fun testActivatingProfilesByDefault() = runBlocking {
    createProjectPom("""
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
    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <packaging>pom</packaging>
                      """.trimIndent())

    createProjectPom("""
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

    createProjectPom("""
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
    createProjectPom("""
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
    createProjectPom("""
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
    createProjectPom("""
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
    val value = System.getenv(envVar)

    createProjectPom("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<profiles>
  <profile>
    <id>one</id>
    <activation>
      <property>
        <name>env.${envVar}</name>
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
    createProjectSubFile("dir/file.txt")

    createProjectPom("""
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
    createProjectPom("""
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
    createProjectPom("""
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
    createProjectPom("""
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
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
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
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
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
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
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
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <packaging>pom</packaging>
                      """.trimIndent())

    createProjectPom("""
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

  fun `test custom source directories with maven wrapper`() = runBlocking {
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

  private suspend fun assertActiveProfiles(vararg expected: String) {
    assertActiveProfiles(emptyList(), *expected)
  }

  private suspend fun assertActiveProfiles(explicitProfiles: List<String>, vararg expected: String) {
    importProjectWithProfiles(projectPom.toNioPath().toString(), *explicitProfiles.toTypedArray())
    val result = projectsTree.projects.first()
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
    assumeMaven4()
    useModel410()
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
    importProjectAsync("""
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
    assertModules("project")


    val submoduleModelBuild = readProject(submodulePom).build

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
