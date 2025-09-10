// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenStaticSyncTest : AbstractMavenStaticSyncTest() {


  @Test
  fun testImportLibraryDependency() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportLibraryDependencyWithPropertyPlaceholder() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportLibraryDependencyWithRecursicePropertyPlaceholder() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }


  @Test
  fun testImportLibraryDependencyWithPlaceholderInParent() = runBlocking {
    createModulePom("m1", """
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

    createProjectPom("""
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
    importProjectAsync()

    assertModules("project", "m1")
    assertModuleLibDep("m1", "Maven: somedep:somedep:4.0",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportProjectWithTargetVersion() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.target>14</maven.compiler.target>
                        <maven.compiler.source>14</maven.compiler.source>
                    </properties>
                    """.trimIndent())


    readAction {
      val module = getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfig() = runBlocking {
    importProjectAsync("""
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
      val module = getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigSetByProperties() = runBlocking {
    importProjectAsync("""
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
      val module = getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigWithoutGroupId() = runBlocking {
    importProjectAsync("""
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
      val module = getModule("project")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigOfParent() = runBlocking {

    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
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
      val module = getModule("m1")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithCompilerConfigOfParentSetByProperties() = runBlocking {

    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
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
      val module = getModule("m1")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }

  @Test
  fun testImportProjectWithTargetVersionOfParent() = runBlocking {

    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
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
      val module = getModule("m1")
      assertEquals(LanguageLevel.JDK_14, LanguageLevelUtil.getEffectiveLanguageLevel(module))
    }
  }


  @Test
  fun testImportProjectWithKotlinConfig() = runBlocking {
    importProjectAsync("""
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
      assertSources("project", "src/main/java", "src/main/kotlin")
    }
  }

  @Test
  fun testImportProjectWithBuildHelperPlugin() = runBlocking {
    importProjectAsync("""
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
      assertSources("project", "src/main/java", "src/main/anothersrc")
      assertTestSources("project", "src/test/java", "src/main/sometestdir")
    }
  }

  @Test
  fun testImportProjectWithKotlinConfigInParent() = runBlocking {

    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
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
      assertSources("m1", "src/main/java", "src/main/kotlin")
    }
  }

  @Test
  fun testImportSourceDirectory() = runBlocking {

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>src/main/somedir</sourceDirectory>
                    </build>
                    """.trimIndent())




    readAction {
      assertSources("project", "src/main/somedir")
    }
  }

  @Test
  fun testImportSourceDirectoryWithBasedirProp() = runBlocking {

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${'$'}{basedir}/src/main/somedir</sourceDirectory>
                    </build>
                    """.trimIndent())




    readAction {
      assertSources("project", "src/main/somedir")
    }
  }

  @Test
  fun testImportSourceDirectoryWithDefinedProp() = runBlocking {

    importProjectAsync("""
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
      assertSources("project", "src/main/somedir")
    }
  }


  @Test
  fun testImportSourceDirectoryWithUndefinedPropShouldNotToAddRootPathAsASource() = runBlocking {

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>

                    <build>
                      <sourceDirectory>${'$'}{some.unknown.property}/some/path</sourceDirectory>
                    </build>
                    """.trimIndent())


    readAction {
      assertSources("project")
    }
  }

  @Test
  fun testImportSourceDirectoryWithSystemVariable() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>

                    <build>
                      <sourceDirectory>${'$'}{user.home}/some/path</sourceDirectory>
                    </build>
                    """.trimIndent())

    val mount = System.getenv("EEL_FIXTURE_MOUNT") ?: ""
    readAction {
      val expectedValue = "$mount${System.getProperty("user.home")}/some/path"
      assertContentRootSources("project", expectedValue, "")
    }
  }

  @Test
  fun `test cyclic dependency`() = runBlocking {
    importProjectAsync("""
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

    val projects = projectsManager.projects.map { it.mavenId.displayString }
    UsefulTestCase.assertSameElements(projects, "group:parent:1")
  }

  @Test
  fun `test import project if module path set to file`(): Unit = runBlocking {
    createPomFile(createProjectSubDir("m1"), "dev-pom.xml", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1-dev</artifactId>
        """)

    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1/dev-pom.xml</module>
                    </modules>
                    """)


    assertModules("project", "m1-dev")

  }


  @Test
  fun `test import project if module in the same dir but different_pom_name and importing dev_pom as project`(): Unit = runBlocking {
    createPomFile(projectRoot, "pom.xml", """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        """)

    projectPom = createPomFile(projectRoot, "dev_pom.xml", """
      <groupId>test</groupId>
      <artifactId>project-dev</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>pom.xml</module>
      </modules>
""")

    importProjectAsync(projectPom)


    assertModules("project-dev", "project")

  }

  @Test
  fun `test import cyclic dependencies in modules`() = runBlocking {
    createModulePom("m1", """
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

    createModulePom("m2", """
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

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                        <module>m2</module>
                    </modules>
                    """)

    assertModules("project", "m1", "m2")
    assertModuleModuleDeps("m1", "m2")
    assertModuleModuleDeps("m2", "m1")

  }

  @Test
  fun testImportTestScopeDependency() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDep("project", "Maven: somedep:somedep:4.0",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")

    assertModuleLibDepScope("project", "Maven: somedep:somedep:4.0", DependencyScope.TEST)

    assertProjectLibraryCoordinates("Maven: somedep:somedep:4.0", "somedep", "somedep", "4.0")
  }

  @Test
  fun testImportLibrariesDeclaredInParent() = runBlocking {
    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)

    importProjectAsync("""
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

    assertModules("project", "m1")

    assertModuleLibDep("m1", "Maven: somedep:somedep:4.0",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-sources.jar!/",
                       "jar://" + repositoryPathCanonical + "/somedep/somedep/4.0/somedep-4.0-javadoc.jar!/")
  }

  @Test
  fun testReimportProjectsIfModulesDeclaredInDefaultProfile() = runBlocking {
    createModulePom("m1", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m1</artifactId>
        """)
    createModulePom("m2", """
         <parent>
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
        </parent>
        <artifactId>m2</artifactId>
        """)
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                        <module>m2</module>
                    </modules>
                    """.trimIndent())
    assertModules("project", "m1", "m2")

    importProjectAsync("""
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
    assertModules("project", "m1", "m2")
  }

  @Test
  fun testImportProjectWithEmptyModulesShouldFinish() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <modules>
                        <module></module>
                    </modules>
                    """.trimIndent())
    assertModules("project")
  }

  @Test
  fun testImportProjectWithCyclicAggregatorsModulesShouldFinish() = runBlocking {
    createModulePom("m1", """
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
    createModulePom("m2", """
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
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>m1</module>
                        <module>m2</module>
                    </modules>
                    """.trimIndent())
    assertModules("project", "m1", "m2")
  }

}