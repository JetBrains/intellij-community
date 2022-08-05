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
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import junit.framework.TestCase
import org.jetbrains.idea.maven.importing.MavenAnnotationProcessorImporter
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class AnnotationProcessorImportingTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testImportAnnotationProcessorProfiles() {
    createModulePom("module1", """
<groupId>test</groupId>
<artifactId>module1</artifactId>
<version>1</version>
""")

    createModulePom("module2", """
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

    createModulePom("module3", """
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

    createModulePom("module3_1", """
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

    createModulePom("module4", """
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

    importProject("""
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

    val compilerConfiguration = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl

    assertEquals(
      compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.MAVEN_DEFAULT_ANNOTATION_PROFILE)?.getModuleNames(),
      setOf("module1", "module4"))
    assertNull(compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE))

    val projectProfile = compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.PROFILE_PREFIX + "project")
    assertNotNull(projectProfile)
    assertEquals(projectProfile!!.getModuleNames(), setOf("module2"))
    assertEquals(projectProfile.getProcessors(), setOf("com.test.SourceCodeGeneratingAnnotationProcessor2"))
    assertNull(compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.PROFILE_PREFIX + "module3"))
    assertNull(compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.PROFILE_PREFIX + "module3_1"))
    assertEquals(compilerConfiguration.moduleProcessorProfiles.size, 2)
  }

  @Test
  fun testOverrideGeneratedOutputDir() {
    importProject("""
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

    val compilerConfiguration = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl

    assertNull(compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.MAVEN_DEFAULT_ANNOTATION_PROFILE))
    val profile = compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.PROFILE_PREFIX + "project")
    assertNotNull(profile)
    assertTrue(profile!!.getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("out/generated"))
  }

  @Test
  fun testImportAnnotationProcessorOptions() {
    importProject("""
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

    val compilerConfiguration = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl

    assertNull(compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.MAVEN_DEFAULT_ANNOTATION_PROFILE))
    val processorOptions =
      compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.PROFILE_PREFIX + "project")?.getProcessorOptions()
    assertEquals(processorOptions, mapOf("opt1" to "111", "opt2" to "222", "opt3" to "333", "opt4" to "444", "justKey" to ""))
  }

  @Test
  fun testMavenProcessorPlugin() {
    importProject("""
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

    val compilerConfiguration = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl

    assertNull(compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.MAVEN_DEFAULT_ANNOTATION_PROFILE))
    val profile = compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.PROFILE_PREFIX + "project")
    assertNotNull(profile)
    assertTrue(profile!!.getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("target/metamodel"))
    assertTrue(profile.getGeneratedSourcesDirectoryName(true).replace('\\', '/').endsWith("target/metamodelTest"))
    assertEquals(profile.getProcessorOptions(), mapOf("myoption1" to "TRUE", "myoption2" to "TRUE"))
  }

  @Test
  fun testMavenProcessorPluginDefault() {
    importProject("""
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

    val compilerConfiguration = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl

    assertNull(compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.MAVEN_DEFAULT_ANNOTATION_PROFILE))
    val profile = compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.PROFILE_PREFIX + "project")
    assertNotNull(profile)
    assertTrue(profile!!.getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("target/metamodel"))
    assertTrue(profile.getGeneratedSourcesDirectoryName(true).replace('\\', '/').endsWith("target/metamodelTest"))
  }

  @Test
  fun testProcessorsViaBscMavenPlugin() {
    importProject("""
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

    val compilerConfiguration = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl

    TestCase.assertEquals(
      compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorImporter.PROFILE_PREFIX + "project")?.getProcessors(),
      hashSetOf("com.test.SourceCodeGeneratingAnnotationProcessor2", "com.mysema.query.apt.jpa.JPAAnnotationProcessor"))
  }
}
