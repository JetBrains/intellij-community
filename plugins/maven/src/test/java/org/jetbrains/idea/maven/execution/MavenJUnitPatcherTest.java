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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.idea.maven.project.MavenProjectSettings;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public class MavenJUnitPatcherTest extends MavenMultiVersionImportingTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassArgLine(true);
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassEnvironmentVariables(true);
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassSystemProperties(true);
  }

  @Test
  public void ExcludeClassPathElement() throws CantRunException {
    String[] excludeSpecifications = {
      """
<classpathDependencyExcludes>
<classpathDependencyExclude>org.jetbrains:annotations</classpathDependencyExclude>
</classpathDependencyExcludes>""",
      """
<classpathDependencyExcludes>
<classpathDependencyExcludes>org.jetbrains:annotations</classpathDependencyExcludes>
</classpathDependencyExcludes>""",
      """
<classpathDependencyExcludes>
<dependencyExclude>org.jetbrains:annotations</dependencyExclude>
</classpathDependencyExcludes>""",
      """
<classpathDependencyExcludes>
org.jetbrains:annotations,
org.jetbrains:annotations
</classpathDependencyExcludes>""",
    };
    for (String excludeSpecification : excludeSpecifications) {
      @Language(value = "XML", prefix = "<project>", suffix = "</project>")
      String pom = "<groupId>test</groupId>\n" +
                   "<artifactId>m1</artifactId>\n" +
                   "<version>1</version>\n" +
                   "<dependencies>\n" +
                   "  <dependency>\n" +
                   "    <groupId>org.jetbrains</groupId>\n" +
                   "    <artifactId>annotations</artifactId>\n" +
                   "    <version>17.0.0</version>\n" +
                   "  </dependency>\n" +
                   "  <dependency>\n" +
                   "    <groupId>org.jetbrains</groupId>\n" +
                   "    <artifactId>annotations-java5</artifactId>\n" +
                   "    <version>17.0.0</version>\n" +
                   "  </dependency>\n" +
                   "</dependencies>\n" +
                   "<build>\n" +
                   "  <plugins>\n" +
                   "    <plugin>\n" +
                   "      <groupId>org.apache.maven.plugins</groupId>\n" +
                   "      <artifactId>maven-surefire-plugin</artifactId>\n" +
                   "      <version>2.16</version>\n" +
                   "      <configuration>\n" +
                   "        " +
                   excludeSpecification +
                   "\n" +
                   "      </configuration>\n" +
                   "    </plugin>\n" +
                   "  </plugins>\n" +
                   "</build>\n";
      VirtualFile m1 = createModulePom("m1", pom);

      importProjects(m1);
      Module module = getModule("m1");

      MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
      JavaParameters javaParameters = new JavaParameters();
      javaParameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS, IdeaTestUtil.getMockJdk18());
      assertEquals(excludeSpecification, asList("annotations-17.0.0.jar", "annotations-java5-17.0.0.jar"),
                   ContainerUtil.map(javaParameters.getClassPath().getPathList(), path -> new File(path).getName()));
      mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
      List<String> classPath = javaParameters.getClassPath().getPathList();
      assertEquals(excludeSpecification, Collections.singletonList("annotations-java5-17.0.0.jar"),
                   ContainerUtil.map(classPath, path -> new File(path).getName()));
    }
  }

  @Test
  public void ExcludeScope() throws CantRunException {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS, IdeaTestUtil.getMockJdk18());
    assertEquals(asList("annotations-17.0.0.jar", "annotations-java5-17.0.0.jar"),
                 ContainerUtil.map(javaParameters.getClassPath().getPathList(), path -> new File(path).getName()));
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    List<String> classPath = javaParameters.getClassPath().getPathList();
    assertEquals(Collections.singletonList("annotations-17.0.0.jar"),
                 ContainerUtil.map(classPath, path -> new File(path).getName()));
  }

  @Test
  public void AddClassPath() {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    List<String> classPath = javaParameters.getClassPath().getPathList();
    assertEquals(asList("path/to/additional/resources", "path/to/additional/jar", "path/to/csv/jar1", "path/to/csv/jar2"), classPath);
  }

  @Test
  public void ArgList() {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.getVMParametersList().getList());
  }

  @Test
  public void IgnoreJaCoCoOption() {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().addParametersString("-ea");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-ea", "-Dmyprop=abc", "@{unresolved}"),
                 javaParameters.getVMParametersList().getList());
  }

  @Test
  public void ImplicitArgLine() {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId><artifactId>m1</artifactId><version>1</version><properties>
        <argLine>-Dfoo=${version}</argLine>
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
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-Dfoo=1"),
                 javaParameters.getVMParametersList().getList());
  }

  @Test
  public void VmPropertiesResolve() {
    VirtualFile m1 = createModulePom("m1", """
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
            <argLine>-Xmx2048M -XX:MaxPermSize=512M "-Dargs=can have spaces" ${argLineApx}</argLine>
          </configuration>
        </plugin>
      </plugins></build>
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().addProperty("argLineApx", "-DsomeKey=someValue");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(
      asList("-DargLineApx=-DsomeKey=someValue", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces", "-DsomeKey=someValue"),
      javaParameters.getVMParametersList().getList());
  }

  @Test
  public void ArgLineLateReplacement() {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().add("-ea");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-ea", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.getVMParametersList().getList());
  }

  @Test
  public void ArgLineLateReplacementParentProperty() {
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
        """);

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
          """);

    importProject();
    Module module = getModule(mn("project", "m1"));

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().add("-ea");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(
        asList("-ea", "module.value", "parent.value"),
        javaParameters.getVMParametersList().getList());
  }

  @Test
  public void ArgLineRefersAnotherProperty() {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <properties>
        <app.testing.jvm.args>-Xms256m -Xmx1524m -Duser.language=en</app.testing.jvm.args>
        <argLine>${app.testing.jvm.args}</argLine>
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
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().add("-ea");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-ea", "-Xms256m", "-Xmx1524m", "-Duser.language=en"),
                 javaParameters.getVMParametersList().getList());
  }

  @Test
  public void ArgLineProperty() {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId><artifactId>m1</artifactId><version>1</version><properties>
      <argLine>-DsomeProp=Hello</argLine>
      </properties><build><plugins>  <plugin>    <groupId>org.apache.maven.plugins</groupId>    <artifactId>maven-surefire-plugin</artifactId>    <version>2.16</version>    <configuration>      <argLine>@{argLine} -Xmx2048M -XX:MaxPermSize=512M "-Dargs=can have spaces"</argLine>    </configuration>  </plugin></plugins></build>""");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().add("-ea");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-ea", "-DsomeProp=Hello", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.getVMParametersList().getList());
  }

  @Test
  public void ResolvePropertiesUsingAt() {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().add("-ea");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-ea", "-Dfoo=bar"),
                 javaParameters.getVMParametersList().getList());
  }
}
