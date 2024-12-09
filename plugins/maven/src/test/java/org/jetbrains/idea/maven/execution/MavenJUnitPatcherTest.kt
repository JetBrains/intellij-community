/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution

import com.intellij.execution.configurations.JavaParameters
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.PathUtil
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.project.MavenProjectSettings
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.name

class MavenJUnitPatcherTest : MavenMultiVersionImportingTestCase() {
  override fun setUp() = runBlocking {
    super.setUp()
    MavenProjectSettings.getInstance(project).testRunningSettings.isPassArgLine = true
    MavenProjectSettings.getInstance(project).testRunningSettings.isPassEnvironmentVariables = true
    MavenProjectSettings.getInstance(project).testRunningSettings.isPassSystemProperties = true
  }

  @Test
  fun ExcludeProjectDependencyInClassPathElement() = runBlocking {
    val m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>dep</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>2.16</version>
            <configuration>
              <classpathDependencyExcludes>
                <classpathDependencyExclude>test:dep</classpathDependencyExclude>
              </classpathDependencyExcludes>
            </configuration>
          </plugin>
        </plugins>
      </build>
      """.trimIndent())

    val dep = createModulePom("dep", """
      <groupId>test</groupId>
      <artifactId>dep</artifactId>
      <version>1</version>
      <dependencies>
      </dependencies>
      
      """.trimIndent())

    createProjectSubDirs("m/src/main/java",
                         "m/target/classes",
                         "dep/src/main/java",
                         "dep/target/classes")

    importProjects(m, dep)
    assertModules("m", "dep")
    assertModuleModuleDeps("m", "dep")

    val module = getModule("m")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()

    val pathTransformer = { path: String ->
      val nioPath = Paths.get(path)

      if (nioPath.name.endsWith(".jar")) {
        nioPath.name
      }
      else {
        nioPath.subpath(nioPath.nameCount - 3, nioPath.nameCount).toString()
      }
    }

    javaParameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS, IdeaTestUtil.getMockJdk18())
    assertEquals(listOf("dep/target/classes", "junit-4.0.jar", "m/target/classes").map(PathUtil::getLocalPath),
                 javaParameters.classPath.getPathList().map(pathTransformer).sorted())

    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(listOf("junit-4.0.jar", "m/target/classes").map(PathUtil::getLocalPath),
                 javaParameters.classPath.getPathList().map(pathTransformer).sorted())
  }

  @Test
  fun ExcludeClassPathElement() = runBlocking {
    val excludeSpecifications = arrayOf(
      """
<classpathDependencyExcludes>
<classpathDependencyExclude>org.jetbrains:annotations</classpathDependencyExclude>
</classpathDependencyExcludes>
""".trimIndent(),
      """
<classpathDependencyExcludes>
<classpathDependencyExcludes>org.jetbrains:annotations</classpathDependencyExcludes>
</classpathDependencyExcludes>
""".trimIndent(),
      """
<classpathDependencyExcludes>
<dependencyExclude>org.jetbrains:annotations</dependencyExclude>
</classpathDependencyExcludes>
""".trimIndent(),
      """
<classpathDependencyExcludes>
org.jetbrains:annotations,
org.jetbrains:annotations
</classpathDependencyExcludes>
""".trimIndent(),
    )
    for (excludeSpecification in excludeSpecifications) {
      @Language(value = "XML", prefix = "<project>", suffix = "</project>") val pom = """<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>org.jetbrains</groupId>
    <artifactId>annotations</artifactId>
    <version>17.0.0</version>
  </dependency>
  <dependency>
    <groupId>org.jetbrains</groupId>
    <artifactId>annotations-java5</artifactId>
    <version>17.0.0</version>
  </dependency>
</dependencies>
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>2.16</version>
      <configuration>
        $excludeSpecification
      </configuration>
    </plugin>
  </plugins>
</build>
"""
      val m1 = createModulePom("m1", pom)

      importProjects(m1)
      val module = getModule("m1")

      val mavenJUnitPatcher = MavenJUnitPatcher()
      val javaParameters = JavaParameters()
      javaParameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS, IdeaTestUtil.getMockJdk18())
      assertEquals(excludeSpecification, mutableListOf("annotations-17.0.0.jar", "annotations-java5-17.0.0.jar"),
                   ContainerUtil.map(javaParameters.classPath.getPathList()) { path: String? -> File(path).getName() })
      patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
      val classPath = javaParameters.classPath.getPathList()
      assertEquals(excludeSpecification, listOf("annotations-java5-17.0.0.jar"),
                   ContainerUtil.map(classPath) { path: String? -> File(path).getName() })
    }
  }

  @Test
  fun ExcludeScope() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>org.jetbrains</groupId>
          <artifactId>annotations</artifactId>
          <version>17.0.0</version>
          <scope>runtime</scope>
        </dependency>
        <dependency>
          <groupId>org.jetbrains</groupId>
          <artifactId>annotations-java5</artifactId>
          <version>17.0.0</version>
        </dependency>
      </dependencies>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>2.16</version>
            <configuration>
              <classpathDependencyScopeExclude>compile</classpathDependencyScopeExclude>
            </configuration>
          </plugin>
        </plugins>
      </build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS, IdeaTestUtil.getMockJdk18())
    assertEquals(mutableListOf("annotations-17.0.0.jar", "annotations-java5-17.0.0.jar"),
                 ContainerUtil.map(javaParameters.classPath.getPathList()) { path: String? -> File(path).getName() })
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    val classPath = javaParameters.classPath.getPathList()
    assertEquals(listOf("annotations-17.0.0.jar"),
                 ContainerUtil.map(classPath) { path: String? -> File(path).getName() })
  }

  @Test
  fun AddClassPath() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>2.16</version>
            <configuration>
              <additionalClasspathElements>
                <additionalClasspathElement>path/to/additional/resources</additionalClasspathElement>
                <additionalClasspathElement>path/to/additional/jar</additionalClasspathElement>
                <additionalClasspathElement>path/to/csv/jar1, path/to/csv/jar2</additionalClasspathElement>
              </additionalClasspathElements>
            </configuration>
          </plugin>
        </plugins>
      </build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    val classPath = javaParameters.classPath.getPathList()
    assertEquals(mutableListOf("path/to/additional/resources", "path/to/additional/jar", "path/to/csv/jar1", "path/to/csv/jar2"), classPath)
  }

  @Test
  fun ArgList() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      <build><plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>2.16</version>
          <configuration>
            <argLine>-Xmx2048M -XX:MaxPermSize=512M "-Dargs=can have spaces"</argLine>
          </configuration>
        </plugin>
      </plugins></build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(mutableListOf("-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.vmParametersList.getList())
  }

  @Test
  fun IgnoreJaCoCoOption() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId><artifactId>m1</artifactId><version>1</version><build>
        <plugins>
          <plugin>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>2.22.0</version>
            <configuration>
              <argLine>-Dmyprop=abc @{jacoco} @{unresolved}</argLine>
            </configuration>
          </plugin>
          <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.3</version>
            <configuration>
              <propertyName>jacoco</propertyName>
            </configuration>
          </plugin>
        </plugins>
      </build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.addParametersString("-ea")
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(mutableListOf("-ea", "-Dmyprop=abc", "@{unresolved}"),
                 javaParameters.vmParametersList.getList())
  }

  @Test
  fun ImplicitArgLine() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId><artifactId>m1</artifactId><version>1</version><properties>
        <argLine>-Dfoo=${'$'}{version}</argLine>
      </properties>

      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>2.22.1</version>
          </plugin>
        </plugins>
      </build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(mutableListOf("-Dfoo=1"),
                 javaParameters.vmParametersList.getList())
  }

  @Test
  fun VmPropertiesResolve() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      <build><plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>2.16</version>
          <configuration>
            <argLine>-Xmx2048M -XX:MaxPermSize=512M "-Dargs=can have spaces" ${'$'}{argLineApx}</argLine>
          </configuration>
        </plugin>
      </plugins></build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.addProperty("argLineApx", "-DsomeKey=someValue")
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(
      mutableListOf("-DargLineApx=-DsomeKey=someValue", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces",
                    "-DsomeKey=someValue"),
      javaParameters.vmParametersList.getList())
  }

  @Test
  fun ArgLineLateReplacement() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <build><plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>2.16</version>
          <configuration>
            <argLine>@{argLine} -Xmx2048M -XX:MaxPermSize=512M "-Dargs=can have spaces"</argLine>
          </configuration>
        </plugin>
      </plugins></build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.add("-ea")
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(mutableListOf("-ea", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.vmParametersList.getList())
  }

  @Test
  fun ArgLineLateReplacementParentProperty() = runBlocking {
    createProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <packaging>pom</packaging>
        <properties>
          <parentProp>parent.value</parentProp>
        </properties>
        <modules>
          <module>m1</module>
        </modules>
        """.trimIndent())

    createModulePom(
      "m1",
      """
          <groupId>test</groupId>
          <artifactId>m1</artifactId>
          <version>1</version>
          <properties>
            <moduleProp>module.value</moduleProp>
          </properties>
          <parent>
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1</version>
          </parent>
          <build>
            <plugins>
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>2.16</version>
                <configuration>
                  <argLine>@{moduleProp} @{parentProp}</argLine>
                </configuration>
              </plugin>
            </plugins>
          </build>
          """.trimIndent())

    importProjectAsync()
    val module = getModule(mn("project", "m1"))

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.add("-ea")
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(
      mutableListOf("-ea", "module.value", "parent.value"),
      javaParameters.vmParametersList.getList())
  }

  @Test
  fun ArgLineRefersAnotherProperty() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <properties>
        <app.testing.jvm.args>-Xms256m -Xmx1524m -Duser.language=en</app.testing.jvm.args>
        <argLine>${'$'}{app.testing.jvm.args}</argLine>
      </properties>
      <build><plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>2.16</version>
          <configuration>
              <argLine>@{argLine}</argLine>
          </configuration>
        </plugin>
      </plugins></build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.add("-ea")
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(mutableListOf("-ea", "-Xms256m", "-Xmx1524m", "-Duser.language=en"),
                 javaParameters.vmParametersList.getList())
  }

  @Test
  fun ArgLineProperty() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId><artifactId>m1</artifactId><version>1</version><properties>
      <argLine>-DsomeProp=Hello</argLine>
      </properties><build><plugins>  <plugin>    <groupId>org.apache.maven.plugins</groupId>    <artifactId>maven-surefire-plugin</artifactId>    <version>2.16</version>    <configuration>      <argLine>@{argLine} -Xmx2048M -XX:MaxPermSize=512M "-Dargs=can have spaces"</argLine>    </configuration>  </plugin></plugins></build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.add("-ea")
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(mutableListOf("-ea", "-DsomeProp=Hello", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.vmParametersList.getList())
  }

  @Test
  fun ResolvePropertiesUsingAt() = runBlocking {
    val m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <properties>
          <test.argLine>-Dfoo=bar</test.argLine>
      </properties>
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>2.16</version>
            <configuration>
              <argLine>@{test.argLine}</argLine>
            </configuration>
          </plugin>
        </plugins>
      </build>
      """.trimIndent())

    importProjects(m1)
    val module = getModule("m1")

    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.add("-ea")
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)
    assertEquals(mutableListOf("-ea", "-Dfoo=bar"),
                 javaParameters.vmParametersList.getList())
  }

  @Test
  fun `should replace test dependency on dependency with classifier`() = runBlocking {
    Registry.get("maven.build.additional.jars").setValue("true", getTestRootDisposable())
    val lib = createModulePom("library", """
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>library</artifactId>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.1.2</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>test-jar</goal>
                        </goals>
                        <configuration>
                            <classifier>some-classifier</classifier>
                            <skipIfEmpty>true</skipIfEmpty>
                            <includes>
                                <include>included/**</include>
                            </includes>
                            <excludes>
                                <exclude>excluded/**</exclude>
                            </excludes>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
""")

    val app = createModulePom("application", """
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>application</artifactId>
    <packaging>jar</packaging>
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>test</groupId>
            <artifactId>library</artifactId>
            <classifier>some-classifier</classifier>
            <type>test-jar</type>
            <scope>compile</scope>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
""")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <packaging>pom</packaging>
      <modules>
          <module>library</module>
          <module>application</module>
      </modules>
""")

    val module = getModule("application")
    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.add("-ea")
    javaParameters.classPath.add(buildDir("application/target/classes"))
    javaParameters.classPath.add(buildDir("application/target/test-classes"))
    javaParameters.classPath.add(buildDir("library/target/classes"))
    javaParameters.classPath.add(buildDir("library/target/test-classes"))
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)

    val pathList = javaParameters.classPath.pathList.mapNotNull {
      FileUtil.getRelativePath(File(projectPath), File(it))
    }
    assertOrderedEquals(
      pathList,
      "application/target/classes",
      "application/target/test-classes",
      "library/target/classes",
      "library/target/test-classes-jar-some-classifier"
    )
  }


  @Test
  fun `should add classpath compile dependency on dependency with classifier if two classifiers are present`() = runBlocking {
    Registry.get("maven.build.additional.jars").setValue("true", getTestRootDisposable())
    val lib = createModulePom("library", """
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>library</artifactId>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.1.2</version>
                <executions>
                    <execution>
                        <id>default-jar</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                        <configuration>
                            <excludes>
                                <exclude>excluded-another/**</exclude>
                            </excludes>
                        </configuration>
                    </execution>
                    <execution>
                        <id>some-execution</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                        <configuration>
                            <classifier>some-classifier</classifier>
                            <skipIfEmpty>true</skipIfEmpty>
                            <includes>
                                <include>included/**</include>
                            </includes>
                            <excludes>
                                <exclude>excluded/**</exclude>
                            </excludes>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
""")

    val app = createModulePom("application", """
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>application</artifactId>
    <packaging>jar</packaging>
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>test</groupId>
            <artifactId>library</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>test</groupId>
            <artifactId>library</artifactId>
            <classifier>some-classifier</classifier> 
            <scope>compile</scope>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
""")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <packaging>pom</packaging>
      <modules>
          <module>library</module>
          <module>application</module>
      </modules>
""")

    val module = getModule("application")
    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.add("-ea")
    javaParameters.classPath.add(buildDir("application/target/classes"))
    javaParameters.classPath.add(buildDir("application/target/test-classes"))
    javaParameters.classPath.add(buildDir("library/target/classes"))
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)

    val pathList = javaParameters.classPath.pathList.mapNotNull {
      FileUtil.getRelativePath(File(projectPath), File(it))
    }
    assertOrderedEquals(
      pathList,
      "application/target/classes",
      "application/target/test-classes",
      "library/target/classes",
      "library/target/classes-jar-some-classifier",
    )
  }

  @Test
  fun `should replace classpath compile dependency on dependency with classifier`() = runBlocking {
    Registry.get("maven.build.additional.jars").setValue("true", getTestRootDisposable())
    val lib = createModulePom("library", """
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>library</artifactId>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.1.2</version>
                <executions>
                    <execution>
                        <id>default-jar</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                        <configuration>
                            <excludes>
                                <exclude>excluded-another/**</exclude>
                            </excludes>
                        </configuration>
                    </execution>
                    <execution>
                        <id>some-execution</id>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                        <configuration>
                            <classifier>some-classifier</classifier>
                            <skipIfEmpty>true</skipIfEmpty>
                            <includes>
                                <include>included/**</include>
                            </includes>
                            <excludes>
                                <exclude>excluded/**</exclude>
                            </excludes>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
""")

    val app = createModulePom("application", """
      <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>application</artifactId>
    <packaging>jar</packaging>
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>test</groupId>
            <artifactId>library</artifactId>
            <classifier>some-classifier</classifier> 
            <scope>compile</scope>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
""")

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <packaging>pom</packaging>
      <modules>
          <module>library</module>
          <module>application</module>
      </modules>
""")

    val module = getModule("application")
    val mavenJUnitPatcher = MavenJUnitPatcher()
    val javaParameters = JavaParameters()
    javaParameters.vmParametersList.add("-ea")
    javaParameters.classPath.add(buildDir("application/target/classes"))
    javaParameters.classPath.add(buildDir("application/target/test-classes"))
    javaParameters.classPath.add(buildDir("library/target/classes"))
    patchJavaParameters(mavenJUnitPatcher, module, javaParameters)

    val pathList = javaParameters.classPath.pathList.mapNotNull {
      FileUtil.getRelativePath(File(projectPath), File(it))
    }
    assertOrderedEquals(
      pathList,
      "application/target/classes",
      "application/target/test-classes",
      "library/target/classes-jar-some-classifier",
    )
  }

  private fun buildDir(path: String): File {
    return File(File(projectPath), path)
  }

  private suspend fun patchJavaParameters(mavenJUnitPatcher: MavenJUnitPatcher, module: Module, javaParameters: JavaParameters) {
    readAction { mavenJUnitPatcher.patchJavaParameters(module, javaParameters) }
  }
}
