/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.model.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectReaderTest extends MavenTestCase {
  public void testBasics() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    MavenId p = readProject(myProjectPom).getMavenId();

    assertEquals("test", p.getGroupId());
    assertEquals("project", p.getArtifactId());
    assertEquals("1", p.getVersion());
  }

  public void testInvalidXml() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    assertProblems(readProject(myProjectPom, new NullProjectLocator()));

    createProjectPom("<foo>" +
                     "</bar>" +
                     "<" +
                     "<groupId>test</groupId" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    MavenProjectReaderResult result = readProject(myProjectPom, new NullProjectLocator());
    assertProblems(result, "'pom.xml' has syntax errors");
    MavenId p = result.mavenModel.getMavenId();

    assertEquals("test", p.getGroupId());
    assertEquals("project", p.getArtifactId());
    assertEquals("1", p.getVersion());
  }

  public void testInvalidXmlCharData() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    assertProblems(readProject(myProjectPom, new NullProjectLocator()));

    createProjectPom("<name>a" + new String(new byte[]{0x0}) + "a</name><fo" + new String(new byte[]{0x0}) + "o></foo>");

    MavenProjectReaderResult result = readProject(myProjectPom, new NullProjectLocator());
    assertProblems(result, "'pom.xml' has syntax errors");
    MavenModel p = result.mavenModel;

    assertEquals("a0x0a", p.getName());
  }

  public void testInvalidParentXml() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +
                     "<foo");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    assertProblems(readProject(module, new NullProjectLocator()), "Parent 'test:parent:1' has problems");
  }

  public void testProjectWithAbsentParentXmlIsValid() throws Exception {
    createProjectPom("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>");
    assertProblems(readProject(myProjectPom, new NullProjectLocator()));
  }

  public void testProjectWithSelfParentIsInvalid() throws Exception {
    createProjectPom("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +

                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>");
    assertProblems(readProject(myProjectPom, new NullProjectLocator()), "Self-inheritance found");
  }

  public void testInvalidProfilesXml() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    createProfilesXml("<profiles");

    assertProblems(readProject(myProjectPom, new NullProjectLocator()), "'profiles.xml' has syntax errors");
  }

  public void testInvalidSettingsXml() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    updateSettingsXml("<settings");

    assertProblems(readProject(myProjectPom, new NullProjectLocator()), "'settings.xml' has syntax errors");
  }

  public void testInvalidXmlWithNotClosedTag() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1" +
                     "<name>foo</name>");

    MavenProjectReaderResult readResult = readProject(myProjectPom, new NullProjectLocator());
    assertProblems(readResult, "'pom.xml' has syntax errors");
    MavenModel p = readResult.mavenModel;

    assertEquals("test", p.getMavenId().getGroupId());
    assertEquals("project", p.getMavenId().getArtifactId());
    assertEquals("Unknown", p.getMavenId().getVersion());
    assertEquals("foo", p.getName());
  }

  public void testInvalidXmlWithWrongClosingTag() throws Exception {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</vers>" +
                     "<name>foo</name>");

    MavenProjectReaderResult readResult = readProject(myProjectPom, new NullProjectLocator());
    assertProblems(readResult, "'pom.xml' has syntax errors");
    MavenModel p = readResult.mavenModel;

    assertEquals("test", p.getMavenId().getGroupId());
    assertEquals("project", p.getMavenId().getArtifactId());
    assertEquals("1", p.getMavenId().getVersion());
    assertEquals("foo", p.getName());
  }

  public void testEmpty() throws Exception {
    createProjectPom("");

    MavenModel p = readProject(myProjectPom);

    assertEquals("Unknown", p.getMavenId().getGroupId());
    assertEquals("Unknown", p.getMavenId().getArtifactId());
    assertEquals("Unknown", p.getMavenId().getVersion());
  }

  public void testSpaces() throws Exception {
    createProjectPom("<name>foo bar</name>");

    MavenModel p = readProject(myProjectPom);
    assertEquals("foo bar", p.getName());
  }

  public void testNewLines() throws Exception {
    createProjectPom("<groupId>\n" +
                     "  group\n" +
                     "</groupId>\n" +
                     "<artifactId>\n" +
                     "  artifact\n" +
                     "</artifactId>\n" +
                     "<version>\n" +
                     "  1\n" +
                     "</version>\n");

    MavenModel p = readProject(myProjectPom);
    assertEquals(new MavenId("group", "artifact", "1"), p.getMavenId());
  }

  public void testCommentsWithNewLinesInTags() throws Exception {
    createProjectPom("<groupId>test<!--a-->\n" +
                     "</groupId>" +
                     "<artifactId>\n" +
                     "<!--a-->project</artifactId>" +
                     "<version>1\n" +
                     "<!--a--></version>" +
                     "<name>\n" +
                     "<!--a-->\n" +
                     "</name>");

    MavenModel p = readProject(myProjectPom);
    MavenId id = p.getMavenId();

    assertEquals("test", id.getGroupId());
    assertEquals("project", id.getArtifactId());
    assertEquals("1", id.getVersion());
    assertNull(p.getName());
  }

  public void testTextInContainerTag() throws Exception {
    createProjectPom("foo <name>name</name> bar");

    MavenModel p = readProject(myProjectPom);
    assertEquals("name", p.getName());
  }

  public void testDefaults() throws Exception {
    VirtualFile file = new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        VirtualFile res = myProjectRoot.createChildData(this, "pom.xml");
        result.setResult(res);
        VfsUtil.saveText(res, "<project>" +
                               "  <groupId>test</groupId>" +
                               "  <artifactId>project</artifactId>" +
                               "  <version>1</version>" +
                               "</project>");
      }
    }.execute().getResultObject();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    MavenModel p = readProject(file);

    assertEquals("jar", p.getPackaging());
    assertNull(p.getName());
    assertNull(p.getParent());

    assertEquals("project-1", p.getBuild().getFinalName());
    assertEquals(null, p.getBuild().getDefaultGoal());
    assertSize(1, p.getBuild().getSources());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("src/main/java"), p.getBuild().getSources().get(0));
    assertSize(1, p.getBuild().getTestSources());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("src/test/java"), p.getBuild().getTestSources().get(0));
    assertEquals(1, p.getBuild().getResources().size());
    assertResource(p.getBuild().getResources().get(0), pathFromBasedir("src/main/resources"),
                   false, null, Collections.<String>emptyList(), Collections.<String>emptyList());
    assertEquals(1, p.getBuild().getTestResources().size());
    assertResource(p.getBuild().getTestResources().get(0), pathFromBasedir("src/test/resources"),
                   false, null, Collections.<String>emptyList(), Collections.<String>emptyList());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target"), p.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target/classes"), p.getBuild().getOutputDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target/test-classes"), p.getBuild().getTestOutputDirectory());
  }

  public void testDefaultsForParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  dummy" +
                     "</parent>");

    MavenModel p = readProject(myProjectPom);

    assertParent(p, "Unknown", "Unknown", "Unknown");
  }

  public void testTakingCoordinatesFromParent() throws Exception {
    createProjectPom("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>project</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>");

    MavenId id = readProject(myProjectPom).getMavenId();

    assertEquals("test", id.getGroupId());
    assertEquals("Unknown", id.getArtifactId());
    assertEquals("1", id.getVersion());
  }

  public void testCustomSettings() throws Exception {
    VirtualFile file = new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        VirtualFile res = myProjectRoot.createChildData(this, "pom.xml");
        result.setResult(res);
        VfsUtil.saveText(res, "<project>" +
                               "  <modelVersion>1.2.3</modelVersion>" +
                               "  <groupId>test</groupId>" +
                               "  <artifactId>project</artifactId>" +
                               "  <version>1</version>" +
                               "  <name>foo</name>" +
                               "  <packaging>pom</packaging>" +

                               "  <parent>" +
                               "    <groupId>testParent</groupId>" +
                               "    <artifactId>projectParent</artifactId>" +
                               "    <version>2</version>" +
                               "    <relativePath>../parent/pom.xml</relativePath>" +
                               "  </parent>" +

                               "  <build>" +
                               "    <finalName>xxx</finalName>" +
                               "    <defaultGoal>someGoal</defaultGoal>" +
                               "    <sourceDirectory>mySrc</sourceDirectory>" +
                               "    <testSourceDirectory>myTestSrc</testSourceDirectory>" +
                               "    <scriptSourceDirectory>myScriptSrc</scriptSourceDirectory>" +
                               "    <resources>" +
                               "      <resource>" +
                               "        <directory>myRes</directory>" +
                               "        <filtering>true</filtering>" +
                               "        <targetPath>dir</targetPath>" +
                               "        <includes><include>**.properties</include></includes>" +
                               "        <excludes><exclude>**.xml</exclude></excludes>" +
                               "      </resource>" +
                               "    </resources>" +
                               "    <testResources>" +
                               "      <testResource>" +
                               "        <directory>myTestRes</directory>" +
                               "        <includes><include>**.properties</include></includes>" +
                               "      </testResource>" +
                               "    </testResources>" +
                               "    <directory>myOutput</directory>" +
                               "    <outputDirectory>myClasses</outputDirectory>" +
                               "    <testOutputDirectory>myTestClasses</testOutputDirectory>" +
                               "  </build>" +
                               "</project>");
      }
    }.execute().getResultObject();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    MavenModel p = readProject(file);

    assertEquals("pom", p.getPackaging());
    assertEquals("foo", p.getName());

    assertParent(p, "testParent", "projectParent", "2");

    assertEquals("xxx", p.getBuild().getFinalName());
    assertEquals("someGoal", p.getBuild().getDefaultGoal());
    assertSize(1, p.getBuild().getSources());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("mySrc"), p.getBuild().getSources().get(0));
    assertSize(1, p.getBuild().getTestSources());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myTestSrc"), p.getBuild().getTestSources().get(0));
    assertEquals(1, p.getBuild().getResources().size());
    assertResource(p.getBuild().getResources().get(0), pathFromBasedir("myRes"),
                   true, "dir", Collections.singletonList("**.properties"), Collections.singletonList("**.xml"));
    assertEquals(1, p.getBuild().getTestResources().size());
    assertResource(p.getBuild().getTestResources().get(0), pathFromBasedir("myTestRes"),
                   false, null, Collections.singletonList("**.properties"), Collections.<String>emptyList());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myOutput"), p.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myClasses"), p.getBuild().getOutputDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myTestClasses"), p.getBuild().getTestOutputDirectory());
  }

  public void testOutputPathsAreBasedOnTargetPath() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<build>" +
                     "  <directory>my-target</directory>" +
                     "</build>");

    MavenModel p = readProject(myProjectPom);

    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target"), p.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/classes"), p.getBuild().getOutputDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/test-classes"), p.getBuild().getTestOutputDirectory());
  }

  public void testDoesNotIncludeResourcesWithoutDirectory() throws Exception {
    createProjectPom("<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory></directory>" +
                     "    </resource>" +
                     "  </resources>" +
                     "  <testResources>" +
                     "    <testResource>" +
                     "      <filtering>true</filtering>" +
                     "    </testResource>" +
                     "  </testResources>" +
                     "</build>");

    MavenModel p = readProject(myProjectPom);

    assertEquals(0, p.getBuild().getResources().size());
    assertEquals(0, p.getBuild().getTestResources().size());
  }

  public void testPathsWithProperties() throws Exception {
    createProjectPom("<properties>" +
                     "  <foo>subDir</foo>" +
                     "  <emptyProperty />" +
                     "</properties>" +
                     "<build>" +
                     "  <sourceDirectory>${foo}/mySrc</sourceDirectory>" +
                     "  <testSourceDirectory>${foo}/myTestSrc</testSourceDirectory>" +
                     "  <scriptSourceDirectory>${foo}/myScriptSrc</scriptSourceDirectory>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>${foo}/myRes</directory>" +
                     "    </resource>" +
                     "    <resource>" +
                     "      <directory>aaa/${emptyProperty}/${unexistingProperty}</directory>" +
                     "    </resource>" +
                     "  </resources>" +
                     "  <testResources>" +
                     "    <testResource>" +
                     "      <directory>${foo}/myTestRes</directory>" +
                     "    </testResource>" +
                     "  </testResources>" +
                     "  <directory>${foo}/myOutput</directory>" +
                     "  <outputDirectory>${foo}/myClasses</outputDirectory>" +
                     "  <testOutputDirectory>${foo}/myTestClasses</testOutputDirectory>" +
                     "</build>");

    MavenModel p = readProject(myProjectPom);

    assertSize(1, p.getBuild().getSources());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/mySrc"), p.getBuild().getSources().get(0));
    assertSize(1, p.getBuild().getTestSources());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myTestSrc"), p.getBuild().getTestSources().get(0));
    assertEquals(2, p.getBuild().getResources().size());
    assertResource(p.getBuild().getResources().get(0), pathFromBasedir("subDir/myRes"),
                   false, null, Collections.<String>emptyList(), Collections.<String>emptyList());
    assertResource(p.getBuild().getResources().get(1), pathFromBasedir("aaa/${unexistingProperty}"),
                   false, null, Collections.<String>emptyList(), Collections.<String>emptyList());
    assertEquals(1, p.getBuild().getTestResources().size());
    assertResource(p.getBuild().getTestResources().get(0), pathFromBasedir("subDir/myTestRes"),
                   false, null, Collections.<String>emptyList(), Collections.<String>emptyList());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myOutput"), p.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myClasses"), p.getBuild().getOutputDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myTestClasses"), p.getBuild().getTestOutputDirectory());
  }

  public void testExpandingProperties() throws Exception {
    createProjectPom("<properties>" +
                     "  <prop1>value1</prop1>" +
                     "  <prop2>value2</prop2>" +
                     "</properties>" +

                     "<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>");
    MavenModel p = readProject(myProjectPom);

    assertEquals("value1", p.getName());
    assertEquals("value2", p.getPackaging());
  }

  public void testExpandingPropertiesRecursively() throws Exception {
    createProjectPom("<properties>" +
                     "  <prop1>value1</prop1>" +
                     "  <prop2>${prop1}2</prop2>" +
                     "</properties>" +

                     "<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>");
    MavenModel p = readProject(myProjectPom);

    assertEquals("value1", p.getName());
    assertEquals("value12", p.getPackaging());
  }

  public void testHandlingRecursiveProperties() throws Exception {
    createProjectPom("<properties>" +
                     "  <prop1>${prop2}</prop1>" +
                     "  <prop2>${prop1}</prop2>" +
                     "</properties>" +

                     "<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>");
    MavenModel p = readProject(myProjectPom);

    assertEquals("${prop1}", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testHandlingRecursionProprielyAndDoNotForgetCoClearRecursionGuard() throws Exception {
    File repositoryPath = new File(myDir, "repository");
    setRepositoryPath(repositoryPath.getPath());

    File parentFile = new File(repositoryPath, "test/parent/1/parent-1.pom");
    parentFile.getParentFile().mkdirs();
    FileUtil.writeToFile(parentFile, createPomXml("<groupId>test</groupId>" +
                                                  "<artifactId>parent</artifactId>" +
                                                  "<version>1</version>").getBytes());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>not-a-project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     " <groupId>test</groupId>" +
                     " <artifactId>parent</artifactId>" +
                     " <version>1</version>" +
                     "</parent>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>child</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        " <groupId>test</groupId>" +
                                        " <artifactId>parent</artifactId>" +
                                        " <version>1</version>" +
                                        "</parent>");

    MavenProjectReaderResult readResult = readProject(child, new NullProjectLocator());
    assertProblems(readResult);
  }

  public void testDoNotGoIntoRecursionWhenTryingToResolveParentInDefaultPath() throws Exception {
    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>child</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>subChild</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>child</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>child/pom.xml</relativePath>" +
                     "</parent>");

    MavenProjectReaderResult readResult = readProject(child, new NullProjectLocator());
    assertProblems(readResult);
  }

  public void testExpandingSystemAndEnvProperties() throws Exception {
    createProjectPom("<name>${java.home}</name>" +
                     "<packaging>${env." + getEnvVar() + "}</packaging>");

    MavenModel p = readProject(myProjectPom);
    assertEquals(System.getProperty("java.home"), p.getName());
    assertEquals(System.getenv(getEnvVar()), p.getPackaging());
  }

  public void testExpandingPropertiesFromProfiles() throws Exception {
    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    MavenModel p = readProject(myProjectPom);
    assertEquals("value1", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testExpandingPropertiesFromManuallyActivatedProfiles() throws Exception {
    createProjectPom("<name>${prop1}</name>" +
                     "<packaging>${prop2}</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "    <properties>" +
                     "      <prop1>value1</prop1>" +
                     "    </properties>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <properties>" +
                     "      <prop2>value2</prop2>" +
                     "    </properties>" +
                     "  </profile>" +
                     "</profiles>");

    MavenModel p = readProject(myProjectPom, "two");
    assertEquals("${prop1}", p.getName());
    assertEquals("value2", p.getPackaging());
  }

  public void testExpandingPropertiesFromParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <prop>value</prop>" +
                     "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    MavenModel p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testDoNotExpandPropertiesFromParentWithWrongCoordinates() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <prop>value</prop>" +
                     "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>invalid</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    MavenModel p = readProject(module);
    assertEquals("${prop}", p.getName());
  }

  public void testExpandingPropertiesFromParentNotInVfs() throws Exception {
    FileUtil.writeToFile(new File(myProjectRoot.getPath(), "pom.xml"),
                         createPomXml("<groupId>test</groupId>" +
                                      "<artifactId>parent</artifactId>" +
                                      "<version>1</version>" +

                                      "<properties>" +
                                      "  <prop>value</prop>" +
                                      "</properties>").getBytes());

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    MavenModel p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromIndirectParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <prop>value</prop>" +
                     "</properties>");

    createModulePom("module",
                    "<groupId>test</groupId>" +
                    "<artifactId>module</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>parent</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>");

    VirtualFile subModule = createModulePom("module/subModule",
                                            "<parent>" +
                                            "  <groupId>test</groupId>" +
                                            "  <artifactId>module</artifactId>" +
                                            "  <version>1</version>" +
                                            "</parent>" +
                                            "<name>${prop}</name>");

    MavenModel p = readProject(subModule);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInSpecifiedLocation() throws Exception {
    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>" +

                    "<properties>" +
                    "  <prop>value</prop>" +
                    "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "  <relativePath>../parent/pom.xml</relativePath>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    MavenModel p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInSpecifiedLocationWithoutFile() throws Exception {
    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>" +

                    "<properties>" +
                    "  <prop>value</prop>" +
                    "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "  <relativePath>../parent</relativePath>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    MavenModel p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInRepository() throws Exception {
    File repositoryPath = new File(myDir, "repository");
    setRepositoryPath(repositoryPath.getPath());

    File parentFile = new File(repositoryPath, "org/test/parent/1/parent-1.pom");
    parentFile.getParentFile().mkdirs();
    FileUtil.writeToFile(parentFile,
                         createPomXml("<groupId>org.test</groupId>" +
                                      "<artifactId>parent</artifactId>" +
                                      "<version>1</version>" +

                                      "<properties>" +
                                      "  <prop>value</prop>" +
                                      "</properties>").getBytes());

    createProjectPom("<parent>" +
                     "  <groupId>org.test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>" +
                     "<name>${prop}</name>");

    MavenModel p = readProject(myProjectPom);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInInvalidLocation() throws Exception {
    final VirtualFile parent = createModulePom("parent",
                                               "<groupId>test</groupId>" +
                                               "<artifactId>parent</artifactId>" +
                                               "<version>1</version>" +

                                               "<properties>" +
                                               "  <prop>value</prop>" +
                                               "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +
                                         "<name>${prop}</name>");

    MavenModel p = readProject(module, new MavenProjectReaderProjectLocator() {
      @Override
      public VirtualFile findProjectFile(MavenId coordinates) {
        return new MavenId("test", "parent", "1").equals(coordinates) ? parent : null;
      }
    }).mavenModel;
    assertEquals("value", p.getName());
  }

  public void testPropertiesFromParentInParentSection() throws Exception {
    createProjectPom("<groupId>${groupProp}</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>${versionProp}</version>" +

                     "<properties>" +
                     "  <groupProp>test</groupProp>" +
                     "  <versionProp>1</versionProp>" +
                     "</properties>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>${groupProp}</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>${versionProp}</version>" +
                                         "</parent>" +
                                         "<artifactId>module</artifactId>");

    MavenId id = readProject(module).getMavenId();
    assertEquals("test:module:1", id.getGroupId() + ":" + id.getArtifactId() + ":" + id.getVersion());
  }

  public void testInheritingSettingsFromParentAndAlignCorrectly() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +
                     "<build>" +
                     "  <directory>custom</directory>" +
                     "</build>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    MavenModel p = readProject(module);
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.getParent(), "custom"), p.getBuild().getDirectory());
  }

  public void testExpandingPropertiesAfterInheritingSettingsFromParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <prop>subDir</prop>" +
                     "</properties>" +
                     "<build>" +
                     "  <directory>${basedir}/${prop}/custom</directory>" +
                     "</build>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    MavenModel p = readProject(module);
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.getParent(), "subDir/custom"), p.getBuild().getDirectory());
  }

  public void testExpandingPropertiesAfterInheritingSettingsFromParentProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <properties>" +
                     "      <prop>subDir</prop>" +
                     "    </properties>" +
                     "    <build>" +
                     "      <directory>${basedir}/${prop}/custom</directory>" +
                     "    </build>" +
                     "  </profile>" +
                     "</profiles>");

    VirtualFile module = createModulePom("module",
                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    MavenModel p = readProject(module, "one");
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.getParent(), "subDir/custom"), p.getBuild().getDirectory());
  }

  public void testPropertiesFromProfilesXmlOldStyle() throws Exception {
    createProjectPom("<name>${prop}</name>");
    createProfilesXmlOldStyle("<profile>" +
                              "  <id>one</id>" +
                              "  <properties>" +
                              "    <prop>foo</prop>" +
                              "  </properties>" +
                              "</profile>");

    MavenModel mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testPropertiesFromProfilesXmlNewStyle() throws Exception {
    createProjectPom("<name>${prop}</name>");
    createProfilesXml("<profile>" +
                      "  <id>one</id>" +
                      "  <properties>" +
                      "    <prop>foo</prop>" +
                      "  </properties>" +
                      "</profile>");

    MavenModel mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testPropertiesFromSettingsXml() throws Exception {
    createProjectPom("<name>${prop}</name>");
    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <properties>" +
                      "      <prop>foo</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");

    MavenModel mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testDoNoInheritParentFinalNameIfUnspecified() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>");

    VirtualFile module = createModulePom("module",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>module</artifactId>" +
                                         "<version>2</version>" +

                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    MavenModel p = readProject(module, "one");
    assertEquals("module-2", p.getBuild().getFinalName());
  }

  public void testDoInheritingParentFinalNameIfSpecified() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <finalName>xxx</finalName>" +
                     "</build>");

    VirtualFile module = createModulePom("module",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>module</artifactId>" +
                                         "<version>2</version>" +

                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>");

    MavenModel p = readProject(module, "one");
    assertEquals("xxx", p.getBuild().getFinalName());
  }


  public void testInheritingParentProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>profileFromParent</id>" +
                     "  </profile>" +
                     "</profiles>");

    VirtualFile module = createModulePom("module",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>module</artifactId>" +
                                         "<version>1</version>" +

                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +

                                         "<profiles>" +
                                         "  <profile>" +
                                         "    <id>profileFromChild</id>" +
                                         "  </profile>" +
                                         "</profiles>");

    MavenModel p = readProject(module);
    assertOrderedElementsAreEqual(ContainerUtil.map(p.getProfiles(), (Function<MavenProfile, Object>)profile -> profile.getId()), "profileFromChild", "profileFromParent");
  }

  public void testCorrectlyCollectProfilesFromDifferentSources() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>profile</id>" +
                     "    <modules><module>parent</module></modules>" +
                     "  </profile>" +
                     "</profiles>");

    final VirtualFile parentProfiles = createProfilesXml("<profile>" +
                                                         "  <id>profile</id>" +
                                                         "  <modules><module>parentProfiles</module></modules>" +
                                                         "</profile>");

    VirtualFile module = createModulePom("module",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>module</artifactId>" +
                                         "<version>1</version>" +

                                         "<parent>" +
                                         "  <groupId>test</groupId>" +
                                         "  <artifactId>parent</artifactId>" +
                                         "  <version>1</version>" +
                                         "</parent>" +

                                         "<profiles>" +
                                         "  <profile>" +
                                         "    <id>profile</id>" +
                                         "    <modules><module>pom</module></modules>" +
                                         "  </profile>" +
                                         "</profiles>");

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>profile</id>" +
                      "    <modules><module>settings</module></modules>" +
                      "  </profile>" +
                      "</profiles>");

    final VirtualFile profiles = createProfilesXml("module",
                                                   "<profile>" +
                                                   "  <id>profile</id>" +
                                                   "  <modules><module>profiles</module></modules>" +
                                                   "</profile>");

    MavenModel p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEquals("pom", p.getProfiles().get(0).getModules().get(0));
    assertEquals("pom", p.getProfiles().get(0).getSource());

    createModulePom("module",
                    "<groupId>test</groupId>" +
                    "<artifactId>module</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>parent</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>");

    p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEquals("profiles", p.getProfiles().get(0).getModules().get(0));
    assertEquals("profiles.xml", p.getProfiles().get(0).getSource());

    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        profiles.delete(this);
      }
    }.execute().throwException();


    p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEmpty("parent", p.getProfiles().get(0).getModules());
    assertEquals("pom", p.getProfiles().get(0).getSource());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>");

    p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEmpty("parentProfiles", p.getProfiles().get(0).getModules());
    assertEquals("profiles.xml", p.getProfiles().get(0).getSource());

    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        parentProfiles.delete(null);
      }
    }.execute().throwException();


    p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEmpty("settings", p.getProfiles().get(0).getModules());
    assertEquals("settings.xml", p.getProfiles().get(0).getSource());
  }

  public void testModulesAreNotInheritedFromParentsProfiles() throws Exception {
    VirtualFile p = createProjectPom("<groupId>test</groupId>" +
                                     "<artifactId>project</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<profiles>" +
                                     " <profile>" +
                                     "  <id>one</id>" +
                                     "   <modules>" +
                                     "    <module>m</module>" +
                                     "   </modules>" +
                                     " </profile>" +
                                     "</profiles>");

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>" +
                                         "<parent>" +
                                         " <groupId>test</groupId>" +
                                         " <artifactId>project</artifactId>" +
                                         " <version>1</version>" +
                                         "</parent>");

    assertSize(1, readProject(p, "one").getModules());
    assertSize(0, readProject(m, "one").getModules());
  }

  public void testActivatingProfilesByDefault() throws Exception {
    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <activeByDefault>false</activeByDefault>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesAfterResolvingInheritance() throws Exception {
    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>");

    createProjectPom("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>parent/pom.xml</relativePath>" +
                     "</parent>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByOS() throws Exception {
    String os = SystemInfo.isWindows ? "windows" : SystemInfo.isMac ? "mac" : "unix";

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <os><family>" + os + "</family></os>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <os><family>xxx</family></os>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByJdk() throws Exception {
    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <jdk>[1.5,)</jdk>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <jdk>(,1.5)</jdk>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByStrictJdkVersion() throws Exception {
    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <jdk>1.4</jdk>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles();
  }

  public void testActivatingProfilesByProperty() throws Exception {
    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <property>" +
                     "        <name>os.name</name>" +
                     "        <value>" + System.getProperty("os.name") + "</value>" +
                     "      </property>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <property>" +
                     "        <name>os.name</name>" +
                     "        <value>xxx</value>" +
                     "      </property>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByEnvProperty() throws Exception {
    String value = System.getenv(getEnvVar());

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <property>" +
                     "        <name>env." + getEnvVar() + "</name>" +
                     "        <value>" + value + "</value>" +
                     "      </property>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <property>" +
                     "        <name>ffffff</name>" +
                     "        <value>ffffff</value>" +
                     "      </property>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByFile() throws Exception {
    createProjectSubFile("dir/file.txt");

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <activation>" +
                     "      <file>" +
                     "        <exists>${basedir}/dir/file.txt</exists>" +
                     "      </file>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <activation>" +
                     "      <file>" +
                     "        <missing>${basedir}/dir/file.txt</missing>" +
                     "      </file>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("one");
  }

  public void testActivateDefaultProfileEventIfThereAreExplicitOnesButAbsent() throws Exception {
    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>default</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>explicit</id>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles(Arrays.asList("foofoofoo"), "default");
  }

  public void testDoNotActivateDefaultProfileIfThereAreActivatedImplicit() throws Exception {
    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>default</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>implicit</id>" +
                     "    <activation>" +
                     "      <jdk>[1.5,)</jdk>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("implicit");
  }

  public void testActivatingImplicitProfilesEventWhenThereAreExplicitOnes() throws Exception {
    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>explicit</id>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>implicit</id>" +
                     "    <activation>" +
                     "      <jdk>[1.5,)</jdk>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles(Arrays.asList("explicit"), "explicit", "implicit");
  }

  public void testAlwaysActivatingActiveProfilesInSettingsXml() throws Exception {
    updateSettingsXml("<activeProfiles>" +
                      "  <activeProfile>settings</activeProfile>" +
                      "</activeProfiles>");

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>explicit</id>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>settings</id>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("settings");
    assertActiveProfiles(Arrays.asList("explicit"), "explicit", "settings");
  }

  public void testAlwaysActivatingActiveProfilesInProfilesXml() throws Exception {
    createFullProfilesXml("<?xml version=\"1.0\"?>" +
                          "<profilesXml>" +
                          "  <activeProfiles>" +
                          "    <activeProfile>profiles</activeProfile>" +
                          "  </activeProfiles>" +
                          "</profilesXml>");

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>explicit</id>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>profiles</id>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("profiles");
    assertActiveProfiles(Arrays.asList("explicit"), "explicit", "profiles");
  }

  public void testActivatingBothActiveProfilesInSettingsXmlAndImplicitProfiles() throws Exception {
    updateSettingsXml("<activeProfiles>" +
                      "  <activeProfile>settings</activeProfile>" +
                      "</activeProfiles>");

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>implicit</id>" +
                     "    <activation>" +
                     "      <jdk>[1.5,)</jdk>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>settings</id>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("settings", "implicit");
  }

  public void testDoNotActivateDefaultProfilesWhenThereAreAlwaysOnProfilesInPomXml() throws Exception {
    updateSettingsXml("<activeProfiles>" +
                      "  <activeProfile>settings</activeProfile>" +
                      "</activeProfiles>");

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>default</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>settings</id>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("settings");
  }

  public void testActivateDefaultProfilesWhenThereAreActiveProfilesInSettingsXml() throws Exception {
    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>settings</id>" +
                      "  </profile>" +
                      "</profiles>" +
                      "<activeProfiles>" +
                      "  <activeProfile>settings</activeProfile>" +
                      "</activeProfiles>");

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>default</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("default", "settings");
  }

  public void testActivateDefaultProfilesWhenThereAreActiveProfilesInProfilesXml() throws Exception {
    createFullProfilesXml("<?xml version=\"1.0\"?>" +
                          "<profilesXml>" +
                          "  <profiles>" +
                          "    <profile>" +
                          "      <id>profiles</id>" +
                          "    </profile>" +
                          "  </profiles>" +
                          "  <activeProfiles>" +
                          "    <activeProfile>profiles</activeProfile>" +
                          "  </activeProfiles>" +
                          "</profilesXml>");

    createProjectPom("<profiles>" +
                     "  <profile>" +
                     "    <id>default</id>" +
                     "    <activation>" +
                     "      <activeByDefault>true</activeByDefault>" +
                     "    </activation>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("default", "profiles");
  }

  public void testActiveProfilesInSettingsXmlOrProfilesXmlThroughInheritance() throws Exception {
    updateSettingsXml("<activeProfiles>" +
                      "  <activeProfile>settings</activeProfile>" +
                      "</activeProfiles>");

    createFullProfilesXml("parent",
                          "<?xml version=\"1.0\"?>" +
                          "<profilesXml>" +
                          "  <activeProfiles>" +
                          "    <activeProfile>parent</activeProfile>" +
                          "  </activeProfiles>" +
                          "</profilesXml>");

    createModulePom("parent",
                    "<groupId>test</groupId>" +
                    "<artifactId>parent</artifactId>" +
                    "<version>1</version>");

    createFullProfilesXml("<?xml version=\"1.0\"?>" +
                          "<profilesXml>" +
                          "  <activeProfiles>" +
                          "    <activeProfile>project</activeProfile>" +
                          "  </activeProfiles>" +
                          "</profilesXml>");


    createProjectPom("<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>parent/pom.xml</relativePath>" +
                     "</parent>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>project</id>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>parent</id>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>settings</id>" +
                     "  </profile>" +
                     "</profiles>");

    assertActiveProfiles("project", "settings");
  }

  private MavenModel readProject(VirtualFile file, String... profiles) {
    MavenProjectReaderResult readResult = readProject(file, new NullProjectLocator(), profiles);
    assertProblems(readResult);
    return readResult.mavenModel;
  }

  private MavenProjectReaderResult readProject(VirtualFile file,
                                               MavenProjectReaderProjectLocator locator,
                                               String... profiles) {
    MavenProjectReaderResult result = new MavenProjectReader().readProject(getMavenGeneralSettings(),
                                                                           file,
                                                                           new MavenExplicitProfiles(Arrays.asList(profiles)),
                                                                           locator);
    return result;
  }

  private static void assertParent(MavenModel p,
                                   String groupId,
                                   String artifactId,
                                   String version) {
    MavenId parent = p.getParent().getMavenId();
    assertEquals(groupId, parent.getGroupId());
    assertEquals(artifactId, parent.getArtifactId());
    assertEquals(version, parent.getVersion());
  }

  private static void assertResource(MavenResource resource,
                                     String dir,
                                     boolean filtered,
                                     String targetPath,
                                     List<String> includes,
                                     List<String> excludes) {
    PlatformTestUtil.assertPathsEqual(dir, resource.getDirectory());
    assertEquals(filtered, resource.isFiltered());
    PlatformTestUtil.assertPathsEqual(targetPath, resource.getTargetPath());
    assertOrderedElementsAreEqual(resource.getIncludes(), includes);
    assertOrderedElementsAreEqual(resource.getExcludes(), excludes);
  }

  private static void assertProblems(MavenProjectReaderResult readerResult, String... expectedProblems) {
    List<String> actualProblems = new ArrayList<>();
    for (MavenProjectProblem each : readerResult.readingProblems) {
      actualProblems.add(each.getDescription());
    }
    assertOrderedElementsAreEqual(actualProblems, expectedProblems);
  }

  private void assertActiveProfiles(String... expected) {
    assertActiveProfiles(Collections.<String>emptyList(), expected);
  }

  private void assertActiveProfiles(List<String> explicitProfiles, String... expected) {
    MavenProjectReaderResult result =
      readProject(myProjectPom, new NullProjectLocator(), ArrayUtil.toStringArray(explicitProfiles));
    assertUnorderedElementsAreEqual(result.activatedProfiles.getEnabledProfiles(), expected);
  }

  private static class NullProjectLocator implements MavenProjectReaderProjectLocator {
    @Override
    public VirtualFile findProjectFile(MavenId coordinates) {
      return null;
    }
  }
}
