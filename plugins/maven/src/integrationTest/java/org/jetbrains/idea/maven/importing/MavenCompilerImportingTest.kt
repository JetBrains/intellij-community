// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.idea.TestFor
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.assumeOnLocalEnvironmentOnly
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.defaultLanguageLevel
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.mavenVersionIsOrMoreThan
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.JavaRelease
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenCompilerImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private lateinit var ideCompilerConfiguration: CompilerConfigurationImpl

  private lateinit var javacCompiler: BackendCompiler
  private lateinit var eclipseCompiler: BackendCompiler

  private val eclipsePom = """
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.6.0</version>
                            <configuration>
                                <compilerId>eclipse</compilerId>
                            </configuration>
                            <dependencies>
                                <dependency>
                                    <groupId>org.codehaus.plexus</groupId>
                                    <artifactId>plexus-compiler-eclipse</artifactId>
                                    <version>2.8.1</version>
                                </dependency>
                            </dependencies>
                        </plugin>
                    </plugins>
                </build>
  """

  private val javacPom = """
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     """


  @BeforeEach
  fun setUp() {
    ideCompilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl
    javacCompiler = ideCompilerConfiguration.defaultCompiler
    eclipseCompiler = ideCompilerConfiguration.registeredJavaCompilers.find { it is EclipseCompiler } as EclipseCompiler
    AcceptedLanguageLevelsSettings.allowLevel(
      maven.testRootDisposable,
      LanguageLevel.entries[LanguageLevel.HIGHEST.ordinal + 1]
    )
  }

  @Test
  fun testLanguageLevel() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <source>1.4</source>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelFromDefaultCompileExecutionConfiguration() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <executions>
              <execution>
                <id>default-compile</id>
                <configuration>
                  <source>1.8</source>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(LanguageLevel.JDK_1_8, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule("project")))
  }

  @Test
  fun testLanguageLevel6() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <source>1.6</source>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(LanguageLevel.JDK_1_6, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelX() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <source>99</source>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(maven.defaultLanguageLevel, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelWhenCompilerPluginIsNotSpecified() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(maven.defaultLanguageLevel, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelWhenConfigurationIsNotSpecified() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(maven.defaultLanguageLevel, getLanguageLevelForModule())
  }


  @Test
  fun testLanguageLevelFromPluginManagementSection() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <pluginManagement>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <configuration>
                <source>1.4</source>
              </configuration>
            </plugin>
          </plugins>
        </pluginManagement>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelFromParentPluginManagementSection() = runBlocking {
    maven.createModulePom("parent", """
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <build>
        <pluginManagement>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <configuration>
                <source>1.4</source>
              </configuration>
            </plugin>
          </plugins>
        </pluginManagement>
      </build>
    """.trimIndent())
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1</version>
        <relativePath>parent/pom.xml</relativePath>
      </parent>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForModule())
  }

  @Test
  fun testOverridingLanguageLevelFromPluginManagementSection() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <pluginManagement>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <configuration>
                <source>1.4</source>
              </configuration>
            </plugin>
          </plugins>
        </pluginManagement>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <source>1.3</source>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(LanguageLevel.JDK_1_3, getLanguageLevelForModule())
  }

  @Test
  fun testPreviewLanguageLevelProperty() = runBlocking {
    val highest = JavaRelease.getHighest()
    val highestPreview = highest.getPreviewLevel()
    val feature = highest.toJavaVersion().feature
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.enablePreview>true</maven.compiler.enablePreview>
      </properties>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.10.1</version>
            <configuration>
                <release>${feature}</release>
                <forceJavacCompilerUse>true</forceJavacCompilerUse>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(highestPreview, getLanguageLevelForModule())
  }

  @Test
  fun testPreviewLanguageLevelParameter() = runBlocking {
    doTestPreviewConfigurationParameter("<enablePreview>true</enablePreview>")
  }

  @Test
  fun testPreviewLanguageLevelOneLine() = runBlocking {
    doTestPreviewConfigurationParameter("<compilerArgs>--enable-preview</compilerArgs>")
  }

  @Test
  fun testPreviewLanguageLevelArg() = runBlocking {
    doTestPreviewConfigurationParameter("<compilerArgs><arg>--enable-preview</arg></compilerArgs>")
  }

  @Test
  fun testPreviewLanguageLevelCompilerArg() = runBlocking {
    doTestPreviewConfigurationParameter("<compilerArgs><compilerArg>--enable-preview</compilerArg></compilerArgs>")
  }

  private suspend fun doTestPreviewConfigurationParameter(configurationParameter: String?) {
    val highest = JavaRelease.getHighest()
    val highestPreview = highest.getPreviewLevel()

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.10.1</version>
            <configuration>
                <release>${highest.feature()}</release>
                ${configurationParameter ?: ""}
                <forceJavacCompilerUse>true</forceJavacCompilerUse>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(highestPreview, getLanguageLevelForModule())
  }

  @Test
  fun testInheritingLanguageLevelFromPluginManagementSection() = runBlocking {
    maven.importProjectAsync(("<groupId>test</groupId>" +
                        "<artifactId>project</artifactId>" +
                        "<version>1</version>" +
                        "<build>" +
                        "  <pluginManagement>" +
                        "    <plugins>" +
                        "      <plugin>" +
                        "        <groupId>org.apache.maven.plugins</groupId>" +
                        "        <artifactId>maven-compiler-plugin</artifactId>" +
                        "        <configuration>" +
                        "          <source>1.4</source>" +
                        "        </configuration>" +
                        "      </plugin>" +
                        "    </plugins>" +
                        "  </pluginManagement>" +
                        "  <plugins>" +
                        "    <plugin>" +
                        "      <groupId>org.apache.maven.plugins</groupId>" +
                        "      <artifactId>maven-compiler-plugin</artifactId>" +
                        "      <configuration>" +
                        "          <target>1.5</target>" +
                        "      </configuration>" +
                        "    </plugin>" +
                        "  </plugins>" +
                        "</build>"))
    maven.assertModules("project")
    assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForModule())
  }

  private fun getLanguageLevelForModule(): LanguageLevel? {
    return LanguageLevelUtil.getCustomLanguageLevel(maven.getModule("project"))
  }

  @Test
  fun testSettingTargetLevel() = runBlocking {
    JavacConfiguration.getOptions(maven.project,
                                  JavacConfiguration::class.java).ADDITIONAL_OPTIONS_STRING = "-Xmm500m -Xms128m -target 1.5"
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
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
    assertEquals("-Xmm500m -Xms128m", JavacConfiguration.getOptions(maven.project,
                                                                    JavacConfiguration::class.java).ADDITIONAL_OPTIONS_STRING.trim { it <= ' ' })
    assertEquals("1.3", ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule("project")))
  }

  @Test
  fun testSettingTargetLevelFromDefaultCompileExecutionConfiguration() = runBlocking {

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <executions>
              <execution>
                <id>default-compile</id>
                <configuration>
                  <target>1.9</target>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.assertModules("project")
    assertEquals(LanguageLevel.JDK_1_9, LanguageLevel.parse(
      ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule("project"))))
  }

  @Test
  fun testSettingTargetLevelFromParent() = runBlocking {
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <packaging>pom</packaging>
      <version>1</version>
      <modules>
        <module>m1</module>
        <module>m2</module>
      </modules>
      <properties>
        <maven.compiler.target>1.3</maven.compiler.target>
      </properties>
    """.trimIndent())
    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
    """.trimIndent())
    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <build>
        <plugins>
          <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <target>1.5</target>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.importProjectAsync()
    assertEquals("1.3", ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule("project")))
    assertEquals("1.3", ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m1"))))
    assertEquals("1.5", ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m2"))))
  }

  @Test
  fun testOverrideLanguageLevelFromParentPom() = runBlocking {
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <packaging>pom</packaging>
      <version>1</version>
      <modules>
        <module>m1</module>
      </modules>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.6.0</version>
            <configuration>
              <source>7</source>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent()
    )
    maven.createModulePom("m1", """
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <release>11</release>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.importProjectAsync()
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule(maven.mn("project", "m1"))))
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m1"))))
  }

  @Test
  fun testReleaseHasPriorityInParentPom() = runBlocking {
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <packaging>pom</packaging>
      <version>1</version>
      <modules>
        <module>m1</module>
      </modules>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.6.0</version>
            <configuration>
              <release>9</release>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent()
    )
    maven.createModulePom("m1", """
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <source>11</source>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_9, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule(maven.mn("project", "m1"))))
    assertEquals(LanguageLevel.JDK_1_9.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m1"))))
  }

  @Test
  fun testReleasePropertyNotSupport() = runBlocking {

    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <packaging>pom</packaging>
      <version>1</version>
      <modules>
        <module>m1</module>
      </modules>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <release>9</release>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent()
    )
    maven.createModulePom("m1", """
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <source>11</source>
              <target>11</target>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.importProjectAsync()
    val expectedlevel = if (maven.mavenVersionIsOrMoreThan("3.9.0")) LanguageLevel.JDK_1_9 else LanguageLevel.JDK_11;
    assertEquals(expectedlevel, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule(maven.mn("project", "m1"))))
    assertEquals(expectedlevel.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule(maven.mn("project", "m1"))))
  }

  @Test
  fun testCompilerPluginExecutionBlockProperty() = runBlocking {
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <profiles>
        <profile>
          <id>target-jdk8</id>
          <activation><jdk>[1.8,)</jdk></activation>
          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <executions>
                  <execution>
                    <id>compile-jdk8</id>
                    <goals>
                      <goal>compile</goal>
                    </goals>
                    <configuration>
                      <source>1.8</source>
                      <target>1.8</target>
                    </configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>
        </profile>
        <profile>
          <id>target-jdk11</id>
          <activation><jdk>[11,)</jdk></activation>
          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <executions>
                  <execution>
                    <id>compile-jdk11</id>
                    <goals>
                      <goal>compile</goal>
                    </goals>
                    <configuration>
                      <source>11</source>
                      <target>11</target>
                    </configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>
        </profile>
      </profiles>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version>
          </plugin>
        </plugins>
      </build>
    """.trimIndent()
    )
    maven.importProjectAsync()
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule("project")))
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule("project")))
  }

  @Test
  fun testShouldResolveJavac() = runBlocking {

    maven.createProjectPom(javacPom)
    maven.importProjectAsync()

    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)

  }

  @Test
  fun testShouldResolveEclipseCompilerOnAutoDetect() = runBlocking {
    MavenProjectsManager.getInstance(maven.project).importingSettings.isAutoDetectCompiler = true

    maven.createProjectPom(eclipsePom)
    maven.importProjectAsync()

    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }


  @Test
  fun testShouldResolveEclipseAndSwitchToJavacCompiler() = runBlocking {
    MavenProjectsManager.getInstance(maven.project).importingSettings.isAutoDetectCompiler = true

    maven.createProjectPom(eclipsePom)
    maven.importProjectAsync()
    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    maven.updateProjectPom(javacPom)
    maven.importProjectAsync()

    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
  }


  @Test
  fun testShouldResolveEclipseAndStayOnEclipseCompiler() = runBlocking {
    MavenProjectsManager.getInstance(maven.project).importingSettings.isAutoDetectCompiler = true

    maven.createProjectPom(eclipsePom)
    maven.importProjectAsync()
    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    maven.createProjectPom(eclipsePom)
    maven.importProjectAsync()

    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldSwitchToEclipseAfterJavac() = runBlocking {
    MavenProjectsManager.getInstance(maven.project).importingSettings.isAutoDetectCompiler = true

    maven.createProjectPom(javacPom)
    maven.importProjectAsync()
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)

    maven.updateProjectPom(eclipsePom)
    maven.importProjectAsync()

    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldNoSwitchToJavacIfFlagDisabled() = runBlocking {
    MavenProjectsManager.getInstance(maven.project).importingSettings.isAutoDetectCompiler = true

    maven.createProjectPom(eclipsePom)
    maven.importProjectAsync()
    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    MavenProjectsManager.getInstance(maven.project).importingSettings.isAutoDetectCompiler = false

    maven.createProjectPom(javacPom)
    maven.importProjectAsync()

    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldNotSwitchToJavacCompilerIfAutoDetectDisabled() = runBlocking {
    MavenProjectsManager.getInstance(maven.project).importingSettings.isAutoDetectCompiler = true

    maven.createProjectPom(eclipsePom)
    maven.importProjectAsync()
    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    maven.createProjectPom(javacPom)
    MavenProjectsManager.getInstance(maven.project).importingSettings.isAutoDetectCompiler = false
    maven.importProjectAsync()

    assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testCompilerPluginLanguageLevel() = runBlocking {
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.6.0</version>
            <configuration>
              <release>7</release>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    maven.importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_7, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule("project")))
    assertEquals(LanguageLevel.JDK_1_7,
                 LanguageLevel.parse(ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule("project"))))
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArguments() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <compilerArguments>
                <Averbose>true</Averbose>
                <parameters></parameters>
                <bootclasspath>rt.jar_path_here</bootclasspath>
              </compilerArguments>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")),
                                    "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here")
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParameters() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <parameters>true</parameters>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")), "-parameters")
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersFalse() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <parameters>false</parameters>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    UsefulTestCase.assertEmpty(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")))
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyOverride() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.parameters>true</maven.compiler.parameters>
      </properties>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <parameters>false</parameters>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    UsefulTestCase.assertEmpty(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")))
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyOverride1() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.parameters>false</maven.compiler.parameters>
      </properties>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
              <parameters>true</parameters>
            </configuration>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")), "-parameters")
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersProperty() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.parameters>true</maven.compiler.parameters>
      </properties>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")), "-parameters")
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyFalse() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.parameters>false</maven.compiler.parameters>
      </properties>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
          </plugin>
        </plugins>
      </build>
    """.trimIndent())
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    UsefulTestCase.assertEmpty(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")))
  }

  @Test
  fun testImportDifferentCompilationPropertiesForMainAndTest() = runBlocking{
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>
        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.0</version>
                <executions>
                    <execution>
                        <id>default-compile</id>
                        <phase>none</phase>
                    </execution>
                    <execution>
                        <id>default-testCompile</id>
                        <phase>none</phase>
                    </execution>
                    <execution>
                        <id>default-source-compile</id>
                        <phase>compile</phase>
                        <configuration>
                            <compilerArgs>
                                <arg>-parameters</arg>
                            </compilerArgs>
                        </configuration>
                    </execution>
                    <execution>
                        <id>default-test-compile</id>
                        <phase>test-compile</phase>
                        <configuration>
                            <compilerArgs>
                                <arg>-verbose</arg>
                            </compilerArgs>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
""")

    maven.assertModules("project", "project.main", "project.test")
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project.main")), "-parameters")
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project.test")), "-verbose")
  }

  @Test
  fun testDifferentJavaLevelsInElementConfiguration() = runBlocking {
    maven.importProjectAsync("""
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
                    <source>11</source>
                    <target>11</target>
                    <testSource>1.8</testSource>
                    <testTarget>1.8</testTarget>
                </configuration>
            </plugin>
        </plugins>
      </build>
""")
    maven.assertModules("project", "project.main", "project.test")
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule("project.main")))
    assertEquals(LanguageLevel.JDK_1_8, LanguageLevelUtil.getCustomLanguageLevel(maven.getModule("project.test")))

  }

  @Test
  fun testCompilerPluginConfigurationUnresolvedCompilerArguments() = runBlocking {
    maven.importProjectAsync(("<groupId>test</groupId>" +
                        "<artifactId>project</artifactId>" +
                        "<version>1</version>" +
                        "<build>" +
                        "  <plugins>" +
                        "    <plugin>" +
                        "      <groupId>org.apache.maven.plugins</groupId>" +
                        "      <artifactId>maven-compiler-plugin</artifactId>" +
                        "      <configuration>" +
                        "        <compilerId>\${maven.compiler.compilerId}</compilerId>" +
                        "        <compilerArgument>\${unresolvedArgument}</compilerArgument>" +
                        "        <compilerArguments>" +
                        "          <d>path/with/braces_\${</d>" +
                        "          <anotherStrangeArg>\${_\${foo}</anotherStrangeArg>" +
                        "        </compilerArguments>" +
                        "        <compilerArgs>" +
                        "          <arg>\${anotherUnresolvedArgument}</arg>" +
                        "          <arg>-myArg</arg>" +
                        "        </compilerArgs>" +
                        "      </configuration>" +
                        "    </plugin>" +
                        "  </plugins>" +
                        "</build>"))
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")),
                                    "-myArg", "-d", "path/with/braces_\${")
  }


  @Test
  fun testCompilerArgumentsShouldBeSetForMainAndTest() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>  
        <plugins>
          <plugin>   
             <groupId>org.apache.maven.plugins</groupId>  
             <artifactId>maven-compiler-plugin</artifactId>      
             <configuration>        
               <compilerArguments>          
                 <Averbose>true</Averbose>          
                 <parameters></parameters>          
                 <bootclasspath>rt.jar_path_here</bootclasspath>        
               </compilerArguments>   
               <testCompilerArguments>
                  <parameters></parameters>
               </testCompilerArguments>
             </configuration>    
          </plugin>
        </plugins>
      </build>""".trimIndent())

    maven.assertModules("project", "project.main", "project.test")

    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project.main")),
                                    "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here")
    assertUnorderedElementsAreEqual(
      ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project.test")),
      "-parameters",
    )
  }

  @Test
  fun testCompilerArgumentsShouldBeTakeFromMainIfTestIsEmpty() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>  
        <plugins>
          <plugin>   
             <groupId>org.apache.maven.plugins</groupId>  
             <artifactId>maven-compiler-plugin</artifactId>      
             <configuration>
               <source>11</source>
               <target>11</target>
               <testSource>1.8</testSource>
               <testTarget>1.8</testTarget>
               <compilerArguments>          
                 <Averbose>true</Averbose>          
                 <parameters></parameters>          
                 <bootclasspath>rt.jar_path_here</bootclasspath>        
               </compilerArguments>   
             </configuration>    
          </plugin>
        </plugins>
      </build>""".trimIndent())

    maven.assertModules("project", "project.main", "project.test")

    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project.main")),
                                    "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here")

    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project.test")),
                                    "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here")

  }

  @Test
  fun testCompilerArgumentsShouldBeSetForMainAndAdditionalSources() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createProjectSubDir("src/main/java")
    maven.createProjectSubDir("src/main/java17")
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>  
        <plugins>
          <plugin>   
             <groupId>org.apache.maven.plugins</groupId>  
             <artifactId>maven-compiler-plugin</artifactId>      
             <executions>
              <execution>
                        <id>default-compile</id>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <release>11</release>
                        </configuration>
                    </execution>

                    <execution>
                        <id>java17-compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <release>17</release>
                            <compileSourceRoots>
                                <compileSourceRoot>${"$"}{project.basedir}/src/main/java17</compileSourceRoot>
                            </compileSourceRoots>
                            <multiReleaseOutput>true</multiReleaseOutput>
                            <compilerArgs>
                                <arg>--blablabla</arg>
                            </compilerArgs>
                        </configuration>
                    </execution>
             </executions>

          </plugin>
        </plugins>
      </build>""".trimIndent())

    maven.assertModules("project", "project.main", "project.test", "project.java17-compile")
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project.main")))
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project.java17-compile")),
                                    "--blablabla")
  }

  @Test
  @TestFor(issues = ["IDEA-371005"])
  fun repetitiveCompilerArguments() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>  
        <plugins>
          <plugin> 
             <groupId>org.apache.maven.plugins</groupId>
             <artifactId>maven-compiler-plugin</artifactId>
             <version>3.12.1</version>
             <configuration>
               <compilerArgs>
                   <arg>--add-exports</arg>
                   <arg>java.base/sun.reflect.annotation=ALL-UNNAMED</arg>
                   <arg>--add-exports</arg>
                   <arg>java.base/sun.nio.ch=ALL-UNNAMED</arg>
               </compilerArgs>
             </configuration>
          </plugin>
        </plugins>
      </build> """.trimIndent())
    maven.assertModules("project")
    assertOrderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")),
                                  "--add-exports", "java.base/sun.reflect.annotation=ALL-UNNAMED", "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED")
  }

  @Test
  @TestFor(issues = ["IDEA-371747"])
  fun testCompilerArgumentsWithWeirdNames() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>  
        <plugins>
          <plugin> 
             <groupId>org.apache.maven.plugins</groupId>
             <artifactId>maven-compiler-plugin</artifactId>
             <version>3.12.1</version>
             <configuration>
                <release>17</release>
                <encoding>UTF-8</encoding>
                <source>17</source>
                <compilerArgs>
                    <myWeirdName>-blablabla</myWeirdName>
                    <anotherWeirdname>-qwerty</anotherWeirdname>
                </compilerArgs>
             </configuration>
          </plugin>
        </plugins>
      </build> """.trimIndent())

    maven.assertModules("project")
    assertOrderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(maven.getModule("project")),
                                  "-blablabla", "-qwerty")

  }

  @Test
  @TestFor(issues = ["IDEA-374666"])
  fun testAvoidUnnecessaryModuleSplitting() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>  
        <plugins>
          <plugin> 
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.12.1</version>
            <executions>
              <execution>
                <id>default-testCompile</id>
                <configuration>
                  <generatedSourcesDirectory>./java-test</generatedSourcesDirectory>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build> """.trimIndent())

    maven.assertModules("project")
  }

  @Test
  fun testLanguageLevelMavenSourcesTag() = runBlocking {
    maven.assumeMaven4()
    maven.assumeModel_4_1_0("test requires model 4.1.0")
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>  
        <sources>
          <source>
            <directory>src</directory>
            <targetVersion>17</targetVersion>
          </source>
        </sources>
      </build>""")
    maven.assertModules("project")
    assertEquals(LanguageLevel.parse("17"), getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelSplittedForMavenSourcesTag() = runBlocking {
    maven.assumeMaven4()
    maven.assumeModel_4_1_0("test requires model 4.1.0")
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <build>  
        <sources>
          <source>
            <directory>test</directory>
            <targetVersion>17</targetVersion>
            <scope>test</scope>
          </source>
          <source>
            <directory>src</directory>
            <targetVersion>11</targetVersion>
          </source>
        </sources>
      </build>""")
    maven.assertModules("project", "project.main", "project.test")
    maven.assertModules("project", "project.main", "project.test")
    assertEquals(LanguageLevel.JDK_11, LanguageLevel.parse(
      ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule("project.main"))))
    assertEquals(LanguageLevel.JDK_17, LanguageLevel.parse(
      ideCompilerConfiguration.getBytecodeTargetLevel(maven.getModule("project.test"))))
  }

}
