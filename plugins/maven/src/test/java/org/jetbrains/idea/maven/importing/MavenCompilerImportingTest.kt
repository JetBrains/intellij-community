// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test

open class MavenCompilerImportingTest : MavenMultiVersionImportingTestCase() {
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
    ideCompilerConfiguration = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    javacCompiler = ideCompilerConfiguration.defaultCompiler
    eclipseCompiler = ideCompilerConfiguration.registeredJavaCompilers.find { it is EclipseCompiler } as EclipseCompiler
  }

  @Test
  open fun testLanguageLevel() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testLanguageLevelFromDefaultCompileExecutionConfiguration() {
    importProject(("<groupId>test</groupId>" +
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
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_8, getLanguageLevelForModule())
  }

  @Test
  open fun testLanguageLevel6() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testLanguageLevelX() {
    importProject(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "        <source>99</source>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.HIGHEST, getLanguageLevelForModule())
  }

  @Test
  open fun testLanguageLevelWhenCompilerPluginIsNotSpecified() {
    importProject(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_5, getLanguageLevelForModule())
  }

  @Test
  open fun testLanguageLevelWhenConfigurationIsNotSpecified() {
    importProject(("<groupId>test</groupId>" +
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
    TestCase.assertEquals(LanguageLevel.JDK_1_5, getLanguageLevelForModule())
  }

  @Test
  open fun testLanguageLevelWhenSourceLanguageLevelIsNotSpecified() {
    importProject(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>" +
                   "      <groupId>org.apache.maven.plugins</groupId>" +
                   "      <artifactId>maven-compiler-plugin</artifactId>" +
                   "      <configuration>" +
                   "      </configuration>" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_5, getLanguageLevelForModule())
  }

  @Test
  open fun testLanguageLevelFromPluginManagementSection() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testLanguageLevelFromParentPluginManagementSection() {
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
    importProject(("<groupId>test</groupId>" +
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
  open fun testOverridingLanguageLevelFromPluginManagementSection() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testPreviewLanguageLevelOneLine() {
    doTestPreview("<compilerArgs>--enable-preview</compilerArgs>\n")
  }

  @Test
  open fun testPreviewLanguageLevelArg() {
    doTestPreview("<compilerArgs><arg>--enable-preview</arg></compilerArgs>\n")
  }

  @Test
  open fun testPreviewLanguageLevelCompilerArg() {
    doTestPreview("<compilerArgs><compilerArg>--enable-preview</compilerArg></compilerArgs>\n")
  }

  private fun doTestPreview(compilerArgs: String?) {
    val feature = LanguageLevel.HIGHEST.toJavaVersion().feature
    importProject(("<groupId>test</groupId>" +
                   "<artifactId>project</artifactId>" +
                   "<version>1</version>" +
                   "<build>" +
                   "  <plugins>" +
                   "    <plugin>\n" +
                   "      <groupId>org.apache.maven.plugins</groupId>\n" +
                   "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                   "      <version>3.8.0</version>\n" +
                   "      <configuration>\n" +
                   "          <release>" + feature + "</release>\n" +
                   compilerArgs +
                   "          <forceJavacCompilerUse>true</forceJavacCompilerUse>\n" +
                   "      </configuration>\n" +
                   "    </plugin>" +
                   "  </plugins>" +
                   "</build>"))
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.values().get(LanguageLevel.HIGHEST.ordinal + 1), getLanguageLevelForModule())
  }

  @Test
  open fun testInheritingLanguageLevelFromPluginManagementSection() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testSettingTargetLevel() {
    JavacConfiguration.getOptions(myProject,
                                  JavacConfiguration::class.java).ADDITIONAL_OPTIONS_STRING = "-Xmm500m -Xms128m -target 1.5"
    importProject(("<groupId>test</groupId>" +
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
                          JavacConfiguration.getOptions(myProject,
                                                        JavacConfiguration::class.java).ADDITIONAL_OPTIONS_STRING.trim { it <= ' ' })
    TestCase.assertEquals("1.3", ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project")))
  }

  @Test
  open fun testSettingTargetLevelFromDefaultCompileExecutionConfiguration() {
    importProject(("<groupId>test</groupId>" +
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
    assertModules("project")
    TestCase.assertEquals(LanguageLevel.JDK_1_9, LanguageLevel.parse(
      ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project"))))
  }

  @Test
  open fun testSettingTargetLevelFromParent() {
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
    importProject()
    TestCase.assertEquals("1.3",
                          ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project")))
    TestCase.assertEquals("1.3",
                          ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
    TestCase.assertEquals("1.5",
                          ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m2"))))
  }

  @Test
  open fun testOverrideLanguageLevelFromParentPom() {
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
    importProject()
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
  }

  @Test
  open fun testReleaseHasPriorityInParentPom() {
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
    importProject()
    assertEquals(LanguageLevel.JDK_1_9, LanguageLevelUtil.getCustomLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals(LanguageLevel.JDK_1_9.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
  }

  @Test
  open fun testReleasePropertyNotSupport() {
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
    importProject()
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule(mn("project", "m1"))))
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(getModule(mn("project", "m1"))))
  }

  @Test
  open fun testCompilerPluginExecutionBlockProperty() {
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
    importProject()
    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule("project")))
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project")))
  }

  @Test
  fun testShouldResolveJavac() {

    createProjectPom(javacPom)
    importProject()

    TestCase.assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)

  }

  @Test
  fun testShouldResolveEclipseCompilerOnAutoDetect() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProject()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }


  @Test
  fun testShouldResolveEclipseAndSwitchToJavacCompiler() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProject()
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    createProjectPom(javacPom)
    importProject()

    TestCase.assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
  }


  @Test
  fun testShouldResolveEclipseAndStayOnEclipseCompiler() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProject()
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    createProjectPom(eclipsePom)
    importProject()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldSwitchToEclipseAfterJavac() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true

    createProjectPom(javacPom)
    importProject()
    TestCase.assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)

    createProjectPom(eclipsePom)
    importProject()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldNoSwitchToJavacIfFlagDisabled() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProject()
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = false

    createProjectPom(javacPom)
    importProject()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testShouldNotSwitchToJavacCompilerIfAutoDetectDisabled() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true

    createProjectPom(eclipsePom)
    importProject()
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    createProjectPom(javacPom)
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = false
    importProject()

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test
  fun testCompilerPluginLanguageLevel() {
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
    importProject()
    assertEquals(LanguageLevel.JDK_1_7, LanguageLevelUtil.getCustomLanguageLevel(getModule("project")))
    assertEquals(LanguageLevel.JDK_1_7,
                 LanguageLevel.parse(ideCompilerConfiguration.getBytecodeTargetLevel(getModule("project"))))
  }

  @Test
  open fun testCompilerPluginConfigurationCompilerArguments() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testCompilerPluginConfigurationCompilerArgumentsParameters() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testCompilerPluginConfigurationCompilerArgumentsParametersFalse() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyOverride() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyOverride1() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testCompilerPluginConfigurationCompilerArgumentsParametersProperty() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testCompilerPluginConfigurationCompilerArgumentsParametersPropertyFalse() {
    importProject(("<groupId>test</groupId>" +
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
  open fun testCompilerPluginConfigurationUnresolvedCompilerArguments() {
    importProject(("<groupId>test</groupId>" +
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

  // commenting the test as the errorProne module is not available to IJ community project
  // TODO move the test to the errorProne module
  //public void stestCompilerPluginErrorProneConfiguration() {
  //  importProject("<groupId>test</groupId>" +
  //                "<artifactId>project</artifactId>" +
  //                "<version>1</version>" +
  //
  //                "<build>" +
  //                "  <plugins>" +
  //                "    <plugin>" +
  //                "      <groupId>org.apache.maven.plugins</groupId>" +
  //                "      <artifactId>maven-compiler-plugin</artifactId>" +
  //                "      <configuration>" +
  //                "        <compilerId>javac-with-errorprone</compilerId>" +
  //                "        <compilerArgs>" +
  //                "          <arg>-XepAllErrorsAsWarnings</arg>" +
  //                "        </compilerArgs>" +
  //                "      </configuration>" +
  //                "    </plugin>" +
  //                "  </plugins>" +
  //                "</build>");
  //
  //  CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
  //  assertEquals("error-prone", compilerConfiguration.getDefaultCompiler().getId());
  //  assertUnorderedElementsAreEqual(compilerConfiguration.getAdditionalOptions(getModule("project")), "-XepAllErrorsAsWarnings");
  //
  //  importProject("<groupId>test</groupId>" +
  //                "<artifactId>project</artifactId>" +
  //                "<version>1</version>");
  //
  //  assertEquals("Javac", compilerConfiguration.getDefaultCompiler().getId());
  //  assertEmpty(compilerConfiguration.getAdditionalOptions(getModule("project")));
  //}

}
