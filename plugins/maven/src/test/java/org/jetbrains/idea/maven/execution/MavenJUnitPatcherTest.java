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

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.project.MavenProjectSettings;

import java.util.List;

import static java.util.Arrays.asList;

public class MavenJUnitPatcherTest extends MavenImportingTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassArgLine(true);
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassEnvironmentVariables(true);
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassSystemProperties(true);
  }

  public void testAddClassPath() {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>\n" +
                                           "<artifactId>m1</artifactId>\n" +
                                           "<version>1</version>\n" +
                                           "<dependencies>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>test</groupId>\n" +
                                           "    <artifactId>m2</artifactId>\n" +
                                           "    <version>1</version>\n" +
                                           "  </dependency>\n" +
                                           "</dependencies>\n" +
                                           "<build>\n" +
                                           "  <plugins>\n" +
                                           "    <plugin>\n" +
                                           "      <groupId>org.apache.maven.plugins</groupId>\n" +
                                           "      <artifactId>maven-surefire-plugin</artifactId>\n" +
                                           "      <version>2.16</version>\n" +
                                           "      <configuration>\n" +
                                           "        <additionalClasspathElements>\n" +
                                           "          <additionalClasspathElement>path/to/additional/resources</additionalClasspathElement>\n" +
                                           "          <additionalClasspathElement>path/to/additional/jar</additionalClasspathElement>\n" +
                                           "          <additionalClasspathElement>path/to/csv/jar1</additionalClasspathElement>\n" +
                                           "        </additionalClasspathElements>\n" +
                                           "      </configuration>\n" +
                                           "    </plugin>\n" +
                                           "  </plugins>\n" +
                                           "</build>\n");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    List<String> classPath = javaParameters.getClassPath().getPathList();
    assertEquals(asList("path/to/additional/resources", "path/to/additional/jar", "path/to/csv/jar1"), classPath);
  }

  public void testArgList() {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +
                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m2</artifactId>" +
                                           "    <version>1</version>" +
                                           "  </dependency>" +
                                           "</dependencies>" +
                                           "<build><plugins>" +
                                           "  <plugin>" +
                                           "    <groupId>org.apache.maven.plugins</groupId>" +
                                           "    <artifactId>maven-surefire-plugin</artifactId>" +
                                           "    <version>2.16</version>" +
                                           "    <configuration>" +
                                           "      <argLine>-Xmx2048M -XX:MaxPermSize=512M \"-Dargs=can have spaces\"</argLine>" +
                                           "    </configuration>" +
                                           "  </plugin>" +
                                           "</plugins></build>");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.getVMParametersList().getList());
  }

  public void testVmPropertiesResolve() {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +
                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m2</artifactId>" +
                                           "    <version>1</version>" +
                                           "  </dependency>" +
                                           "</dependencies>" +
                                           "<build><plugins>" +
                                           "  <plugin>" +
                                           "    <groupId>org.apache.maven.plugins</groupId>" +
                                           "    <artifactId>maven-surefire-plugin</artifactId>" +
                                           "    <version>2.16</version>" +
                                           "    <configuration>" +
                                           "      <argLine>-Xmx2048M -XX:MaxPermSize=512M \"-Dargs=can have spaces\" ${argLineApx}</argLine>" +
                                           "    </configuration>" +
                                           "  </plugin>" +
                                           "</plugins></build>");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().addProperty("argLineApx", "-DsomeKey=someValue");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-DargLineApx=-DsomeKey=someValue", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces", "-DsomeKey=someValue"),
                 javaParameters.getVMParametersList().getList());
  }

  public void testArgLineLateReplacement() {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +
                                           "<build><plugins>" +
                                           "  <plugin>" +
                                           "    <groupId>org.apache.maven.plugins</groupId>" +
                                           "    <artifactId>maven-surefire-plugin</artifactId>" +
                                           "    <version>2.16</version>" +
                                           "    <configuration>" +
                                           "      <argLine>@{argLine} -Xmx2048M -XX:MaxPermSize=512M \"-Dargs=can have spaces\"</argLine>" +
                                           "    </configuration>" +
                                           "  </plugin>" +
                                           "</plugins></build>");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().add("-ea");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-ea", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.getVMParametersList().getList());
  }
}
