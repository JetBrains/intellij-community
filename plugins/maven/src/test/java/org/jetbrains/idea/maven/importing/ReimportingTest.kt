// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.getExpectedTargetLanguageLevel
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectWithProfiles
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.zipFile
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ReimportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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
  }

  @Test
  fun testAddingNewModule() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                         <module>m3</module>
                       </modules>
                       """.trimIndent())

    maven.updateModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.updateAllProjects()
    maven.assertModules("project", "m1", "m2", "m3")
  }

  @Test
  fun testRemovingObsoleteModule() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1")
  }

  @Test
  fun testDoesNotRemoveObsoleteModuleIfUserSaysNo() = runBlocking {
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1")
  }

  @Test
  fun testReimportingWithProfiles() = runBlocking {
    maven.assumeModel_4_0_0("Autoscanning")
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>profile1</id>
                           <activation>
                             <activeByDefault>false</activeByDefault>
                           </activation>
                           <modules>
                             <module>m1</module>
                           </modules>
                         </profile>
                         <profile>
                           <id>profile2</id>
                           <activation>
                             <activeByDefault>false</activeByDefault>
                           </activation>
                           <modules>
                             <module>m2</module>
                           </modules>
                         </profile>
                       </profiles>
                       """.trimIndent())

    maven.importProjectWithProfiles("profile1")
    maven.assertModules("project", "m1")

    maven.importProjectWithProfiles("profile2")
    maven.assertModules("project", "m2")
  }

  @Test
  fun testChangingDependencyTypeToTestJar() = runBlocking {
    val m1 = maven.updateModulePom("m1", createPomXmlWithModuleDependency("jar"))

    val m2 = maven.updateModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectsAsync(m1, m2)
    val dep = OrderEntryUtil.findModuleOrderEntry(ModuleRootManager.getInstance(maven.getModule("m1")), maven.getModule("m2"))
    assertNotNull(dep)
    assertFalse(dep!!.isProductionOnTestDependency())

    maven.updateModulePom("m1", createPomXmlWithModuleDependency("test-jar"))
    maven.importProjectsAsync(m1, m2)
    val dep2 = OrderEntryUtil.findModuleOrderEntry(ModuleRootManager.getInstance(maven.getModule("m1")), maven.getModule("m2"))
    assertNotNull(dep2)
    assertTrue(dep2!!.isProductionOnTestDependency())
  }

  @Test
  fun testSettingTargetLevel() = runBlocking {
    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.updateAllProjects()
    assertEquals(maven.getExpectedTargetLanguageLevel(), CompilerConfiguration.getInstance(maven.project).getBytecodeTargetLevel(maven.getModule("m1")))

    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
              <configuration>
                <target>1.3</target>
              </configuration>
           </plugin>
        </plugins>
      </build>
      """.trimIndent())
    maven.updateAllProjects()
    assertEquals("1.3", CompilerConfiguration.getInstance(maven.project).getBytecodeTargetLevel(maven.getModule("m1")))

    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
              <configuration>
                <target>1.6</target>
              </configuration>
           </plugin>
        </plugins>
      </build>
      """.trimIndent())

    maven.updateAllProjects()
    assertEquals("1.6", CompilerConfiguration.getInstance(maven.project).getBytecodeTargetLevel(maven.getModule("m1")))

    // after configuration/target element delete in maven-compiler-plugin CompilerConfiguration#getBytecodeTargetLevel should be also updated
    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.updateAllProjects()
    assertEquals(maven.getExpectedTargetLanguageLevel(), CompilerConfiguration.getInstance(maven.project).getBytecodeTargetLevel(maven.getModule("m1")))
  }

  @Test
  fun testReimportingWhenModuleHaveRootOfTheParent() = runBlocking {
    maven.createProjectSubDir("m1/res")
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.updateModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      <build>
                        <resources>
                          <resource><directory>../m1</directory></resource>
                        </resources>
                      </build>
                      """.trimIndent())

    maven.importProjectAsync()
  }

  @Test
  fun testMoveModuleWithSystemScopedDependency() = runBlocking {
    zipFile {
      file("a.txt")
    }.generate(maven.projectPath.resolve("lib.jar"))
    maven.updateModulePom("m1", generatePomWithSystemDependency("../lib.jar"))
    maven.importProjectAsync()

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir/m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    maven.updateModulePom("dir/m1", generatePomWithSystemDependency("../../lib.jar"))
    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")
  }

  @Test
  fun testParentVersionProperty() = runBlocking {
    maven.assumeModel_4_0_0("[FATAL] 'version' contains an expression but should be a constant.")
    val parentPomTemplate =

      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>${'$'}{my.parent.version}</version>
        <packaging>pom</packaging>
        <modules>
          <module>m1</module>
        </modules>
        <properties>
          <my.parent.version>1</my.parent.version>
        </properties>
        <build>
          <plugins>
            <plugin>
              <artifactId>maven-compiler-plugin</artifactId>
              <version>3.1</version>
              <configuration>
                <source>%s</source>
                <target>%<s</target>
              </configuration>
            </plugin>
          </plugins>
        </build>
        """.trimIndent()
    maven.updateProjectPom(String.format(parentPomTemplate, "1.8"))

    maven.updateModulePom("m1",
                    """
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>${'$'}{my.parent.version}</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      <version>${'$'}{parent.version}</version>
                      """.trimIndent())

    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project)

    maven.importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(maven.getModule("project")))
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(maven.getModule(maven.mn("project", "m1"))))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(maven.getModule("project")))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m1"))))

    maven.updateProjectPom(String.format(parentPomTemplate, "1.7"))

    maven.importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(maven.getModule("project")))
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(maven.getModule(maven.mn("project", "m1"))))
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(maven.getModule("project")))
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m1"))))
  }

  @Test
  fun testParentVersionProperty2() = runBlocking {
    maven.assumeModel_4_0_0("[FATAL] 'version' contains an expression but should be a constant.")
    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    val m1pomTemplate = """
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <artifactId>m1</artifactId>
      <version>${'$'}{my.parent.version}</version>
      <properties>
        <my.parent.version>1</my.parent.version>
      </properties>
      <build>
        <plugins>
          <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.1</version>
            <configuration>
              <source>%s</source>
              <target>%s</target>
            </configuration>
          </plugin>
        </plugins>
      </build>
      """.trimIndent()
    maven.updateModulePom("m1", String.format(m1pomTemplate, "1.8", "1.8"))

    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project)

    maven.updateAllProjects()
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(maven.getModule(maven.mn("project", "m1"))))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m1"))))

    maven.updateModulePom("m1", String.format(m1pomTemplate, "17", "17"))

    maven.updateAllProjects()
    assertEquals(LanguageLevel.JDK_17, getEffectiveLanguageLevel(maven.getModule(maven.mn("project", "m1"))))
    assertEquals("17", compilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m1"))))
  }

  private suspend fun getEffectiveLanguageLevel(module: Module): LanguageLevel {
    return readAction {
      LanguageLevelUtil.getEffectiveLanguageLevel(module)
    }
  }

  companion object {
    private fun createPomXmlWithModuleDependency(dependencyType: String): String {
      return """<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>test</groupId>
    <artifactId>m2</artifactId>
    <version>1</version>
    <type>
$dependencyType</type>
  </dependency>
</dependencies>"""
    }

    @Language(value = "XML", prefix = "<project>", suffix = "</project>")
    private fun generatePomWithSystemDependency(relativePath: String): String {
      return """<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>
<dependencies>
   <dependency>
      <groupId>my-group</groupId>
      <artifactId>lib</artifactId>
      <scope>system</scope>
      <version>1</version>
      <systemPath>${"$"}{basedir}/$relativePath</systemPath>
   </dependency>
</dependencies>"""
    }
  }
}
