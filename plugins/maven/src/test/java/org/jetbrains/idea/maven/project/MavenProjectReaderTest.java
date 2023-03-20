// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenProfile;
import org.jetbrains.idea.maven.model.MavenResource;
import org.junit.Assume;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectReaderTest extends MavenProjectReaderTestCase {
  public void testBasics() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    MavenId p = readProject(myProjectPom).getMavenId();

    assertEquals("test", p.getGroupId());
    assertEquals("project", p.getArtifactId());
    assertEquals("1", p.getVersion());
  }

  public void testInvalidXml() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    assertProblems(readProject(myProjectPom, new NullProjectLocator()));

    createProjectPom("""
                       <foo>
                       </bar>
                       <<groupId>test</groupId<artifactId>project</artifactId>
                       <version>1</version>
                       """);

    MavenProjectReaderResult result = readProject(myProjectPom, new NullProjectLocator());
    assertProblems(result, "'pom.xml' has syntax errors");
    MavenId p = result.mavenModel.getMavenId();

    assertEquals("test", p.getGroupId());
    assertEquals("project", p.getArtifactId());
    assertEquals("1", p.getVersion());
  }

  public void testInvalidXmlCharData() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    assertProblems(readProject(myProjectPom, new NullProjectLocator()));

    createProjectPom("<name>a" +
                     new String(new byte[]{0x0}, StandardCharsets.UTF_8) +
                     "a</name><fo" +
                     new String(new byte[]{0x0},
                                StandardCharsets.UTF_8) +
                     "o></foo>\n");

    MavenProjectReaderResult result = readProject(myProjectPom, new NullProjectLocator());
    assertProblems(result, "'pom.xml' has syntax errors");
    MavenModel p = result.mavenModel;

    assertEquals("a0x0a", p.getName());
  }

  public void testInvalidParentXml() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <foo""");

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """);

    assertProblems(readProject(module, new NullProjectLocator()), "Parent 'test:parent:1' has problems");
  }

  public void testProjectWithAbsentParentXmlIsValid() {
    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       """);
    assertProblems(readProject(myProjectPom, new NullProjectLocator()));
  }

  public void testProjectWithSelfParentIsInvalid() {
    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       """);
    assertProblems(readProject(myProjectPom, new NullProjectLocator()), "Self-inheritance found");
  }

  public void testInvalidProfilesXml() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    createProfilesXml("<profiles");

    assertProblems(readProject(myProjectPom, new NullProjectLocator()), "'profiles.xml' has syntax errors");
  }

  public void testInvalidSettingsXml() throws Exception {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    updateSettingsXml("<settings");

    assertProblems(readProject(myProjectPom, new NullProjectLocator()), "'settings.xml' has syntax errors");
  }

  public void testInvalidXmlWithNotClosedTag() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1<name>foo</name>
                       """);

    MavenProjectReaderResult readResult = readProject(myProjectPom, new NullProjectLocator());
    assertProblems(readResult, "'pom.xml' has syntax errors");
    MavenModel p = readResult.mavenModel;

    assertEquals("test", p.getMavenId().getGroupId());
    assertEquals("project", p.getMavenId().getArtifactId());
    assertEquals("Unknown", p.getMavenId().getVersion());
    assertEquals("foo", p.getName());
  }

  // These tests fail until issue https://youtrack.jetbrains.com/issue/IDEA-272809 is fixed
  public void testInvalidXmlWithWrongClosingTag() {
    //waiting for IDEA-272809
    Assume.assumeTrue(false);
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</vers>
                       <name>foo</name>
                       """);

    MavenProjectReaderResult readResult = readProject(myProjectPom, new NullProjectLocator());
    assertProblems(readResult, "'pom.xml' has syntax errors");
    MavenModel p = readResult.mavenModel;

    assertEquals("test", p.getMavenId().getGroupId());
    assertEquals("project", p.getMavenId().getArtifactId());
    assertEquals("1", p.getMavenId().getVersion());
    assertEquals("foo", p.getName());
  }

  public void testEmpty() {
    createProjectPom("");

    MavenModel p = readProject(myProjectPom);

    assertEquals("Unknown", p.getMavenId().getGroupId());
    assertEquals("Unknown", p.getMavenId().getArtifactId());
    assertEquals("Unknown", p.getMavenId().getVersion());
  }

  public void testSpaces() {
    createProjectPom("<name>foo bar</name>");

    MavenModel p = readProject(myProjectPom);
    assertEquals("foo bar", p.getName());
  }

  public void testNewLines() {
    createProjectPom("""
                       <groupId>
                         group
                       </groupId>
                       <artifactId>
                         artifact
                       </artifactId>
                       <version>
                         1
                       </version>
                       """);

    MavenModel p = readProject(myProjectPom);
    assertEquals(new MavenId("group", "artifact", "1"), p.getMavenId());
  }

  public void testCommentsWithNewLinesInTags() {
    createProjectPom("""
                       <groupId>test<!--a-->
                       </groupId><artifactId>
                       <!--a-->project</artifactId><version>1
                       <!--a--></version><name>
                       <!--a-->
                       </name>""");

    MavenModel p = readProject(myProjectPom);
    MavenId id = p.getMavenId();

    assertEquals("test", id.getGroupId());
    assertEquals("project", id.getArtifactId());
    assertEquals("1", id.getVersion());
    assertNull(p.getName());
  }

  public void testTextInContainerTag() {
    createProjectPom("foo <name>name</name> bar");

    MavenModel p = readProject(myProjectPom);
    assertEquals("name", p.getName());
  }

  public void testDefaults() throws IOException {
    VirtualFile file = WriteAction.compute(() -> {
      VirtualFile res = myProjectRoot.createChildData(this, "pom.xml");
      VfsUtil.saveText(res, """
        <project>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </project>
        """);
      return res;
    });
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
                   false, null, Collections.emptyList(), Collections.emptyList());
    assertEquals(1, p.getBuild().getTestResources().size());
    assertResource(p.getBuild().getTestResources().get(0), pathFromBasedir("src/test/resources"),
                   false, null, Collections.emptyList(), Collections.emptyList());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target"), p.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target/classes"), p.getBuild().getOutputDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("target/test-classes"), p.getBuild().getTestOutputDirectory());
  }

  public void testDefaultsForParent() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         dummy</parent>
                       """);

    MavenModel p = readProject(myProjectPom);

    assertParent(p, "Unknown", "Unknown", "Unknown");
  }

  public void testTakingCoordinatesFromParent() {
    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                       </parent>
                       """);

    MavenId id = readProject(myProjectPom).getMavenId();

    assertEquals("test", id.getGroupId());
    assertEquals("Unknown", id.getArtifactId());
    assertEquals("1", id.getVersion());
  }

  public void testCustomSettings() throws IOException {
    VirtualFile file = WriteAction.compute(() -> {
      VirtualFile res = myProjectRoot.createChildData(this, "pom.xml");
      VfsUtil.saveText(res, """
        <project>
          <modelVersion>1.2.3</modelVersion>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
          <name>foo</name>
          <packaging>pom</packaging>
          <parent>
            <groupId>testParent</groupId>
            <artifactId>projectParent</artifactId>
            <version>2</version>
            <relativePath>../parent/pom.xml</relativePath>
          </parent>
          <build>
            <finalName>xxx</finalName>
            <defaultGoal>someGoal</defaultGoal>
            <sourceDirectory>mySrc</sourceDirectory>
            <testSourceDirectory>myTestSrc</testSourceDirectory>
            <scriptSourceDirectory>myScriptSrc</scriptSourceDirectory>
            <resources>
              <resource>
                <directory>myRes</directory>
                <filtering>true</filtering>
                <targetPath>dir</targetPath>
                <includes><include>**.properties</include></includes>
                <excludes><exclude>**.xml</exclude></excludes>
              </resource>
            </resources>
            <testResources>
              <testResource>
                <directory>myTestRes</directory>
                <includes><include>**.properties</include></includes>
              </testResource>
            </testResources>
            <directory>myOutput</directory>
            <outputDirectory>myClasses</outputDirectory>
            <testOutputDirectory>myTestClasses</testOutputDirectory>
          </build>
        </project>
        """);
      return res;
    });
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
                   false, null, Collections.singletonList("**.properties"), Collections.emptyList());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myOutput"), p.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myClasses"), p.getBuild().getOutputDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("myTestClasses"), p.getBuild().getTestOutputDirectory());
  }

  public void testOutputPathsAreBasedOnTargetPath() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <directory>my-target</directory>
                       </build>
                       """);

    MavenModel p = readProject(myProjectPom);

    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target"), p.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/classes"), p.getBuild().getOutputDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("my-target/test-classes"), p.getBuild().getTestOutputDirectory());
  }

  public void testDoesNotIncludeResourcesWithoutDirectory() {
    createProjectPom("""
                       <build>
                         <resources>
                           <resource>
                             <directory></directory>
                           </resource>
                           <resource>
                             <directory>myRes</directory>
                           </resource>
                         </resources>
                         <testResources>
                           <testResource>
                             <filtering>true</filtering>
                           </testResource>
                           <testResource>
                             <directory>myTestRes</directory>
                           </testResource>
                         </testResources>
                       </build>
                       """);

    MavenModel p = readProject(myProjectPom);

    assertEquals(1, p.getBuild().getResources().size());
    assertResource(p.getBuild().getResources().get(0), pathFromBasedir("myRes"),
                   false, null, Collections.emptyList(), Collections.emptyList());

    assertEquals(1, p.getBuild().getTestResources().size());
    assertResource(p.getBuild().getTestResources().get(0), pathFromBasedir("myTestRes"),
                   false, null, Collections.emptyList(), Collections.emptyList());
  }

  public void testRepairResourcesWithoutDirectory() {
    createProjectPom("""
                    <build>
                       <resources>
                         <resource>
                         </resource>
                       </resources>
                       <testResources>
                         <testResource>
                         </testResource>
                       </testResources>
                    </build>
                    """);

    MavenModel p = readProject(myProjectPom);

    assertEquals(1, p.getBuild().getResources().size());
    assertResource(p.getBuild().getResources().get(0), pathFromBasedir("src/main/resources"),
                   false, null, Collections.emptyList(), Collections.emptyList());

    assertEquals(1, p.getBuild().getTestResources().size());
    assertResource(p.getBuild().getTestResources().get(0), pathFromBasedir("src/test/resources"),
                   false, null, Collections.emptyList(), Collections.emptyList());
  }

  public void testRepairResourcesWithEmptyDirectory() {
    createProjectPom("""
                       <build>
                         <resources>
                           <resource>
                             <directory></directory>
                           </resource>
                         </resources>
                         <testResources>
                           <testResource>
                             <directory></directory>
                           </testResource>
                         </testResources>
                       </build>
                       """);

    MavenModel p = readProject(myProjectPom);

    assertEquals(1, p.getBuild().getResources().size());
    assertResource(p.getBuild().getResources().get(0), pathFromBasedir("src/main/resources"),
                   false, null, Collections.emptyList(), Collections.emptyList());

    assertEquals(1, p.getBuild().getTestResources().size());
    assertResource(p.getBuild().getTestResources().get(0), pathFromBasedir("src/test/resources"),
                   false, null, Collections.emptyList(), Collections.emptyList());
  }

  public void testPathsWithProperties() {
    createProjectPom("""
                       <properties>
                         <foo>subDir</foo>
                         <emptyProperty />
                       </properties>
                       <build>
                         <sourceDirectory>${foo}/mySrc</sourceDirectory>
                         <testSourceDirectory>${foo}/myTestSrc</testSourceDirectory>
                         <scriptSourceDirectory>${foo}/myScriptSrc</scriptSourceDirectory>
                         <resources>
                           <resource>
                             <directory>${foo}/myRes</directory>
                           </resource>
                           <resource>
                             <directory>aaa/${emptyProperty}/${unexistingProperty}</directory>
                           </resource>
                         </resources>
                         <testResources>
                           <testResource>
                             <directory>${foo}/myTestRes</directory>
                           </testResource>
                         </testResources>
                         <directory>${foo}/myOutput</directory>
                         <outputDirectory>${foo}/myClasses</outputDirectory>
                         <testOutputDirectory>${foo}/myTestClasses</testOutputDirectory>
                       </build>
                       """);

    MavenModel p = readProject(myProjectPom);

    assertSize(1, p.getBuild().getSources());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/mySrc"), p.getBuild().getSources().get(0));
    assertSize(1, p.getBuild().getTestSources());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myTestSrc"), p.getBuild().getTestSources().get(0));
    assertEquals(2, p.getBuild().getResources().size());
    assertResource(p.getBuild().getResources().get(0), pathFromBasedir("subDir/myRes"),
                   false, null, Collections.emptyList(), Collections.emptyList());
    assertResource(p.getBuild().getResources().get(1), pathFromBasedir("aaa/${unexistingProperty}"),
                   false, null, Collections.emptyList(), Collections.emptyList());
    assertEquals(1, p.getBuild().getTestResources().size());
    assertResource(p.getBuild().getTestResources().get(0), pathFromBasedir("subDir/myTestRes"),
                   false, null, Collections.emptyList(), Collections.emptyList());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myOutput"), p.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myClasses"), p.getBuild().getOutputDirectory());
    PlatformTestUtil.assertPathsEqual(pathFromBasedir("subDir/myTestClasses"), p.getBuild().getTestOutputDirectory());
  }

  public void testExpandingProperties() {
    createProjectPom("""
                       <properties>
                         <prop1>value1</prop1>
                         <prop2>value2</prop2>
                       </properties>
                       <name>${prop1}</name>
                       <packaging>${prop2}</packaging>
                       """);
    MavenModel p = readProject(myProjectPom);

    assertEquals("value1", p.getName());
    assertEquals("value2", p.getPackaging());
  }

  public void testExpandingPropertiesRecursively() {
    createProjectPom("""
                       <properties>
                         <prop1>value1</prop1>
                         <prop2>${prop1}2</prop2>
                       </properties>
                       <name>${prop1}</name>
                       <packaging>${prop2}</packaging>
                       """);
    MavenModel p = readProject(myProjectPom);

    assertEquals("value1", p.getName());
    assertEquals("value12", p.getPackaging());
  }

  public void testHandlingRecursiveProperties() {
    createProjectPom("""
                       <properties>
                         <prop1>${prop2}</prop1>
                         <prop2>${prop1}</prop2>
                       </properties>
                       <name>${prop1}</name>
                       <packaging>${prop2}</packaging>
                       """);
    MavenModel p = readProject(myProjectPom);

    assertEquals("${prop1}", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testHandlingRecursionProprielyAndDoNotForgetCoClearRecursionGuard() throws Exception {
    File repositoryPath = new File(myDir, "repository");
    setRepositoryPath(repositoryPath.getPath());

    File parentFile = new File(repositoryPath, "test/parent/1/parent-1.pom");
    parentFile.getParentFile().mkdirs();
    FileUtil.writeToFile(parentFile, createPomXml("""
                                                    <groupId>test</groupId>
                                                    <artifactId>parent</artifactId>
                                                    <version>1</version>""").getBytes(StandardCharsets.UTF_8));

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>not-a-project</artifactId>
                       <version>1</version>
                       <parent>
                        <groupId>test</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                       </parent>
                       """);

    VirtualFile child = createModulePom("child",
                                        """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <parent>
                                           <groupId>test</groupId>
                                           <artifactId>parent</artifactId>
                                           <version>1</version>
                                          </parent>
                                          """);

    MavenProjectReaderResult readResult = readProject(child, new NullProjectLocator());
    assertProblems(readResult);
  }

  public void testDoNotGoIntoRecursionWhenTryingToResolveParentInDefaultPath() {
    VirtualFile child = createModulePom("child",
                                        """
                                          <groupId>test</groupId>
                                          <artifactId>child</artifactId>
                                          <version>1</version>
                                          <parent>
                                            <groupId>test</groupId>
                                            <artifactId>parent</artifactId>
                                            <version>1</version>
                                          </parent>
                                          """);

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>subChild</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>child</artifactId>
                         <version>1</version>
                         <relativePath>child/pom.xml</relativePath>
                       </parent>
                       """);

    MavenProjectReaderResult readResult = readProject(child, new NullProjectLocator());
    assertProblems(readResult);
  }

  public void testExpandingSystemAndEnvProperties() {
    createProjectPom("<name>${java.home}</name>\n" +
                     "<packaging>${env." + getEnvVar() + "}</packaging>\n");

    MavenModel p = readProject(myProjectPom);
    assertEquals(System.getProperty("java.home"), p.getName());
    assertEquals(System.getenv(getEnvVar()), p.getPackaging());
  }

  public void testExpandingPropertiesFromProfiles() {
    createProjectPom("""
                       <name>${prop1}</name>
                       <packaging>${prop2}</packaging>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                           <properties>
                             <prop1>value1</prop1>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <prop2>value2</prop2>
                           </properties>
                         </profile>
                       </profiles>
                       """);

    MavenModel p = readProject(myProjectPom);
    assertEquals("value1", p.getName());
    assertEquals("${prop2}", p.getPackaging());
  }

  public void testExpandingPropertiesFromManuallyActivatedProfiles() {
    createProjectPom("""
                       <name>${prop1}</name>
                       <packaging>${prop2}</packaging>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                           <properties>
                             <prop1>value1</prop1>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <prop2>value2</prop2>
                           </properties>
                         </profile>
                       </profiles>
                       """);

    MavenModel p = readProject(myProjectPom, "two");
    assertEquals("${prop1}", p.getName());
    assertEquals("value2", p.getPackaging());
  }

  public void testExpandingPropertiesFromParent() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${prop}</name>
                                           """);

    MavenModel p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testDoNotExpandPropertiesFromParentWithWrongCoordinates() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>invalid</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${prop}</name>
                                           """);

    MavenModel p = readProject(module);
    assertEquals("${prop}", p.getName());
  }

  public void testExpandingPropertiesFromParentNotInVfs() throws Exception {
    FileUtil.writeToFile(new File(myProjectRoot.getPath(), "pom.xml"),
                         createPomXml("""
                                        <groupId>test</groupId>
                                        <artifactId>parent</artifactId>
                                        <version>1</version>
                                        <properties>
                                          <prop>value</prop>
                                        </properties>""").getBytes(StandardCharsets.UTF_8));

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${prop}</name>
                                           """);

    MavenModel p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromIndirectParent() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>value</prop>
                       </properties>
                       """);

    createModulePom("module",
                    """
                      <groupId>test</groupId>
                      <artifactId>module</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                      </parent>
                      """);

    VirtualFile subModule = createModulePom("module/subModule",
                                            """
                                              <parent>
                                                <groupId>test</groupId>
                                                <artifactId>module</artifactId>
                                                <version>1</version>
                                              </parent>
                                              <name>${prop}</name>
                                              """);

    MavenModel p = readProject(subModule);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInSpecifiedLocation() {
    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <properties>
                        <prop>value</prop>
                      </properties>
                      """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                             <relativePath>../parent/pom.xml</relativePath>
                                           </parent>
                                           <name>${prop}</name>
                                           """);

    MavenModel p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInSpecifiedLocationWithoutFile() {
    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      <properties>
                        <prop>value</prop>
                      </properties>
                      """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                             <relativePath>../parent</relativePath>
                                           </parent>
                                           <name>${prop}</name>
                                           """);

    MavenModel p = readProject(module);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInRepository() throws Exception {
    File repositoryPath = new File(myDir, "repository");
    setRepositoryPath(repositoryPath.getPath());

    File parentFile = new File(repositoryPath, "org/test/parent/1/parent-1.pom");
    parentFile.getParentFile().mkdirs();
    FileUtil.writeToFile(parentFile,
                         createPomXml("""
                                        <groupId>org.test</groupId>
                                        <artifactId>parent</artifactId>
                                        <version>1</version>
                                        <properties>
                                          <prop>value</prop>
                                        </properties>""").getBytes(StandardCharsets.UTF_8));

    createProjectPom("""
                       <parent>
                         <groupId>org.test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       <name>${prop}</name>
                       """);

    MavenModel p = readProject(myProjectPom);
    assertEquals("value", p.getName());
  }

  public void testExpandingPropertiesFromParentInInvalidLocation() {
    final VirtualFile parent = createModulePom("parent",
                                               """
                                                 <groupId>test</groupId>
                                                 <artifactId>parent</artifactId>
                                                 <version>1</version>
                                                 <properties>
                                                   <prop>value</prop>
                                                 </properties>
                                                 """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <name>${prop}</name>
                                           """);

    MavenModel p = readProject(module, new MavenProjectReaderProjectLocator() {
      @Override
      public VirtualFile findProjectFile(MavenId coordinates) {
        return new MavenId("test", "parent", "1").equals(coordinates) ? parent : null;
      }
    }).mavenModel;
    assertEquals("value", p.getName());
  }

  public void testPropertiesFromParentInParentSection() {
    createProjectPom("""
                       <groupId>${groupProp}</groupId>
                       <artifactId>parent</artifactId>
                       <version>${versionProp}</version>
                       <properties>
                         <groupProp>test</groupProp>
                         <versionProp>1</versionProp>
                       </properties>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>${groupProp}</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>${versionProp}</version>
                                           </parent>
                                           <artifactId>module</artifactId>
                                           """);

    MavenId id = readProject(module).getMavenId();
    assertEquals("test:module:1", id.getGroupId() + ":" + id.getArtifactId() + ":" + id.getVersion());
  }

  public void testInheritingSettingsFromParentAndAlignCorrectly() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <build>
                         <directory>custom</directory>
                       </build>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """);

    MavenModel p = readProject(module);
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.getParent(), "custom"), p.getBuild().getDirectory());
  }

  public void testExpandingPropertiesAfterInheritingSettingsFromParent() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <properties>
                         <prop>subDir</prop>
                       </properties>
                       <build>
                         <directory>${basedir}/${prop}/custom</directory>
                       </build>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """);

    MavenModel p = readProject(module);
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.getParent(), "subDir/custom"), p.getBuild().getDirectory());
  }

  public void testExpandingPropertiesAfterInheritingSettingsFromParentProfiles() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <prop>subDir</prop>
                           </properties>
                           <build>
                             <directory>${basedir}/${prop}/custom</directory>
                           </build>
                         </profile>
                       </profiles>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """);

    MavenModel p = readProject(module, "one");
    PlatformTestUtil.assertPathsEqual(pathFromBasedir(module.getParent(), "subDir/custom"), p.getBuild().getDirectory());
  }

  public void testPropertiesFromProfilesXmlOldStyle() {
    createProjectPom("<name>${prop}</name>");
    createProfilesXmlOldStyle("""
                                <profile>
                                  <id>one</id>
                                  <properties>
                                    <prop>foo</prop>
                                  </properties>
                                </profile>
                                """);

    MavenModel mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testPropertiesFromProfilesXmlNewStyle() {
    createProjectPom("<name>${prop}</name>");
    createProfilesXml("""
                        <profile>
                          <id>one</id>
                          <properties>
                            <prop>foo</prop>
                          </properties>
                        </profile>
                        """);

    MavenModel mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testPropertiesFromSettingsXml() throws Exception {
    createProjectPom("<name>${prop}</name>");
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <properties>
                              <prop>foo</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """);

    MavenModel mavenProject = readProject(myProjectPom);
    assertEquals("${prop}", mavenProject.getName());

    mavenProject = readProject(myProjectPom, "one");
    assertEquals("foo", mavenProject.getName());
  }

  public void testDoNoInheritParentFinalNameIfUnspecified() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>2</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """);

    MavenModel p = readProject(module, "one");
    assertEquals("module-2", p.getBuild().getFinalName());
  }

  public void testDoInheritingParentFinalNameIfSpecified() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <build>
                         <finalName>xxx</finalName>
                       </build>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>2</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           """);

    MavenModel p = readProject(module, "one");
    assertEquals("xxx", p.getBuild().getFinalName());
  }


  public void testInheritingParentProfiles() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>profileFromParent</id>
                         </profile>
                       </profiles>
                       """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <profiles>
                                             <profile>
                                               <id>profileFromChild</id>
                                             </profile>
                                           </profiles>
                                           """);

    MavenModel p = readProject(module);
    assertOrderedElementsAreEqual(ContainerUtil.map(p.getProfiles(), (Function<MavenProfile, Object>)profile -> profile.getId()),
                                  "profileFromChild", "profileFromParent");
  }

  public void testCorrectlyCollectProfilesFromDifferentSources() throws Exception {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>profile</id>
                           <modules><module>parent</module></modules>
                         </profile>
                       </profiles>
                       """);

    final VirtualFile parentProfiles = createProfilesXml("""
                                                           <profile>
                                                             <id>profile</id>
                                                             <modules><module>parentProfiles</module></modules>
                                                           </profile>
                                                           """);

    VirtualFile module = createModulePom("module",
                                         """
                                           <groupId>test</groupId>
                                           <artifactId>module</artifactId>
                                           <version>1</version>
                                           <parent>
                                             <groupId>test</groupId>
                                             <artifactId>parent</artifactId>
                                             <version>1</version>
                                           </parent>
                                           <profiles>
                                             <profile>
                                               <id>profile</id>
                                               <modules><module>pom</module></modules>
                                             </profile>
                                           </profiles>
                                           """);

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>profile</id>
                            <modules><module>settings</module></modules>
                          </profile>
                        </profiles>
                        """);

    final VirtualFile profiles = createProfilesXml("module",
                                                   """
                                                     <profile>
                                                       <id>profile</id>
                                                       <modules><module>profiles</module></modules>
                                                     </profile>
                                                     """);

    MavenModel p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEquals("pom", p.getProfiles().get(0).getModules().get(0));
    assertEquals("pom", p.getProfiles().get(0).getSource());

    createModulePom("module",
                    """
                      <groupId>test</groupId>
                      <artifactId>module</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                      </parent>
                      """);

    p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEquals("profiles", p.getProfiles().get(0).getModules().get(0));
    assertEquals("profiles.xml", p.getProfiles().get(0).getSource());

    WriteCommandAction.writeCommandAction(myProject).run(() -> profiles.delete(this));


    p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEmpty("parent", p.getProfiles().get(0).getModules());
    assertEquals("pom", p.getProfiles().get(0).getSource());

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       """);

    p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEmpty("parentProfiles", p.getProfiles().get(0).getModules());
    assertEquals("profiles.xml", p.getProfiles().get(0).getSource());

    WriteCommandAction.writeCommandAction(myProject).run(() -> parentProfiles.delete(null));


    p = readProject(module);
    assertEquals(1, p.getProfiles().size());
    assertEmpty("settings", p.getProfiles().get(0).getModules());
    assertEquals("settings.xml", p.getProfiles().get(0).getSource());
  }

  public void testModulesAreNotInheritedFromParentsProfiles() {
    VirtualFile p = createProjectPom("""
                                       <groupId>test</groupId>
                                       <artifactId>project</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <profiles>
                                        <profile>
                                         <id>one</id>
                                          <modules>
                                           <module>m</module>
                                          </modules>
                                        </profile>
                                       </profiles>
                                       """);

    VirtualFile m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
       <groupId>test</groupId>
       <artifactId>project</artifactId>
       <version>1</version>
      </parent>
      """);

    assertSize(1, readProject(p, "one").getModules());
    assertSize(0, readProject(m, "one").getModules());
  }

  public void testActivatingProfilesByDefault() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <activeByDefault>false</activeByDefault>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesAfterResolvingInheritance() {
    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      """);

    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>parent/pom.xml</relativePath>
                       </parent>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByOS() {
    String os = SystemInfo.isWindows ? "windows" : SystemInfo.isMac ? "mac" : "unix";

    createProjectPom("<profiles>\n" +
                     "  <profile>\n" +
                     "    <id>one</id>\n" +
                     "    <activation>\n" +
                     "      <os><family>\n" + os + "</family></os>\n" +
                     "    </activation>\n" +
                     "  </profile>\n" +
                     "  <profile>\n" +
                     "    <id>two</id>\n" +
                     "    <activation>\n" +
                     "      <os><family>xxx</family></os>\n" +
                     "    </activation>\n" +
                     "  </profile>\n" +
                     "</profiles>\n");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByJdk() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <jdk>(,1.5)</jdk>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByStrictJdkVersion() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <jdk>1.4</jdk>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles();
  }

  public void testActivatingProfilesByProperty() {
    createProjectPom("<profiles>\n" +
                     "  <profile>\n" +
                     "    <id>one</id>\n" +
                     "    <activation>\n" +
                     "      <property>\n" +
                     "        <name>os.name</name>\n" +
                     "        <value>\n" + System.getProperty("os.name") + "</value>\n" +
                     "      </property>\n" +
                     "    </activation>\n" +
                     "  </profile>\n" +
                     "  <profile>\n" +
                     "    <id>two</id>\n" +
                     "    <activation>\n" +
                     "      <property>\n" +
                     "        <name>os.name</name>\n" +
                     "        <value>xxx</value>\n" +
                     "      </property>\n" +
                     "    </activation>\n" +
                     "  </profile>\n" +
                     "</profiles>\n");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByEnvProperty() {
    String value = System.getenv(getEnvVar());

    createProjectPom("<profiles>\n" +
                     "  <profile>\n" +
                     "    <id>one</id>\n" +
                     "    <activation>\n" +
                     "      <property>\n" +
                     "        <name>env." + getEnvVar() + "</name>\n" +
                     "        <value>\n" + value + "</value>\n" +
                     "      </property>\n" +
                     "    </activation>\n" +
                     "  </profile>\n" +
                     "  <profile>\n" +
                     "    <id>two</id>\n" +
                     "    <activation>\n" +
                     "      <property>\n" +
                     "        <name>ffffff</name>\n" +
                     "        <value>ffffff</value>\n" +
                     "      </property>\n" +
                     "    </activation>\n" +
                     "  </profile>\n" +
                     "</profiles>\n");

    assertActiveProfiles("one");
  }

  public void testActivatingProfilesByFile() throws Exception {
    createProjectSubFile("dir/file.txt");

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <file>
                               <exists>${basedir}/dir/file.txt</exists>
                             </file>
                           </activation>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <file>
                               <missing>${basedir}/dir/file.txt</missing>
                             </file>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("one");
  }

  public void testActivateDefaultProfileEventIfThereAreExplicitOnesButAbsent() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                         <profile>
                           <id>explicit</id>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles(Arrays.asList("foofoofoo"), "default");
  }

  public void testDoNotActivateDefaultProfileIfThereAreActivatedImplicit() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                         <profile>
                           <id>implicit</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("implicit");
  }

  public void testActivatingImplicitProfilesEventWhenThereAreExplicitOnes() {
    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>explicit</id>
                         </profile>
                         <profile>
                           <id>implicit</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles(Arrays.asList("explicit"), "explicit", "implicit");
  }

  public void testAlwaysActivatingActiveProfilesInSettingsXml() throws Exception {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """);

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>explicit</id>
                         </profile>
                         <profile>
                           <id>settings</id>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("settings");
    assertActiveProfiles(Arrays.asList("explicit"), "explicit", "settings");
  }

  public void testAlwaysActivatingActiveProfilesInProfilesXml() {
    createFullProfilesXml("""
                            <?xml version="1.0"?>
                            <profilesXml>
                              <activeProfiles>
                                <activeProfile>profiles</activeProfile>
                              </activeProfiles>
                            </profilesXml>
                            """);

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>explicit</id>
                         </profile>
                         <profile>
                           <id>profiles</id>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("profiles");
    assertActiveProfiles(Arrays.asList("explicit"), "explicit", "profiles");
  }

  public void testActivatingBothActiveProfilesInSettingsXmlAndImplicitProfiles() throws Exception {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """);

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>implicit</id>
                           <activation>
                             <jdk>[1.5,)</jdk>
                           </activation>
                         </profile>
                         <profile>
                           <id>settings</id>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("settings", "implicit");
  }

  public void testDoNotActivateDefaultProfilesWhenThereAreAlwaysOnProfilesInPomXml() throws Exception {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """);

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                         <profile>
                           <id>settings</id>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("settings");
  }

  public void testActivateDefaultProfilesWhenThereAreActiveProfilesInSettingsXml() throws Exception {
    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>settings</id>
                          </profile>
                        </profiles>
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """);

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("default", "settings");
  }

  public void testActivateDefaultProfilesWhenThereAreActiveProfilesInProfilesXml() {
    createFullProfilesXml("""
                            <?xml version="1.0"?>
                            <profilesXml>
                              <profiles>
                                <profile>
                                  <id>profiles</id>
                                </profile>
                              </profiles>
                              <activeProfiles>
                                <activeProfile>profiles</activeProfile>
                              </activeProfiles>
                            </profilesXml>
                            """);

    createProjectPom("""
                       <profiles>
                         <profile>
                           <id>default</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("default", "profiles");
  }

  public void testActiveProfilesInSettingsXmlOrProfilesXmlThroughInheritance() throws Exception {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>settings</activeProfile>
                        </activeProfiles>
                        """);

    createFullProfilesXml("parent",
                          """
                            <?xml version="1.0"?>
                            <profilesXml>
                              <activeProfiles>
                                <activeProfile>parent</activeProfile>
                              </activeProfiles>
                            </profilesXml>
                            """);

    createModulePom("parent",
                    """
                      <groupId>test</groupId>
                      <artifactId>parent</artifactId>
                      <version>1</version>
                      """);

    createFullProfilesXml("""
                            <?xml version="1.0"?>
                            <profilesXml>
                              <activeProfiles>
                                <activeProfile>project</activeProfile>
                              </activeProfiles>
                            </profilesXml>
                            """);


    createProjectPom("""
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>parent/pom.xml</relativePath>
                       </parent>
                       <profiles>
                         <profile>
                           <id>project</id>
                         </profile>
                         <profile>
                           <id>parent</id>
                         </profile>
                         <profile>
                           <id>settings</id>
                         </profile>
                       </profiles>
                       """);

    assertActiveProfiles("project", "settings");
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

  private void assertActiveProfiles(String... expected) {
    assertActiveProfiles(Collections.emptyList(), expected);
  }

  private void assertActiveProfiles(List<String> explicitProfiles, String... expected) {
    MavenProjectReaderResult result =
      readProject(myProjectPom, new NullProjectLocator(), ArrayUtilRt.toStringArray(explicitProfiles));
    assertUnorderedElementsAreEqual(result.activatedProfiles.getEnabledProfiles(), expected);
  }

}
