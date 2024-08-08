// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.io.zipFile
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.Test
import java.io.File

class ReimportingTest : MavenMultiVersionImportingTestCase() {
  override fun setUp() = runBlocking {
    super.setUp()
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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
  }

  @Test
  fun testAddingNewModule() = runBlocking {
    updateProjectPom("""
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

    updateModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """.trimIndent())

    updateAllProjects()
    assertModules("project", "m1", "m2", "m3")
  }

  @Test
  fun testRemovingObsoleteModule() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1")
  }

  @Test
  fun testDoesNotRemoveObsoleteModuleIfUserSaysNo() = runBlocking {
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1")
  }

  @Test
  fun testReimportingWithProfiles() = runBlocking {
    updateProjectPom("""
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

    importProjectWithProfiles("profile1")
    assertModules("project", "m1")

    importProjectWithProfiles("profile2")
    assertModules("project", "m2")
  }

  @Test
  fun testChangingDependencyTypeToTestJar() = runBlocking {
    val m1 = updateModulePom("m1", createPomXmlWithModuleDependency("jar"))

    val m2 = updateModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectsAsync(m1, m2)
    val dep = OrderEntryUtil.findModuleOrderEntry(ModuleRootManager.getInstance(getModule("m1")), getModule("m2"))
    assertNotNull(dep)
    assertFalse(dep!!.isProductionOnTestDependency())

    updateModulePom("m1", createPomXmlWithModuleDependency("test-jar"))
    importProjectsAsync(m1, m2)
    val dep2 = OrderEntryUtil.findModuleOrderEntry(ModuleRootManager.getInstance(getModule("m1")), getModule("m2"))
    assertNotNull(dep2)
    assertTrue(dep2!!.isProductionOnTestDependency())
  }

  @Test
  fun testSettingTargetLevel() = runBlocking {
    updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    updateAllProjects()
    assertEquals("1.8", CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(getModule("m1")))

    updateModulePom("m1", """
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
    updateAllProjects()
    assertEquals("1.3", CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(getModule("m1")))

    updateModulePom("m1", """
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

    updateAllProjects()
    assertEquals("1.6", CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(getModule("m1")))

    // after configuration/target element delete in maven-compiler-plugin CompilerConfiguration#getBytecodeTargetLevel should be also updated
    updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    updateAllProjects()
    assertEquals("1.8", CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(getModule("m1")))
  }

  @Test
  fun testReimportingWhenModuleHaveRootOfTheParent() = runBlocking {
    createProjectSubDir("m1/res")
    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    updateModulePom("m2",
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

    importProjectAsync()
  }

  @Test
  fun testMoveModuleWithSystemScopedDependency() = runBlocking {
    zipFile {
      file("a.txt")
    }.generate(File(projectPath, "lib.jar"))
    updateModulePom("m1", generatePomWithSystemDependency("../lib.jar"))
    importProjectAsync()

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir/m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    updateModulePom("dir/m1", generatePomWithSystemDependency("../../lib.jar"))
    importProjectAsync()
    assertModules("project", "m1", "m2")
  }

  @Test
  fun testParentVersionProperty() = runBlocking {
    if (ignore()) return@runBlocking
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
    updateProjectPom(String.format(parentPomTemplate, "1.8"))

    updateModulePom("m1",
                    """
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>${'$'}{my.parent.version}</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      <version>${'$'}{parent.version}</version>
                      """.trimIndent())

    val compilerConfiguration = CompilerConfiguration.getInstance(project)

    importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule("project")))
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule("project")))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))

    updateProjectPom(String.format(parentPomTemplate, "1.7"))

    importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(getModule("project")))
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(getModule("project")))
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
  }

  @Test
  fun testParentVersionProperty2() = runBlocking {
    needFixForMaven4()
    updateProjectPom("""
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
        <groupId>${'$'}{my.parent.groupId}</groupId>
        <artifactId>project</artifactId>
        <version>${'$'}{my.parent.version}</version>
      </parent>
      <artifactId>m1</artifactId>
      <version>${'$'}{my.parent.version}</version>
      <properties>
        <my.parent.version>1</my.parent.version>
        <my.parent.groupId>test</my.parent.groupId>
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
    updateModulePom("m1", String.format(m1pomTemplate, "1.8"))

    val compilerConfiguration = CompilerConfiguration.getInstance(project)

    updateAllProjects()
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))

    updateModulePom("m1", String.format(m1pomTemplate, "1.7"))

    updateAllProjects()
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
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
