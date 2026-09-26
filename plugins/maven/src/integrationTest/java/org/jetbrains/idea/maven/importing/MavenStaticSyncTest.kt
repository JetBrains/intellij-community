// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContentRootSources
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.assertModuleLibDepScope
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertProjectLibraryCoordinates
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createPomFile
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.repositoryPathCanonical
import com.intellij.maven.testFramework.utils.RealMavenPreventionFixture
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.importProjectStaticSync
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenStaticSyncTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  


  private lateinit var noRealMaven: RealMavenPreventionFixture

  @BeforeEach
  fun setUp() {
    noRealMaven = RealMavenPreventionFixture(maven.project)
    noRealMaven.setUp()
  }

  @AfterEach
  fun tearDown() {
    noRealMaven.tearDown()
  }

  @Test
  fun testImportLibraryDependency() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>somedep</groupId>
                        <artifactId>somedep</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    maven.assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportLibraryDependencyWithPropertyPlaceholder() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <somedep.version>4.0</somedep.version>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>somedep</groupId>
                        <artifactId>somedep</artifactId>
                        <version>${'$'}{somedep.version}</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    maven.assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportLibraryDependencyWithRecursicePropertyPlaceholder() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <somedep.version>${'$'}{another.property}</somedep.version>
                        <another.property>4.0</another.property>
                    </properties>
                    <dependencies>
                      <dependency>
                        <groupId>somedep</groupId>
                        <artifactId>somedep</artifactId>
                        <version>${'$'}{somedep.version}</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    maven.assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }


  @Test
  fun testImportLibraryDependencyWithPlaceholderInParent() = runBlocking {
    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        <dependencies>
            <dependency>
                <groupId>somedep</groupId>
                <artifactId>somedep</artifactId>
                <version>${'$'}{somedep.version}</version>
            </dependency>
        </dependencies>
        """)

    maven.createProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <somedep.version>4.0</somedep.version>
                    </properties>
                    <modules>
                        <module>m1</module>
                    </modules>
""")
    maven.importProjectStaticSync()

    maven.assertModules("project", "m1")
    maven.assertModuleLibDep("m1", "Maven: somedep:somedep:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    maven.assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportProjectWithTargetVersion() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.target>14</maven.compiler.target>
                        <maven.compiler.source>14</maven.compiler.source>
                    </properties>
                    """.trimIndent())


    readAction {
      val module = maven.getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfig() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                          <configuration>
                            <source>14</source>
                            <target>14</target>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      val module = maven.getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigSetByProperties() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <java.version>14</java.version>
                    </properties>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                          <configuration>
                            <source>${'$'}{java.version}</source>
                            <target>${'$'}{java.version}</target>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      val module = maven.getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigWithoutGroupId() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                          <configuration>
                            <source>14</source>
                            <target>14</target>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      val module = maven.getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigOfParent() = runBlocking {

    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                    </modules>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                          <configuration>
                            <source>14</source>
                            <target>14</target>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())




    readAction {
      val module = maven.getModule("m1")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigOfParentSetByProperties() = runBlocking {

    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <java.version>14</java.version>
                    </properties>
                    <modules>
                        <module>m1</module>
                    </modules>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                          <configuration>
                            <source>${'$'}{java.version}</source>
                            <target>${'$'}{java.version}</target>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())




    readAction {
      val module = maven.getModule("m1")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithTargetVersionOfParent() = runBlocking {

    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <maven.compiler.target>14</maven.compiler.target>
                        <maven.compiler.source>14</maven.compiler.source>
                    </properties>
                    <modules>
                        <module>m1</module>
                    </modules>
                    
                    """.trimIndent())




    readAction {
      val module = maven.getModule("m1")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }


  @Test
  fun testImportProjectWithKotlinConfig() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <kotlin.version>1.9.21</kotlin.version>
                    </properties>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.jetbrains.kotlin</groupId>
                          <artifactId>kotlin-maven-plugin</artifactId>
                          <version>${'$'}{kotlin.version}</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      maven.assertSources("project", "src/main/java", "src/main/kotlin")
    }
  }

  @Test
  fun testImportProjectWithBuildHelperPlugin() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
        
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>3.2.0</version>
                          <executions>
                            <execution>
                              <id>add-source-exec</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                    <source>src/main/anothersrc/</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-test-source-exec</id>
                              <phase>generate-test-sources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                    <source>src/main/sometestdir/</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())


    readAction {
      maven.assertSources("project", "src/main/java", "src/main/anothersrc")
      maven.assertTestSources("project", "src/test/java", "src/main/sometestdir")
    }
  }

  @Test
  fun testImportProjectWithKotlinConfigInParent() = runBlocking {

    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                    </modules>
                    <properties>
                        <kotlin.version>1.9.21</kotlin.version>
                    </properties>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.jetbrains.kotlin</groupId>
                          <artifactId>kotlin-maven-plugin</artifactId>
                          <version>${'$'}{kotlin.version}</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())




    readAction {
      maven.assertSources("m1", "src/main/java", "src/main/kotlin")
    }
  }

  @Test
  fun testImportSourceDirectory() = runBlocking {

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>src/main/somedir</sourceDirectory>
                    </build>
                    """.trimIndent())




    readAction {
      maven.assertSources("project", "src/main/somedir")
    }
  }

  @Test
  fun testImportSourceDirectoryWithBasedirProp() = runBlocking {

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${'$'}{basedir}/src/main/somedir</sourceDirectory>
                    </build>
                    """.trimIndent())




    readAction {
      maven.assertSources("project", "src/main/somedir")
    }
  }

  @Test
  fun testImportSourceDirectoryWithDefinedProp() = runBlocking {

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <our.src.dir>src/main/somedir</our.src.dir>
                    </properties>

                    <build>
                      <sourceDirectory>${'$'}{our.src.dir}</sourceDirectory>
                    </build>
                    """.trimIndent())


    readAction {
      maven.assertSources("project", "src/main/somedir")
    }
  }


  @Test
  fun testImportSourceDirectoryWithUndefinedPropShouldNotToAddRootPathAsASource() = runBlocking {

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>

                    <build>
                      <sourceDirectory>${'$'}{some.unknown.property}/some/path</sourceDirectory>
                    </build>
                    """.trimIndent())


    readAction {
      maven.assertSources("project")
    }
  }

  @Test
  fun testImportSourceDirectoryWithSystemVariable() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>

                    <build>
                      <sourceDirectory>${'$'}{user.home}/some/path</sourceDirectory>
                    </build>
                    """.trimIndent())

    readAction {
      val expectedValue = "${System.getProperty("user.home")}/some/path"
      maven.assertContentRootSources("project", expectedValue, "")
    }
  }

  @Test
  fun `test cyclic dependency`() = runBlocking {
    maven.importProjectStaticSync("""
                <groupId>group</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
                <packaging>pom</packaging>
                <parent>
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                </parent>
                """.trimIndent())

    val projects = maven.projectsManager.projects.map { it.mavenId.displayString }
    UsefulTestCase.assertSameElements(projects, "group:parent:1")
  }

  @Test
  fun `test import project if module path set to file`(): Unit = runBlocking {
    maven.createPomFile(maven.createProjectSubDir("m1"), "dev-pom.xml", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1-dev</artifactId>
        """)

    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1/dev-pom.xml</module>
                    </modules>
                    """)


    maven.assertModules("project", "m1-dev")

  }


  @Test
  fun `test import project if module in the same dir but different_pom_name and importing dev_pom as project`(): Unit = runBlocking {
    maven.createPomFile(maven.projectRoot, "pom.xml", """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        """)

    maven.projectPom = maven.createPomFile(maven.projectRoot, "dev_pom.xml", """
      <groupId>test</groupId>
      <artifactId>project-dev</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>pom.xml</module>
      </modules>
""")

    maven.importProjectStaticSync(maven.projectPom)


    maven.assertModules("project-dev", "project")

  }

  @Test
  fun `test import cyclic dependencies in modules`() = runBlocking {
    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        <dependencies>
          <dependency>
              <groupId>test</groupId>
              <artifactId>m2</artifactId>
              <version>1</version>
          </dependency>
        </dependencies>
        """)

    maven.createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        <dependencies>
          <dependency>
              <groupId>test</groupId>
              <artifactId>m1</artifactId>
              <version>1</version>
          </dependency>
        </dependencies>
        """)

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                        <module>m2</module>
                    </modules>
                    """)

    maven.assertModules("project", "m1", "m2")
    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleModuleDeps("m2", "m1")

  }

  @Test
  fun testImportTestScopeDependency() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>somedep</groupId>
                        <artifactId>somedep</artifactId>
                        <version>4.0</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")

    maven.assertModuleLibDepScope("project", "Maven: somedep:somedep:4.0", DependencyScope.TEST)

    maven.assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportLibrariesDeclaredInParent() = runBlocking {
    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                    </modules>
                    <dependencies> 
                    <dependency>
                        <groupId>somedep</groupId>
                        <artifactId>somedep</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project", "m1")

    maven.assertModuleLibDep("m1", "Maven: somedep:somedep:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
  }

  @Test
  fun testReimportProjectsIfModulesDeclaredInDefaultProfile() = runBlocking {
    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)
    maven.createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        """)
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                        <module>m2</module>
                    </modules>
                    """.trimIndent())
    maven.assertModules("project", "m1", "m2")

    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                    </modules>
                    <profiles>
                      <profile>
                        <id>myprofile</id>
                        <activation>
                          <property>
                            <name>!someVarName</name>
                          </property>
                        </activation>
                        <modules>
                          <module>m2</module>
                        </modules>
                      </profile>
                  </profiles>
                    """.trimIndent())
    maven.assertModules("project", "m1", "m2")
  }

  @Test
  fun testImportProjectWithEmptyModulesShouldFinish() = runBlocking {
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <modules>
                        <module></module>
                    </modules>
                    """.trimIndent())
    maven.assertModules("project")
  }

  @Test
  fun testImportProjectWithModules() = runBlocking {
    maven.createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        """)
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <modules>
                        <module>m2</module>
                    </modules>
                    """.trimIndent())
    maven.assertModules("project", "m2")
  }

  @Test
  fun testImportProjectWithSubprojects() = runBlocking {
    maven.assumeModel_4_1_0("for 4.1.0")
    maven.createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        """)
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <subprojects>
                        <subproject>m2</subproject>
                    </subprojects>
                    """.trimIndent())
    maven.assertModules("project", "m2")
  }

  @Test
  fun testImportProjectScanSubdirectories() = runBlocking {
    maven.assumeModel_4_1_0("for 4.1.0")
    maven.createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        """)
    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    """.trimIndent())
    maven.assertModules("project", "m1", "m2")
  }

  @Test
  fun testImportProjectDoesNotScanSubdirectoriesIfModel400() = runBlocking {
    maven.assumeModel_4_0_0("for 4.0.0")
    maven.createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        """)
    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    """.trimIndent())
    maven.assertModules("project")
  }

  @Test
  fun testImportProjectDoesNotScanSubdirectoriesIfEmptyTag() = runBlocking {
    maven.assumeModel_4_1_0("for 4.1.0")
    maven.createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        """)
    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <subprojects/>
                    """.trimIndent())
    maven.assertModules("project")
  }

  @Test
  fun testImportProjectWithCyclicAggregatorsModulesShouldFinish() = runBlocking {
    maven.createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        <modules>
            <module>../m2</module>
        </modules>            
        """)
    maven.createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        <modules>
            <module>../m1</module>
        </modules>
        """)
    maven.importProjectStaticSync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                        <module>m2</module>
                    </modules>
                    """.trimIndent())
    maven.assertModules("project", "m1", "m2")
  }

}