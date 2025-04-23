// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.idea.TestFor
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test

class MavenCompilerImportingTest : MavenMultiVersionImportingTestCase() {
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


  override fun setUp() {
    super.setUp()
    ideCompilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    javacCompiler = ideCompilerConfiguration.defaultCompiler
    eclipseCompiler = ideCompilerConfiguration.registeredJavaCompilers.find { it is EclipseCompiler } as EclipseCompiler
    AcceptedLanguageLevelsSettings.allowLevel(testRootDisposable, LanguageLevel.values()[LanguageLevel.HIGHEST.ordinal + 1])
  }

  @Test
  fun testLanguageLevel() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "        <source>1.4</source>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelFromDefaultCompileExecutionConfiguration() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <executions>" +
                   "        <execution>" +
                   "          <id>default-compile</id>" +
                   "             <configuration>" +
                   "                <source>1.8</source>" +
                   "             </configuration>" +
                   "        </execution>" +
                   "      </executions>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project", "project.main", "project.test")
    TestCase.assertEquals(LanguageLevel.JDK_1_8, LanguageLevelUtil.getCustomLanguageLevel(getModule("project.main")))
    TestCase.assertEquals(LanguageLevel.JDK_1_8, LanguageLevelUtil.getCustomLanguageLevel(getModule("project.test")))
  }

  @Test
  fun testLanguageLevel6() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "        <source>1.6</source>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_6, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelX() = runBlocking {
    importProjectAsync("""
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
    assertModules("project")
    assertEquals(defaultLanguageLevel, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelWhenCompilerPluginIsNotSpecified() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>"))
    assertModules("project")
    assertEquals(defaultLanguageLevel, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelWhenConfigurationIsNotSpecified() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    assertEquals(defaultLanguageLevel, getLanguageLevelForModule())
  }


  @Test
  fun testLanguageLevelFromPluginManagementSection() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
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
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForModule())
  }

  @Test
  fun testLanguageLevelFromParentPluginManagementSection() = runBlocking {
    createModulePom("parent",
                    ("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
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
                     "</build>"))
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<parent>" +
                   "  <groupId>test</groupId>" +
                   "  <artifactId>parent</artifactId>" +
                   "  <version>1</version>" +
                   "  <relativePath>parent/pom.xml</relativePath>" +
                   "</parent>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForModule())
  }

  @Test
  fun testOverridingLanguageLevelFromPluginManagementSection() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
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
                   "        <source>1.3</source>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_3, getLanguageLevelForModule())
  }

  @Test
  fun testPreviewLanguageLevelProperty() = runBlocking {
    val feature = LanguageLevel.HIGHEST.toJavaVersion().feature
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<properties>" +
                   "  <maven.compiler.enablePreview>true</maven.compiler.enablePreview>" +
                   "</properties>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <version>3.10.1</version>" +
                   "      <configuration>" +
                   "          <release>" + feature + "</release>" +
                   "          <forceJavacCompilerUse>true</forceJavacCompilerUse>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.values().get(LanguageLevel.HIGHEST.ordinal + 1), getLanguageLevelForModule())
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
    val feature = LanguageLevel.HIGHEST.feature()
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <version>3.10.1</version>" +
                   "      <configuration>" +
                   "          <release>" + feature + "</release>" +
                   configurationParameter +
                   "          <forceJavacCompilerUse>true</forceJavacCompilerUse>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.values().get(LanguageLevel.HIGHEST.ordinal + 1), getLanguageLevelForModule())
  }

  @Test
  fun testInheritingLanguageLevelFromPluginManagementSection() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
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
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForModule())
  }

  private fun getLanguageLevelForModule(): LanguageLevel? {
    return LanguageLevelUtil.getCustomLanguageLevel(getModule("project"))
  }

  @Test
  fun testSettingTargetLevel() = runBlocking {
    JavacConfiguration.getOptions(project,
                                  JavacConfiguration::class.java).ADDITIONAL_OPTIONS_STRING = "-Xmm500m -Xms128m -target 1.5"
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "        <configuration>" +
                   "          <target>1.3</target>" +
                   "        </configuration>" +
                   "     </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    TestCase.assertEquals("-Xmm500m -Xms128m",
                          JavacConfiguration.getOptions(project,
                                                        JavacConfiguration::class.java).ADDITIONAL_OPTIONS_STRING.trim { it <= ' ' })
    TestCase.assertEquals("1.3", ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project")))
  }

  @Test
  fun testSettingTargetLevelFromDefaultCompileExecutionConfiguration() = runBlocking {

    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <executions>" +
                   "        <execution>" +
                   "          <id>default-compile</id>" +
                   "             <configuration>" +
                   "                <target>1.9</target>" +
                   "             </configuration>" +
                   "        </execution>" +
                   "      </executions>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project", "project.main", "project.test")
    TestCase.assertEquals(LanguageLevel.JDK_1_9, LanguageLevel.parse(
      ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project.main"))))
    TestCase.assertEquals(defaultLanguageLevel, LanguageLevel.parse(
      ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project.test"))))
  }

  @Test
  fun testSettingTargetLevelFromParent() = runBlocking {
    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<packaging>pom</packaging>" +
                      "<version>1</version>" +
                      "<modules>" +
                      "  <module>m1</module>" +
                      "  <module>m2</module>" +
                      "</modules>" +
                      "<properties>" +
                      "<maven.compiler.target>1.3</maven.compiler.target>" +
                      "</properties>"))
    createModulePom("m1", ("<groupId>test</groupId>" +
                           "<artifactId>m1</artifactId>" +
                           "<version>1</version>" +
                           "<parent>" +
                           "<groupId>test</groupId>" +
                           "<artifactId>project</artifactId>" +
                           "<version>1</version>" +
                           "</parent>"))
    createModulePom("m2", ("<groupId>test</groupId>" +
                           "<artifactId>m2</artifactId>" +
                           "<version>1</version>" +
                           "<parent>" +
                           "<groupId>test</groupId>" +
                           "<artifactId>project</artifactId>" +
                           "<version>1</version>" +
                           "</parent>" +
                           "<build>" +
                           "  <plugins>" +
                           "    <plugin>" +
                           "      <artifactId>maven-compiler-plugin</artifactId>" +
                           "        <configuration>" +
                           "          <target>1.5</target>" +
                           "        </configuration>" +
                           "     </plugin>" +
                           "  </plugins>" +
                           "</build>"))
    importProjectAsync()
    TestCase.assertEquals("1.3",
                          ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project")))
    TestCase.assertEquals("1.3",
                          ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
    TestCase.assertEquals("1.5",
                          ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m2"))))
  }

  @Test
  fun testOverrideLanguageLevelFromParentPom() = runBlocking {
    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<packaging>pom</packaging>" +
                      "<version>1</version>" +
                      "<modules>" +
                      "  <module>m1</module>" +
                      "</modules>" +
                      "<build>" +
                      "  <plugins>" +
                      "    <plugin>" +
                      "      <groupId>org.apache.maven.plugins</groupId>" +
                      "      <artifactId>maven-compiler-plugin</artifactId>" +
                      "      <version>3.6.0</version>" +
                      "      <configuration>" +
                      "       <source>7</source>" +
                      "      </configuration>" +
                      "    </plugin>" +
                      "  </plugins>" +
                      "</build>")
    )
    createModulePom("m1",
                    ("<artifactId>m1</artifactId>" +
                     "<version>1</version>" +
                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <release>11</release>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>"))
    importProjectAsync()
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
  }

  @Test
  fun testReleaseHasPriorityInParentPom() = runBlocking {
    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<packaging>pom</packaging>" +
                      "<version>1</version>" +
                      "<modules>" +
                      "  <module>m1</module>" +
                      "</modules>" +
                      "<build>" +
                      "  <plugins>" +
                      "    <plugin>" +
                      "      <groupId>org.apache.maven.plugins</groupId>" +
                      "      <artifactId>maven-compiler-plugin</artifactId>" +
                      "      <version>3.6.0</version>" +
                      "      <configuration>" +
                      "       <release>9</release>" +
                      "      </configuration>" +
                      "    </plugin>" +
                      "  </plugins>" +
                      "</build>")
    )
    createModulePom("m1",
                    ("<artifactId>m1</artifactId>" +
                     "<version>1</version>" +
                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <source>11</source>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>"))
    importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_9, LanguageLevelUtil.getCustomLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals(LanguageLevel.JDK_1_9.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
  }

  @Test
  fun testReleasePropertyNotSupport() = runBlocking {

    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<packaging>pom</packaging>" +
                      "<version>1</version>" +
                      "<modules>" +
                      "  <module>m1</module>" +
                      "</modules>" +
                      "<build>" +
                      "  <plugins>" +
                      "    <plugin>" +
                      "      <groupId>org.apache.maven.plugins</groupId>" +
                      "      <artifactId>maven-compiler-plugin</artifactId>" +
                      "      <configuration>" +
                      "       <release>9</release>" +
                      "      </configuration>" +
                      "    </plugin>" +
                      "  </plugins>" +
                      "</build>")
    )
    createModulePom("m1",
                    ("<artifactId>m1</artifactId>" +
                     "<version>1</version>" +
                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-compiler-plugin</artifactId>" +
                     "      <configuration>" +
                     "        <source>11</source>" +
                     "        <target>11</target>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>"))
    importProjectAsync()
    val expectedlevel = if (mavenVersionIsOrMoreThan("3.9.0")) LanguageLevel.JDK_1_9 else LanguageLevel.JDK_11;
    assertEquals(expectedlevel, LanguageLevelUtil.getCustomLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals(expectedlevel.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
  }

  @Test
  fun testCompilerPluginExecutionBlockProperty() = runBlocking {
    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<version>1</version>" +
                      "<profiles>" +
                      "  <profile>" +
                      "    <id>target-jdk8</id>" +
                      "    <activation><jdk>[1.8,)</jdk></activation>" +
                      "    <build>" +
                      "      <plugins>" +
                      "        <plugin>" +
                      "          <groupId>org.apache.maven.plugins</groupId>" +
                      "          <artifactId>maven-compiler-plugin</artifactId>" +
                      "          <executions>" +
                      "            <execution>" +
                      "              <id>compile-jdk8</id>" +
                      "              <goals>" +
                      "                <goal>compile</goal>" +
                      "              </goals>" +
                      "              <configuration>" +
                      "                <source>1.8</source>" +
                      "                <target>1.8</target>" +
                      "              </configuration>" +
                      "            </execution>" +
                      "          </executions>" +
                      "        </plugin>" +
                      "      </plugins>" +
                      "    </build>" +
                      "  </profile>" +
                      "  <profile>" +
                      "    <id>target-jdk11</id>" +
                      "    <activation><jdk>[11,)</jdk></activation>" +
                      "    <build>" +
                      "      <plugins>" +
                      "        <plugin>" +
                      "          <groupId>org.apache.maven.plugins</groupId>" +
                      "          <artifactId>maven-compiler-plugin</artifactId>" +
                      "          <executions>" +
                      "            <execution>" +
                      "              <id>compile-jdk11</id>" +
                      "              <goals>" +
                      "                <goal>compile</goal>" +
                      "              </goals>" +
                      "              <configuration>" +
                      "                <source>11</source>" +
                      "                <target>11</target>" +
                      "              </configuration>" +
                      "            </execution>" +
                      "          </executions>" +
                      "        </plugin>" +
                      "      </plugins>" +
                      "    </build>" +
                      "  </profile>" +
                      "</profiles>" +
                      "<build>" +
                      "  <plugins>" +
                      "    <plugin>" +
                      "      <groupId>org.apache.maven.plugins</groupId>" +
                      "      <artifactId>maven-compiler-plugin</artifactId>" +
                      "      <version>3.8.1</version>" +
                      "    </plugin>" +
                      "  </plugins>" +
                      "</build>")
    )
    importProjectAsync()
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule("project")))
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project")))
  }

  @Test
  fun testShouldResolveJavac() = runBlocking {

    createProjectPom(javacPom)
    importProjectAsync()

    TestCase.assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)

  }

  @Test
  fun testShouldResolveEclipseCompilerOnAutoDetect() = runBlocking {
    MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProjectAsync()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }


  @Test
  fun testShouldResolveEclipseAndSwitchToJavacCompiler() = runBlocking {
    MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProjectAsync()
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    updateProjectPom(javacPom)
    importProjectAsync()

    TestCase.assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
  }


  @Test
  fun testShouldResolveEclipseAndStayOnEclipseCompiler() = runBlocking {
    MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProjectAsync()
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    createProjectPom(eclipsePom)
    importProjectAsync()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldSwitchToEclipseAfterJavac() = runBlocking {
    MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler = true

    createProjectPom(javacPom)
    importProjectAsync()
    TestCase.assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)

    updateProjectPom(eclipsePom)
    importProjectAsync()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldNoSwitchToJavacIfFlagDisabled() = runBlocking {
    MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProjectAsync()
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler = false

    createProjectPom(javacPom)
    importProjectAsync()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldNotSwitchToJavacCompilerIfAutoDetectDisabled() = runBlocking {
    MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProjectAsync()
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    createProjectPom(javacPom)
    MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler = false
    importProjectAsync()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testCompilerPluginLanguageLevel() = runBlocking {
    createProjectPom(("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<version>1</version>" +
                      "<build>" +
                      "  <plugins>" +
                      "    <plugin>" +
                      "      <groupId>org.apache.maven.plugins</groupId>" +
                      "      <artifactId>maven-compiler-plugin</artifactId>" +
                      "      <version>3.6.0</version>" +
                      "      <configuration>" +
                      "        <release>7</release>" +
                      "      </configuration>" +
                      "    </plugin>" +
                      "  </plugins>" +
                      "</build>"))
    importProjectAsync()
    assertEquals(LanguageLevel.JDK_1_7, LanguageLevelUtil.getCustomLanguageLevel(getModule("project")))
    assertEquals(LanguageLevel.JDK_1_7,
                 LanguageLevel.parse(ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project"))))
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArguments() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "        <compilerArguments>" +
                   "          <Averbose>true</Averbose>" +
                   "          <parameters></parameters>" +
                   "          <bootclasspath>rt.jar_path_here</bootclasspath>" +
                   "        </compilerArguments>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project")),
                                    "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here")
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParameters() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "        <parameters>true</parameters>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project")), "-parameters")
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersFalse() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "        <parameters>false</parameters>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    UsefulTestCase.assertEmpty(ideCompilerConfiguration.getAdditionalOptions(getModule("project")))
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyOverride() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<properties>" +
                   "  <maven.compiler.parameters>true</maven.compiler.parameters>" +
                   "</properties>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "        <parameters>false</parameters>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    UsefulTestCase.assertEmpty(ideCompilerConfiguration.getAdditionalOptions(getModule("project")))
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyOverride1() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<properties>" +
                   "  <maven.compiler.parameters>false</maven.compiler.parameters>" +
                   "</properties>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "        <parameters>true</parameters>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project")), "-parameters")
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersProperty() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<properties>" +
                   "  <maven.compiler.parameters>true</maven.compiler.parameters>" +
                   "</properties>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project")), "-parameters")
  }

  @Test
  fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyFalse() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<properties>" +
                   "  <maven.compiler.parameters>false</maven.compiler.parameters>" +
                   "</properties>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    UsefulTestCase.assertEmpty(ideCompilerConfiguration.getAdditionalOptions(getModule("project")))
  }

  @Test
  fun testImportDifferentCompilationPropertiesForMainAndTest() = runBlocking{
    importProjectAsync("""
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

    assertModules("project", "project.main", "project.test")
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project.main")), "-parameters")
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project.test")), "-verbose")
  }

  @Test
  fun testDifferentJavaLevelsInElementConfiguration() = runBlocking {
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
                    <source>11</source>
                    <target>11</target>
                    <testSource>1.8</testSource>
                    <testTarget>1.8</testTarget>
                </configuration>
            </plugin>
        </plugins>
      </build>
""")
    assertModules("project", "project.main", "project.test")
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule("project.main")))
    assertEquals(LanguageLevel.JDK_1_8, LanguageLevelUtil.getCustomLanguageLevel(getModule("project.test")))

  }

  @Test
  fun testCompilerPluginConfigurationUnresolvedCompilerArguments() = runBlocking {
    importProjectAsync(("<groupId>test</groupId>" +
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
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project")),
                                    "-myArg", "-d", "path/with/braces_\${")
  }


  @Test
  fun testCompilerArgumentsShouldBeSetForMainAndTest() = runBlocking {
    importProjectAsync("""
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

    assertModules("project", "project.main", "project.test")

    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project.main")),
                                    "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here")
    assertUnorderedElementsAreEqual(
      ideCompilerConfiguration.getAdditionalOptions(getModule("project.test")),
      "-parameters",
    )
  }

  @Test
  fun testCompilerArgumentsShouldBeTakeFromMainIfTestIsEmpty() = runBlocking {
    importProjectAsync("""
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

    assertModules("project", "project.main", "project.test")

    assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project.main")),
                                    "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here")

    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project.test")),
                                    "-Averbose=true", "-parameters", "-bootclasspath", "rt.jar_path_here")

  }

  @Test
  fun testCompilerArgumentsShouldBeSetForMainAndAdditionalSources() = runBlocking {
    createProjectSubDir("src/main/java")
    createProjectSubDir("src/main/java17")
    importProjectAsync("""
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

    assertModules("project", "project.main", "project.test", "project.java17-compile")
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project.main")))
    assertUnorderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project.java17-compile")),
                                    "--blablabla")
  }

  @Test
  @TestFor(issues = ["IDEA-371005"])
  fun repetitiveCompilerArguments() = runBlocking {
    importProjectAsync("""
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
    assertModules("project")
    assertOrderedElementsAreEqual(ideCompilerConfiguration.getAdditionalOptions(getModule("project")),
                                  "--add-exports", "java.base/sun.reflect.annotation=ALL-UNNAMED", "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED")
  }

}
