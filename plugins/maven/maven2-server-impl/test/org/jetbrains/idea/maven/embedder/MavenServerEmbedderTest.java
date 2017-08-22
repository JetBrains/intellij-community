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
package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerEmbedder;
import org.jetbrains.idea.maven.server.MavenServerExecutionResult;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.server.embedder.Maven2ServerEmbedderImpl;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;

public class MavenServerEmbedderTest extends MavenImportingTestCase {
  private MavenEmbedderWrapper myEmbedder;
  private Maven2ServerEmbedderImpl myEmbedderImpl;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initEmbedder();
  }

  @Override
  protected void tearDown() throws Exception {
    releaseEmbedder();
    super.tearDown();
  }

  private void initEmbedder() throws RemoteException {
    if (myEmbedder != null) releaseEmbedder();


    myEmbedderImpl = Maven2ServerEmbedderImpl.create(MavenServerManager.convertSettings(getMavenGeneralSettings()));
    myEmbedder = new MavenEmbedderWrapper(null) {
      @NotNull
      @Override
      protected MavenServerEmbedder create() {
        return myEmbedderImpl;
      }
    };
  }

  private void releaseEmbedder() {
    myEmbedder.release();
    myEmbedder = null;
  }

  public void _testSettingLocalRepository() throws Exception {
    assertEquals(getRepositoryFile(), myEmbedderImpl.getLocalRepositoryFile());

    File repo = new File(myDir, "/repo");
    setRepositoryPath(repo.getPath());

    initEmbedder();
    assertEquals(getRepositoryFile(), myEmbedderImpl.getLocalRepositoryFile());
  }

  public void _testReleasingTwice() {
    myEmbedder.release();
    myEmbedder.release();
  }

  public void _testExecutionGoals() throws Exception {
    createProjectSubFile("src/main/java/A.java", "public class A {}");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.execute(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList(), Arrays.asList("compile"));

    assertNotNull(result.projectData);
    assertNotNull(new File(getProjectPath(), "target").exists());
    assertOrderedElementsAreEqual(result.unresolvedArtifacts);

    MavenModel project = result.projectData.mavenModel;
    assertNotNull(project);
    assertEquals("project", project.getMavenId().getArtifactId());
  }

  public void _testResolvingProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.resolveProject(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList());
    assertNotNull(result.projectData);
    assertOrderedElementsAreEqual(result.unresolvedArtifacts);

    MavenModel project = result.projectData.mavenModel;
    assertNotNull(project);
    assertEquals("project", project.getMavenId().getArtifactId());
    assertEquals(1, project.getDependencies().size());
  }

  public void _testResolvingProjectPropertiesInFolders() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.resolveProject(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList());

    MavenModel project = result.projectData.mavenModel;
    assertNotNull(project);
    assertEquals("project", project.getMavenId().getArtifactId());
    PlatformTestUtil.assertPathsEqual(myProjectRoot.getPath() + "/target", project.getBuild().getDirectory());
    PlatformTestUtil.assertPathsEqual(myProjectRoot.getPath() + "/src/main/java", project.getBuild().getSources().get(0));
  }

  public void _testResolvingProjectWithExtensions() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>com.adobe.flex.framework</groupId>" +
                     "    <artifactId>framework</artifactId>" +
                     "    <version>3.2.0.3958</version>" +
                     "    <type>resource-bundle</type>" +
                     "    <classifier>en_US</classifier>" +
                     "  </dependency>" +
                     "</dependencies>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.sonatype.flexmojos</groupId>" +
                     "      <artifactId>flexmojos-maven-plugin</artifactId>" +
                     "      <version>3.5.0</version>" +
                     "      <extensions>true</extensions>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.resolveProject(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList());

    assertNotNull(result.projectData);
    assertOrderedElementsAreEqual(result.unresolvedArtifacts);

    MavenModel p = result.projectData.mavenModel;
    assertEquals(1, p.getDependencies().size());
    assertEquals("rb.swc", p.getDependencies().get(0).getExtension());
  }

  public void _testResolvingProjectWithRegisteredExtensions() throws Exception {
    ComponentDescriptor desc = new ComponentDescriptor();
    desc.setRole(ArtifactHandler.ROLE);
    desc.setRoleHint("foo");
    desc.setImplementation(MyArtifactHandler.class.getName());
    myEmbedderImpl.getContainer().addComponentDescriptor(desc);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>3.8.1</version>" +
                     "    <scope>test</scope>" +
                     "    <type>foo</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.resolveProject(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList());

    assertNotNull(result.projectData);
    assertOrderedElementsAreEqual(result.unresolvedArtifacts);

    MavenModel p = result.projectData.mavenModel;
    assertEquals(1, p.getDependencies().size());
    assertEquals("pom", p.getDependencies().get(0).getExtension());
  }

  public static class MyArtifactHandler implements ArtifactHandler {
    @Override
    public String getExtension() {
      return "pom";
    }

    @Override
    public String getDirectory() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getClassifier() {
      return null;
    }

    @Override
    public String getPackaging() {
      return "foo";
    }

    @Override
    public boolean isIncludesDependencies() {
      return false;
    }

    @Override
    public String getLanguage() {
      return "java";
    }

    @Override
    public boolean isAddedToClasspath() {
      return true;
    }
  }

  public void _testUnresolvedArtifacts() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>fff</groupId>" +
                     "    <artifactId>zzz</artifactId>" +
                     "    <version>666</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.resolveProject(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList());

    assertNotNull(result.projectData);
    assertOrderedElementsAreEqual(result.unresolvedArtifacts, new MavenId("fff", "zzz", "666"));
  }

  public void _testUnresolvedSystemArtifacts() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>fff</groupId>" +
                     "    <artifactId>zzz</artifactId>" +
                     "    <version>666</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>" + myProjectRoot.getPath() + "/foo.jar</systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.resolveProject(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList());

    assertNotNull(result.projectData);
    assertOrderedElementsAreEqual(result.unresolvedArtifacts, new MavenId("fff", "zzz", "666"));
  }

  public void _testDependencyWithUnresolvedParent() throws Exception {
    File repo = new File(myDir, "/repo");
    setRepositoryPath(repo.getPath());

    initEmbedder();

    VirtualFile m = createModulePom("foo-parent",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>foo-parent</artifactId>" +
                                    "<version>1</version>" +
                                    "<packaging>pom</packaging>");
    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    myEmbedder.execute(m, Collections.<String>emptyList(), Collections.<String>emptyList(), Arrays.asList("install"));
    myEmbedder.reset();
    File fooParentFile = new File(repo, "test/foo-parent/1/foo-parent-1.pom");
    assertTrue(fooParentFile.exists());

    m = createModulePom("foo",
                        "<artifactId>foo</artifactId>" +
                        "<version>1</version>" +

                        "<parent>" +
                        "  <groupId>test</groupId>" +
                        "  <artifactId>foo-parent</artifactId>" +
                        "  <version>1</version>" +
                        "</parent>");
    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    myEmbedder.execute(m, Collections.<String>emptyList(), Collections.<String>emptyList(), Arrays.asList("install"));
    myEmbedder.reset();
    assertTrue(new File(repo, "test/foo/1/foo-1.pom").exists());

    FileUtil.delete(fooParentFile);
    initEmbedder(); // reset all caches

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>test</groupId>" +
                     "    <artifactId>foo</artifactId>" +
                     "    <version>1</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.resolveProject(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList());

    assertNotNull(result.projectData);
    assertOrderedElementsAreEqual(result.unresolvedArtifacts, new MavenId("test", "foo-parent", "1"));
  }

  public void _testUnresolvedSystemArtifactsWithoutPath() throws Exception {
    if (ignore()) return; // need to repair model before resolving
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>fff</groupId>" +
                     "    <artifactId>zzz</artifactId>" +
                     "    <version>666</version>" +
                     "    <scope>system</scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    myEmbedder.customizeForResolve(new SoutMavenConsole(), EMPTY_MAVEN_PROCESS);
    MavenServerExecutionResult result =
      myEmbedder.resolveProject(myProjectPom, Collections.<String>emptyList(), Collections.<String>emptyList());

    assertNotNull(result);
    assertOrderedElementsAreEqual(result.unresolvedArtifacts, new MavenId("fff", "zzz", "666"));
  }
}
