/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.idea.TestFor
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.importing.MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE
import org.jetbrains.idea.maven.importing.MAVEN_DEFAULT_ANNOTATION_PROFILE
import org.jetbrains.idea.maven.importing.MavenAnnotationProcessorConfiguratorUtil.getModuleProfileName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class AnnotationProcessorImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testImportAnnotationProcessorProfiles() = runBlocking {
    maven.createModulePom("module1", """
<groupId>test</groupId>
<artifactId>module1</artifactId>
<version>1</version>
""")

    maven.createModulePom("module2", """
<groupId>test</groupId>
<artifactId>module2</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessors>
            <annotationProcessor>com.test.SourceCodeGeneratingAnnotationProcessor2</annotationProcessor>
          </annotationProcessors>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    maven.createModulePom("module3", """
<groupId>test</groupId>
<artifactId>module3</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
           <proc>none</proc>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    maven.createModulePom("module3_1", """
<groupId>test</groupId>
<artifactId>module3_1</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
                <executions>
                    <execution>
                        <id>default-compile</id>
                      <configuration>
                        <compilerArgument> -proc:none</compilerArgument>
                      </configuration>
                    </execution>
                </executions>
      </plugin>
    </plugins>
  </build>
""")

    maven.createModulePom("module4", """
<groupId>test</groupId>
<artifactId>module4</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessors>
          </annotationProcessors>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    maven.importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>module1</module>
  <module>module2</module>
  <module>module3</module>
  <module>module4</module>
</modules>

""")

    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    assertEquals(
      compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE)?.moduleNames,
      setOf("module1", "module4"))
    assertNull(compilerConfiguration.findModuleProcessorProfile(MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE))

    val projectProfile = compilerConfiguration.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(projectProfile)
    assertEquals(projectProfile!!.moduleNames, setOf("module2"))
    assertEquals(projectProfile.processors, setOf("com.test.SourceCodeGeneratingAnnotationProcessor2"))
    assertNull(compilerConfiguration.findModuleProcessorProfile(getModuleProfileName("module3")))
    assertNull(compilerConfiguration.findModuleProcessorProfile(getModuleProfileName("module3_1")))
    assertEquals(compilerConfiguration.moduleProcessorProfiles.size, 2)
  }

  @Test
  fun testOverrideGeneratedOutputDir() = runBlocking {
    maven.importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <generatedSourcesDirectory>out/generated</generatedSourcesDirectory>

      </configuration>
    </plugin>
  </plugins>
</build>
""")

    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    assertNull(compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE))
    val profile = compilerConfiguration.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(profile)
    assertTrue(profile!!.getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("out/generated"))
  }

  @Test
  fun testImportAnnotationProcessorOptions() = runBlocking {
    maven.importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <compilerArgument>-Aopt1=111 -Xmx512Mb -Aopt2=222</compilerArgument>
        <compilerArgs>
            <arg>-Aopt3=333</arg>
            <arg>-AjustKey</arg>
        </compilerArgs>
        <compilerArguments>
          <Aopt4>444</Aopt4>
          <opt>666</opt>
        </compilerArguments>
      </configuration>
    </plugin>
  </plugins>
</build>
""")

    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    assertNull(compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE))
    val processorOptions =
      compilerConfiguration.findModuleProcessorProfile(getModuleProfileName("project"))?.processorOptions
    assertEquals(processorOptions, mapOf("opt1" to "111", "opt2" to "222", "opt3" to "333", "opt4" to "444", "justKey" to ""))
  }

  @Test
  fun testMavenProcessorPlugin() = runBlocking {
    maven.importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<build>
  <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <compilerArgument>-proc:none</compilerArgument>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.bsc.maven</groupId>
                <artifactId>maven-processor-plugin</artifactId>

                <executions>
                    <execution>
                        <id>process</id>
                        <goals>
                            <goal>process</goal>
                        </goals>
                        <phase>generate-sources</phase>
                        <configuration>
                            <outputDirectory>target/metamodel</outputDirectory>
                            <!-- STANDARD WAY -->
                            <compilerArguments>-Amyoption1=TRUE</compilerArguments>
                            <!-- NEW FEATURE FROM VERSION 2.0.4-->
                            <options>
                                <myoption2>TRUE</myoption2>
                            </options>
                        </configuration>
                    </execution>
                    <execution>
                        <id>process-test</id>
                        <goals>
                            <goal>process-test</goal>
                        </goals>
                        <phase>generate-sources</phase>
                        <configuration>
                            <outputDirectory>target/metamodelTest</outputDirectory>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
  </plugins>
</build>
""")

    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    assertNull(compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE))
    val profile = compilerConfiguration.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(profile)
    assertTrue(profile!!.getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("target/metamodel"))
    assertTrue(profile.getGeneratedSourcesDirectoryName(true).replace('\\', '/').endsWith("target/metamodelTest"))
    assertEquals(profile.processorOptions, mapOf("myoption1" to "TRUE", "myoption2" to "TRUE"))
  }

  @Test
  fun testMavenProcessorPluginDefault() = runBlocking {
    maven.importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <compilerArgument>-Aopt1=111 -Xmx512Mb -Aopt2=222</compilerArgument>
        <compilerArgs>
            <arg>-proc:none</arg>
        </compilerArgs>
      </configuration>
    </plugin>

            <plugin>
                <groupId>org.bsc.maven</groupId>
                <artifactId>maven-processor-plugin</artifactId>

                <executions>
                    <execution>
                        <id>process</id>
                        <goals>
                            <goal>process</goal>
                        </goals>
                        <phase>generate-sources</phase>
                        <configuration>
                            <outputDirectory>target/metamodel</outputDirectory>
                        </configuration>
                    </execution>
                    <execution>
                        <id>process-test</id>
                        <goals>
                            <goal>process-test</goal>
                        </goals>
                        <phase>generate-sources</phase>
                        <configuration>
                            <outputDirectory>target/metamodelTest</outputDirectory>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
  </plugins>
</build>
""")

    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    assertNull(compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE))
    val profile = compilerConfiguration.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(profile)
    assertTrue(profile!!.getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("target/metamodel"))
    assertTrue(profile.getGeneratedSourcesDirectoryName(true).replace('\\', '/').endsWith("target/metamodelTest"))
  }

  @Test
  fun testProcessorsViaBscMavenPlugin() = runBlocking {
    maven.importProjectAsync("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
              <compilerArgument>-proc:none</compilerArgument>
          </configuration>
      </plugin>
      <plugin>
        <groupId>org.bsc.maven</groupId>
        <artifactId>maven-processor-plugin</artifactId>

        <executions>
          <execution>
            <id>process-entities</id>
            <goals>
                <goal>process</goal>
            </goals>
            <phase>generate-sources</phase>

            <configuration>
                <processors>
                    <processor>com.mysema.query.apt.jpa.JPAAnnotationProcessor</processor>
                    <processor>com.test.SourceCodeGeneratingAnnotationProcessor2</processor>
                </processors>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
""")

    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    assertEquals(compilerConfiguration.findModuleProcessorProfile(getModuleProfileName("project"))?.processors,
                 hashSetOf("com.test.SourceCodeGeneratingAnnotationProcessor2", "com.mysema.query.apt.jpa.JPAAnnotationProcessor"))
  }

  @Test
  fun testExternalDependencyPath() = runBlocking {
    maven.importProjectAsync("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>com.google.dagger</groupId>
            <artifactId>dagger-compiler</artifactId>
            <version>2.2</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>""")
    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertNotNull(mavenProject)

    val annotationProcessors = mavenProject!!.externalAnnotationProcessors
    UsefulTestCase.assertNotEmpty(annotationProcessors)

    assertTrue(
      annotationProcessors.any { "com.google.dagger" == it!!.groupId && "dagger-compiler" == it.artifactId && "2.2" == it.version })
    assertTrue(annotationProcessors.any { "com.google.dagger" == it!!.groupId && "dagger" == it.artifactId && "2.2" == it.version })

    val config = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl
    val projectProfile = config.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(projectProfile)
    val path = projectProfile!!.processorPath
    assertTrue(path.contains(FileUtil.toSystemDependentName("/com/google/dagger/dagger-compiler/2.2/dagger-compiler-2.2.jar")))
  }

  @Test
  fun testExternalDependencyAnnotationPath() = runBlocking {
    maven.importProjectAsync("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <annotationProcessorPath>
            <groupId>com.google.dagger</groupId>
            <artifactId>dagger-compiler</artifactId>
            <version>2.2</version>
          </annotationProcessorPath>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>""")

    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertNotNull(mavenProject)

    val annotationProcessors = mavenProject!!.externalAnnotationProcessors
    UsefulTestCase.assertNotEmpty(annotationProcessors)

    assertTrue(
      annotationProcessors.any { "com.google.dagger" == it!!.groupId && "dagger-compiler" == it.artifactId && "2.2" == it.version })
    assertTrue(annotationProcessors.any { "com.google.dagger" == it!!.groupId && "dagger" == it.artifactId && "2.2" == it.version })

    val config = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    val projectProfile = config.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(projectProfile)
    val path = projectProfile!!.processorPath
    assertTrue(path.contains(FileUtil.toSystemDependentName("/com/google/dagger/dagger-compiler/2.2/dagger-compiler-2.2.jar")))
  }

  @Test
  fun testLocalDependency() = runBlocking {
    maven.createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>
<modules>
  <module>m1</module>
  <module>m2</module>
</modules>""")

    maven.createModulePom("m1", """<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>19.0</version>
  </dependency>
</dependencies>""")

    maven.createModulePom("m2", """<groupId>test</groupId>
<artifactId>m2</artifactId>
<version>1</version>
<build>
  <plugins>
    <plugin>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>test</groupId>
            <artifactId>m1</artifactId>
            <version>1</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>""")
    maven.createProjectSubFile("m1/src/main/java/A.java", "public class A{}")
    maven.importProjectAsync()

    val module = maven.getModule("m2")
    assertNotNull(module)

    val mavenProject = maven.projectsManager.findProject(module)
    assertNotNull(mavenProject)

    val annotationProcessors = mavenProject!!.externalAnnotationProcessors
    UsefulTestCase.assertEmpty(annotationProcessors)

    val config = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    val defaultProfile = config.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE)
    assertNotNull(defaultProfile)
    UsefulTestCase.assertSameElements(defaultProfile!!.moduleNames, "m1")

    val projectProfile = config.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(projectProfile)
    UsefulTestCase.assertSameElements(projectProfile!!.moduleNames, "m2")
    val path = projectProfile.processorPath
    assertTrue(path.contains(FileUtil.toSystemDependentName("/m1/target/classes")))
    assertTrue(path.contains(FileUtil.toSystemDependentName("/com/google/guava/guava/19.0/guava-19.0.jar")))
  }

  @Test
  fun testDisabledAnnotationProcessor() = runBlocking {
    maven.importProjectAsync("""
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
  """.trimIndent())
    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    var profile = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE)
    assertNotNull(profile)
    assertTrue(profile!!.isEnabled)

    WriteAction.runAndWait<RuntimeException> {
      val p = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE)
      p!!.isEnabled = false
    }
    profile = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE)
    assertNotNull(profile)
    assertFalse(profile!!.isEnabled)

    maven.importProjectAsync()
    profile = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE)
    assertNotNull(profile)
    assertFalse(profile!!.isEnabled)
  }

  @Test
  fun testNotRemoveEmptyUserProfile() = runBlocking {
    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl
    WriteAction.runAndWait<RuntimeException> {
      compilerConfiguration.addNewProcessorProfile("test-profile").isEnabled = true
    }
    assertNotNull(compilerConfiguration.findModuleProcessorProfile("test-profile"))

    maven.importProjectAsync("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<packaging>pom</packaging>" +
                       "<version>1</version>")

    assertNotNull(compilerConfiguration.findModuleProcessorProfile("test-profile"))
  }

  @Test
  fun testRemoveEmptyInnerProfile() = runBlocking {
    val compilerConfiguration = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl
    val profileName = getModuleProfileName("test-profile")
    WriteAction.runAndWait<RuntimeException> {
      compilerConfiguration.addNewProcessorProfile(profileName).isEnabled = true
    }
    assertNotNull(compilerConfiguration.findModuleProcessorProfile(profileName))

    maven.importProjectAsync("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<packaging>pom</packaging>" +
                       "<version>1</version>")

    assertNull(compilerConfiguration.findModuleProcessorProfile(profileName))
  }

  @Test
  fun testImportManagedDependencyAnnotationProcessor() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1.0</version>
      <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.google.dagger</groupId>
                <artifactId>dagger-compiler</artifactId>
                <version>2.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
                <configuration>
                    <annotationProcessorPaths>
                         <path>
                            <groupId>com.google.dagger</groupId>
                            <artifactId>dagger-compiler</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
""")

    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertNotNull(mavenProject)

    val annotationProcessors = mavenProject!!.externalAnnotationProcessors
    assertNotEmpty(annotationProcessors)

    assertTrue(
      annotationProcessors.any { "com.google.dagger" == it!!.groupId && "dagger-compiler" == it.artifactId && "2.2" == it.version })
    assertTrue(annotationProcessors.any { "com.google.dagger" == it!!.groupId && "dagger" == it.artifactId && "2.2" == it.version })

    val config = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    val projectProfile = config.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(projectProfile)
    val path = projectProfile!!.processorPath
    assertTrue(path.contains(FileUtil.toSystemDependentName("/com/google/dagger/dagger-compiler/2.2/dagger-compiler-2.2.jar")))
  }

  @Test
  fun testImportManagedDependencyAnnotationProcessorFromExecution() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1.0</version>
      <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.google.dagger</groupId>
                <artifactId>dagger-compiler</artifactId>
                <version>2.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
                <executions>
                     <execution>
                        <id>myid</id>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>com.google.dagger</groupId>
                                    <artifactId>dagger-compiler</artifactId>
                                </path>
                            </annotationProcessorPaths>    
                        </configuration>
                     </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
""")

    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertNotNull(mavenProject)

    val annotationProcessors = mavenProject!!.externalAnnotationProcessors
    assertNotEmpty(annotationProcessors)

    assertTrue(
      annotationProcessors.any { "com.google.dagger" == it!!.groupId && "dagger-compiler" == it.artifactId && "2.2" == it.version })
    assertTrue(annotationProcessors.any { "com.google.dagger" == it!!.groupId && "dagger" == it.artifactId && "2.2" == it.version })

    val config = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    val projectProfile = config.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNotNull(projectProfile)
    val path = projectProfile!!.processorPath
    assertTrue(path.contains(FileUtil.toSystemDependentName("/com/google/dagger/dagger-compiler/2.2/dagger-compiler-2.2.jar")))
  }

  @Test
  @TestFor(issues = ["IDEA-368907"])
  fun testShouldNotImportManagedDependencyAnnotationProcessorForNonCompilePhases() = runBlocking {
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1.0</version>
      <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
                <executions>
                    <execution>
                        <id>log4j2-plugin-processor</id>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <phase>process-classes</phase>
                        <configuration>
                            <proc>only</proc>
                            <compileSourceRoots>${'$'}{project.build.outputDirectory}</compileSourceRoots>
                            <annotationProcessors>
                                <annotationProcessor>
                                    org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor
                                </annotationProcessor>
                            </annotationProcessors>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
      </build>  
      """)

    val mavenProject = maven.projectsManager.findProject(maven.getModule("project"))
    assertNotNull(mavenProject)

    val annotationProcessors = mavenProject!!.externalAnnotationProcessors
    assertEmpty(annotationProcessors)

    val config = CompilerConfiguration.getInstance(maven.project) as CompilerConfigurationImpl

    val projectProfile = config.findModuleProcessorProfile(getModuleProfileName("project"))
    assertNull(projectProfile)
  }
}
