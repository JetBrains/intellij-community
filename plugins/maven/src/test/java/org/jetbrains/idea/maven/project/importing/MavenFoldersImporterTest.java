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
package org.jetbrains.idea.maven.project.importing;

import com.intellij.ProjectTopics;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.VcsIgnoreManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenFoldersImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapterLegacyImpl;
import org.jetbrains.idea.maven.importing.ModifiableModelsProviderProxyWrapper;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.junit.Test;

import java.io.File;

public class MavenFoldersImporterTest extends MavenMultiVersionImportingTestCase {
  @Test
  public void testUpdatingExternallyCreatedFolders() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myProjectRoot.getChildren(); // make sure fs is cached

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    new File(myProjectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs();
    updateProjectFolders();

    assertExcludes("project", "target");
    assertGeneratedSources("project", "target/generated-sources/xxx");

    assertNull(myProjectRoot.findChild("target"));
  }

  @Test 
  public void testIgnoreTargetFolder() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/classes").mkdirs();
    updateProjectFolders();

    assertExcludes("project", "target");
    myProjectRoot.refresh(false, true);
    VirtualFile target = myProjectRoot.findChild("target");
    assertNotNull(target);
    if (!Registry.is("ide.hide.excluded.files")) {
      assertTrue(VcsIgnoreManager.getInstance(myProject).isPotentiallyIgnoredFile(target));
    }
  }

  @Test 
  public void testUpdatingFoldersForAllTheProjects() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    importProject();

    assertExcludes("m1", "target");
    assertExcludes("m2", "target");

    new File(myProjectRoot.getPath(), "m1/target/foo/z").mkdirs();
    new File(myProjectRoot.getPath(), "m1/target/generated-sources/xxx/z").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/bar").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/generated-sources/yyy/z").mkdirs();

    updateProjectFolders();

    assertExcludes("m1", "target");
    assertGeneratedSources("m1", "target/generated-sources/xxx");

    assertExcludes("m2", "target");
    assertGeneratedSources("m2", "target/generated-sources/yyy");
  }

  @Test 
  public void testDoesNotTouchSourceFolders() {
    createStdProjectFolders();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");

    updateProjectFolders();

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  @Test 
  public void testDoesNotExcludeRegisteredSources() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    final File sourceDir = new File(myProjectRoot.getPath(), "target/src");
    sourceDir.mkdirs();

    ApplicationManager.getApplication().runWriteAction(() -> {
      MavenRootModelAdapter adapter = new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(myProjectsTree.findProject(myProjectPom),
                                                                                                    getModule("project"),
                                                                                                    new ModifiableModelsProviderProxyWrapper(myProject)));
      adapter.addSourceFolder(sourceDir.getPath(), JavaSourceRootType.SOURCE);
      adapter.getRootModel().commit();
    });


    updateProjectFolders();

    assertSources("project", "target/src");
    assertExcludes("project", "target");
  }

  @Test 
  public void testDoesNothingWithNonMavenModules() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createModule("userModule");
    updateProjectFolders(); // shouldn't throw exceptions
  }

  @Test 
  public void testDoNotUpdateOutputFoldersWhenUpdatingExcludedFolders() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    ApplicationManager.getApplication().runWriteAction(() -> {
      MavenRootModelAdapter adapter = new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(myProjectsTree.findProject(myProjectPom),
                                                                getModule("project"),
                                                                new ModifiableModelsProviderProxyWrapper(myProject)));
      adapter.useModuleOutput(new File(myProjectRoot.getPath(), "target/my-classes").getPath(),
                              new File(myProjectRoot.getPath(), "target/my-test-classes").getPath());
      adapter.getRootModel().commit();
    });


    MavenFoldersImporter.updateProjectFolders(myProject, true);

    ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule("project"));
    CompilerModuleExtension compiler = rootManager.getModuleExtension(CompilerModuleExtension.class);
    assertTrue(compiler.getCompilerOutputUrl(), compiler.getCompilerOutputUrl().endsWith("my-classes"));
    assertTrue(compiler.getCompilerOutputUrlForTests(), compiler.getCompilerOutputUrlForTests().endsWith("my-test-classes"));
  }

  @Test 
  public void testDoNotCommitIfFoldersWasNotChanged() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    final int[] count = new int[]{0};
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        count[0]++;
      }
    });

    updateProjectFolders();

    assertEquals(0, count[0]);
  }

  @Test 
  public void testCommitOnlyOnceForAllModules() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    importProject();

    final int[] count = new int[]{0};
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        count[0]++;
      }
    });

    new File(myProjectRoot.getPath(), "target/generated-sources/foo/z").mkdirs();
    new File(m1.getPath(), "target/generated-sources/bar/z").mkdirs();
    new File(m2.getPath(), "target/generated-sources/baz/z").mkdirs();

    updateProjectFolders();

    assertEquals(1, count[0]);
  }

  @Test 
  public void testMarkSourcesAsGeneratedOnReImport() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    new File(myProjectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs();
    updateProjectFolders();

    assertGeneratedSources("project", "target/generated-sources/xxx");

    ModuleRootModificationUtil.updateModel(getModule("project"), model -> {
      for (SourceFolder folder : model.getContentEntries()[0].getSourceFolders()) {
        JavaSourceRootProperties properties = folder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
        assertNotNull(properties);
        properties.setForGeneratedSources(false);
      }
    });
    assertGeneratedSources("project");

    importProject();
    assertGeneratedSources("project", "target/generated-sources/xxx");
  }

  @Test 
  public void testCustomPomFileNameDefaultContentRoots() throws Exception {
    createProjectSubFile("m1/customName.xml", createPomXml(
                  "<artifactId>m1</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>project</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>" +

                  "<build>" +
                  "  <sourceDirectory>sources</sourceDirectory>" +
                  "  <testSourceDirectory>tests</testSourceDirectory>" +
                  "</build>"));

    new File(myProjectRoot.getPath(), "m1/sources").mkdirs();
    new File(myProjectRoot.getPath(), "m1/tests").mkdirs();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m1/customName.xml</module>" +
                  "</modules>");

    assertContentRoots("m1", getProjectPath() + "/m1");
  }

  @Test 
  public void testCustomPomFileNameCustomContentRoots() throws Exception {
    createProjectSubFile("m1/pom.xml", createPomXml(
                  "<artifactId>m1-pom</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>project</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>"));

    createProjectSubFile("m1/custom.xml", createPomXml(
                  "<artifactId>m1-custom</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>project</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>" +

                  "<build>" +
                  "  <resources><resource><directory>sources/resources</directory></resource></resources>" +
                  "  <sourceDirectory>sources</sourceDirectory>" +
                  "  <testSourceDirectory>tests</testSourceDirectory>" +
                  "</build>"));

    new File(myProjectRoot.getPath(), "m1/sources/resources").mkdirs();
    new File(myProjectRoot.getPath(), "m1/tests").mkdirs();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m1</module>" +
                  "  <module>m1/custom.xml</module>" +
                  "</modules>");

    assertModules("project", "m1-pom", "m1-custom");

    assertContentRoots("m1-pom", getProjectPath() + "/m1");
    assertContentRoots("m1-custom", getProjectPath() + "/m1/sources", getProjectPath() + "/m1/tests");
  }

  @Test 
  public void testContentRootOutsideOfModuleDir() throws Exception {
    createProjectSubFile("m1/pom.xml", createPomXml(
      "<artifactId>m1-pom</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>" +

      "<build>" +
      "  <sourceDirectory>../pom-sources</sourceDirectory>" +
      "</build>"));

    createProjectSubFile("m1/custom.xml", createPomXml(
      "<artifactId>m1-custom</artifactId>" +
      "<version>1</version>" +

      "<parent>" +
      "  <groupId>test</groupId>" +
      "  <artifactId>project</artifactId>" +
      "  <version>1</version>" +
      "</parent>" +

      "<build>" +
      "  <sourceDirectory>../custom-sources</sourceDirectory>" +
      "</build>"));

    new File(myProjectRoot.getPath(), "pom-sources").mkdirs();
    new File(myProjectRoot.getPath(), "custom-sources").mkdirs();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m1</module>" +
                  "  <module>m1/custom.xml</module>" +
                  "</modules>");

    assertModules("project", "m1-pom", "m1-custom");

    assertContentRoots("m1-pom", getProjectPath() + "/m1", getProjectPath() + "/pom-sources");
    assertContentRoots("m1-custom", getProjectPath() + "/custom-sources");
  }

  @Test 
  public void testOverrideLanguageLevelFromParentPom() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
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
                     "</build>"
    );

    createModulePom("m1",
      "<artifactId>m1</artifactId>" +
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
      "</build>");

    importProject();

    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule("m1")));
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")));
  }

  @Test 
  public void testReleaseHasPriorityInParentPom() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
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
                     "</build>"
    );

    createModulePom("m1",
      "<artifactId>m1</artifactId>" +
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
      "</build>");

    importProject();

    assertEquals(LanguageLevel.JDK_1_9, LanguageLevelUtil.getCustomLanguageLevel(getModule("m1")));
    assertEquals(LanguageLevel.JDK_1_9.toJavaVersion().toString(),
                 CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")));
  }

  @Test
  public void testReleasePropertyNotSupport() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
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
                     "</build>"
    );

    createModulePom("m1",
      "<artifactId>m1</artifactId>" +
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
      "</build>");

    importProject();

    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule("m1")));
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")));
  }

  @Test
  public void testCompilerPluginExecutionBlockProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
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
                     "</build>"
    );


    importProject();

    assertEquals(LanguageLevel.JDK_11, LanguageLevelUtil.getCustomLanguageLevel(getModule("project")));
    assertEquals(LanguageLevel.JDK_11.toJavaVersion().toString(),
                 CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("project")));
  }

  private void updateProjectFolders() {
    MavenFoldersImporter.updateProjectFolders(myProject, false);
  }
}
