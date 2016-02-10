/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.idea.maven.MavenImportingTestCase
import org.jetbrains.idea.maven.importing.configurers.MavenAnnotationProcessorConfigurer

/**
 * @author Sergey Evdokimov
 */
@SuppressWarnings("GroovyPointlessBoolean")
class AnnotationProcessorImportingTest extends MavenImportingTestCase {

  public void testImportAnnotationProcessorProfiles() throws Exception {
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

    importProject """
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

""";

    def compilerConfiguration = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject))

    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.MAVEN_DEFAULT_ANNOTATION_PROFILE).getModuleNames() == new HashSet<String>(["module1", "module4"])
    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE) == null

    def projectProfile = compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.PROFILE_PREFIX + 'project')
    assert projectProfile.getModuleNames() == new HashSet<String>(["module2"])
    assert projectProfile.getProcessors() == new HashSet<String>(["com.test.SourceCodeGeneratingAnnotationProcessor2"])
    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.PROFILE_PREFIX + 'module3') == null
    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.PROFILE_PREFIX + 'module3_1') == null
    assert compilerConfiguration.moduleProcessorProfiles.size() == 2
  }

  public void testOverrideGeneratedOutputDir() {
    importProject """
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
""";

    def compilerConfiguration = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject))

    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.MAVEN_DEFAULT_ANNOTATION_PROFILE) == null
    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.PROFILE_PREFIX + "project").getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("out/generated")
  }

  public void testImportAnnotationProcessorOptions() {
    importProject """
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
""";

    def compilerConfiguration = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject))

    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.MAVEN_DEFAULT_ANNOTATION_PROFILE) == null
    def processorOptions = compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.PROFILE_PREFIX + "project").getProcessorOptions()
    assert new HashMap(processorOptions) == ['opt1': '111', 'opt2': '222', 'opt3': '333', 'opt4': '444', 'justKey': '']
  }

  public void testMavenProcessorPlugin() {
    importProject """
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
""";

    def compilerConfiguration = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject))

    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.MAVEN_DEFAULT_ANNOTATION_PROFILE) == null
    def profile = compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.PROFILE_PREFIX + "project")
    assert profile.getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("target/metamodel")
    assert profile.getGeneratedSourcesDirectoryName(true).replace('\\', '/').endsWith("target/metamodelTest")
    assert new HashMap(profile.getProcessorOptions()) == ['myoption1' : 'TRUE', 'myoption2' : 'TRUE']
  }

  public void testMavenProcessorPluginDefault() {
    importProject """
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
""";

    def compilerConfiguration = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject))

    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.MAVEN_DEFAULT_ANNOTATION_PROFILE) == null
    def profile = compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.PROFILE_PREFIX + "project")
    assert profile.getGeneratedSourcesDirectoryName(false).replace('\\', '/').endsWith("target/metamodel")
    assert profile.getGeneratedSourcesDirectoryName(true).replace('\\', '/').endsWith("target/metamodelTest")
  }

  public void testProcessorsViaBscMavenPlugin() {
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

    def compilerConfiguration = ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject))

    assert compilerConfiguration.findModuleProcessorProfile(MavenAnnotationProcessorConfigurer.PROFILE_PREFIX + 'project').getProcessors() ==
           new HashSet<String>(["com.test.SourceCodeGeneratingAnnotationProcessor2", "com.mysema.query.apt.jpa.JPAAnnotationProcessor"])
  }
}
