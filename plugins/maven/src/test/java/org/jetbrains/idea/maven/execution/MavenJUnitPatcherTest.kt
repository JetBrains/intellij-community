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

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.project.MavenProjectSettings
import org.junit.Test
import java.io.File

class MavenJUnitPatcherTest : MavenMultiVersionImportingTestCase() {
  override fun runInDispatchThread() = true
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    MavenProjectSettings.getInstance(project).testRunningSettings.isPassArgLine = true
    MavenProjectSettings.getInstance(project).testRunningSettings.isPassEnvironmentVariables = true
    MavenProjectSettings.getInstance(project).testRunningSettings.isPassSystemProperties = true
  }

  @Test
  @Throws(CantRunException::class)
  fun ExcludeClassPathElement() {
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
      mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
      val classPath = javaParameters.classPath.getPathList()
      assertEquals(excludeSpecification, listOf("annotations-java5-17.0.0.jar"),
                   ContainerUtil.map(classPath) { path: String? -> File(path).getName() })
    }
  }

  @Test
  @Throws(CantRunException::class)
  fun ExcludeScope() {
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
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
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters)
    assertEquals(mutableListOf("-ea", "-Dfoo=bar"),
                 javaParameters.vmParametersList.getList())
  }
}
