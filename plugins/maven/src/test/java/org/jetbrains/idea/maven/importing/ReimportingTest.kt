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
import java.util.concurrent.atomic.AtomicInteger

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
    createProjectPom("""
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

    createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """.trimIndent())

    updateAllProjects()
    assertModules("project", "m1", "m2", "m3")
  }

  @Test
  fun testRemovingObsoleteModule() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    importProjectAsync()
    assertModules("project", "m1")
  }

  @Test
  fun testDoesNotRemoveObsoleteModuleIfUserSaysNo() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    //configConfirmationForNoAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(false)

    importProjectAsync()
    if (supportsKeepingModulesFromPreviousImport()) {
      assertModules("project", "m1", "m2")
    }
    else {
      assertModules("project", "m1")
    }
  }

  @Test
  fun testDoesNotAskUserTwiceToRemoveTheSameModule() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())
    //AtomicInteger counter = configConfirmationForNoAnswer();
    val counter = AtomicInteger()
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(false)

    assertEquals(0, counter.get())

    importProjectAsync()
    if (null == MavenProjectLegacyImporter.getAnswerToDeleteObsoleteModulesQuestion()) {
      counter.incrementAndGet()
    }
    assertEquals(if (supportsKeepingModulesFromPreviousImport()) 1 else 0, counter.get())

    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(false)
    importProjectAsync()
    if (null == MavenProjectLegacyImporter.getAnswerToDeleteObsoleteModulesQuestion()) {
      counter.incrementAndGet()
    }
    assertEquals(if (supportsKeepingModulesFromPreviousImport()) 1 else 0, counter.get())
  }

  @Test
  fun testDoesNotAskToRemoveManuallyAdderModules() = runBlocking {
    createModule("userModule")
    assertModules("project", "m1", "m2", "userModule")

    val counter = configConfirmationForNoAnswer()

    importProjectAsync()

    assertEquals(0, counter.get())
    assertModules("project", "m1", "m2", "userModule")
  }

  @Test
  fun testRemovingAndCreatingModulesForAggregativeProjects() = runBlocking {
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      """.trimIndent())
    importProjectAsync()

    assertModules("project", "m1", "m2")

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)

    waitForImportWithinTimeout {
      mavenImporterSettings.setCreateModulesForAggregators(false)
    }
    if (supportsCreateAggregatorOption()) {
      assertModules(mn("project", "m2"))
    }
    else {
      assertModules("project", "m1", "m2")
    }

    waitForImportWithinTimeout {
      mavenImporterSettings.setCreateModulesForAggregators(true)
    }
    assertModules("project", "m1", "m2")
  }

  @Test
  fun testDoNotCreateModulesForNewlyCreatedAggregativeProjectsIfNotNecessary() = runBlocking {
    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    waitForImportWithinTimeout {
      mavenImporterSettings.setCreateModulesForAggregators(false)
    }

    createProjectPom("""
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

    createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      """.trimIndent())
    importProjectAsync()

    if (supportsCreateAggregatorOption()) {
      assertModules("m1", "m2")
    }
    else {
      assertModules("project", "m1", "m2", "m3")
    }
  }

  @Test
  fun testReimportingWithProfiles() = runBlocking {
    createProjectPom("""
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

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)

    importProjectWithProfiles("profile1")
    assertModules("project", "m1")

    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    importProjectWithProfiles("profile2")
    assertModules("project", "m2")
  }

  @Test
  fun testChangingDependencyTypeToTestJar() = runBlocking {
    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    val m1 = createModulePom("m1", createPomXmlWithModuleDependency("jar"))

    val m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectsAsync(m1, m2)
    val dep = OrderEntryUtil.findModuleOrderEntry(ModuleRootManager.getInstance(getModule("m1")), getModule("m2"))
    assertNotNull(dep)
    assertFalse(dep!!.isProductionOnTestDependency())

    createModulePom("m1", createPomXmlWithModuleDependency("test-jar"))
    importProjectsAsync(m1, m2)
    val dep2 = OrderEntryUtil.findModuleOrderEntry(ModuleRootManager.getInstance(getModule("m1")), getModule("m2"))
    assertNotNull(dep2)
    assertTrue(dep2!!.isProductionOnTestDependency())
  }

  @Test
  fun testSettingTargetLevel() = runBlocking {
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())
    updateAllProjects()
    assertEquals("1.8", CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(getModule("m1")))

    createModulePom("m1", """
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

    createModulePom("m1", """
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
    createModulePom("m1", """
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

    createModulePom("m2",
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

    //AtomicInteger counter = configConfirmationForNoAnswer();
    val counter = AtomicInteger()
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(false)
    importProjectAsync()
    if (null == MavenProjectLegacyImporter.getAnswerToDeleteObsoleteModulesQuestion()) {
      counter.incrementAndGet()
    }
    assertEquals(0, counter.get())
  }

  @Test
  fun testMoveModuleWithSystemScopedDependency() = runBlocking {
    zipFile {
      file("a.txt")
    }.generate(File(projectPath, "lib.jar"))
    createModulePom("m1", generatePomWithSystemDependency("../lib.jar"))
    importProjectAsync()

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir/m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    createModulePom("dir/m1", generatePomWithSystemDependency("../../lib.jar"))
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
    createProjectPom(String.format(parentPomTemplate, "1.8"))

    createModulePom("m1",
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

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule("project")))
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule("project")))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))

    createProjectPom(String.format(parentPomTemplate, "1.7"))

    importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(getModule("project")))
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(getModule("project")))
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
  }

  @Test
  fun testParentVersionProperty2() = runBlocking {
    createProjectPom("""
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
    createModulePom("m1", String.format(m1pomTemplate, "1.8"))

    val compilerConfiguration = CompilerConfiguration.getInstance(project)

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))

    createModulePom("m1", String.format(m1pomTemplate, "1.7"))

    importProjectAsync()
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
