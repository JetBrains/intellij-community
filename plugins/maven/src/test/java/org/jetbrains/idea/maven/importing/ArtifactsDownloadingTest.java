/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;
import java.util.Arrays;

public class ArtifactsDownloadingTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "plugins", "local1");
    helper.copy("plugins", "local1");
    setRepositoryPath(helper.getTestDataPath("local1"));
  }

  public void testJavadocsAndSources() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    File sources = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-sources.jar");
    File javadoc = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-javadoc.jar");

    assertFalse(sources.exists());
    assertFalse(javadoc.exists());

    downloadArtifacts();

    assertTrue(sources.exists());
    assertTrue(javadoc.exists());
  }

  public void testDownloadingSpecificDependency() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>jmock</groupId>" +
                  "    <artifactId>jmock</artifactId>" +
                  "    <version>1.2.0</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    File sources = new File(getRepositoryPath(), "/jmock/jmock/1.2.0/jmock-1.2.0-sources.jar");
    File javadoc = new File(getRepositoryPath(), "/jmock/jmock/1.2.0/jmock-1.2.0-javadoc.jar");
    assertFalse(sources.exists());
    assertFalse(javadoc.exists());

    MavenProject project = myProjectsTree.getRootProjects().get(0);
    MavenArtifact dep = project.getDependencies().get(0);
    downloadArtifacts(Arrays.asList(project), Arrays.asList(dep));

    assertTrue(sources.exists());
    assertTrue(javadoc.exists());
    assertFalse(new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-sources.jar").exists());
    assertFalse(new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-javadoc.jar").exists());
  }

  public void testReturningNotFoundArtifacts() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>lib</groupId>" +
                  "    <artifactId>xxx</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    MavenProject project = myProjectsTree.getRootProjects().get(0);
    MavenArtifactDownloader.DownloadResult unresolvedArtifacts = downloadArtifacts(Arrays.asList(project), null);
    assertUnorderedElementsAreEqual(unresolvedArtifacts.resolvedSources, new MavenId("junit", "junit", "4.0"));
    assertUnorderedElementsAreEqual(unresolvedArtifacts.resolvedDocs, new MavenId("junit", "junit", "4.0"));
    assertUnorderedElementsAreEqual(unresolvedArtifacts.unresolvedSources, new MavenId("lib", "xxx", "1"));
    assertUnorderedElementsAreEqual(unresolvedArtifacts.unresolvedDocs, new MavenId("lib", "xxx", "1"));
  }

  public void testJavadocsAndSourcesForTestDeps() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <scope>test</scope>" +
                  "  </dependency>" +
                  "</dependencies>");

    File sources = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-sources.jar");
    File javadoc = new File(getRepositoryPath(), "/junit/junit/4.0/junit-4.0-javadoc.jar");

    assertFalse(sources.exists());
    assertFalse(javadoc.exists());

    downloadArtifacts();

    assertTrue(sources.exists());
    assertTrue(javadoc.exists());
  }

  public void testCustomDocsAndSources() throws Exception {
    String remoteRepo = FileUtil.toSystemIndependentName(myDir.getPath() + "/repo");
    updateSettingsXmlFully("<settings>" +
                           "<mirrors>" +
                           "  <mirror>" +
                           "    <id>Nexus</id>" +
                           "    <url>" + VfsUtil.pathToUrl(remoteRepo) + "</url>" +
                           "    <mirrorOf>*</mirrorOf>" +
                           "  </mirror>" +
                           "</mirrors>" +
                           "</settings>");

    FileUtil.writeToFile(new File(remoteRepo, "/xxx/yyy/1/yyy-1-sources.jar"), "111".getBytes());
    FileUtil.writeToFile(new File(remoteRepo, "/xxx/yyy/1/yyy-1-sources.jar.sha1"),
                         "6216f8a75fd5bb3d5f22b6f9958cdede3fc086c2  xxx/yyy/1/yyy-1-sources.jar".getBytes());

    FileUtil.writeToFile(new File(remoteRepo, "/xxx/yyy/1/yyy-1-asdoc.zip"), "111".getBytes());
    FileUtil.writeToFile(new File(remoteRepo, "/xxx/yyy/1/yyy-1-asdoc.zip.sha1"),
                         "6216f8a75fd5bb3d5f22b6f9958cdede3fc086c2  xxx/yyy/1/yyy-1-asdoc.zip".getBytes());

    FileUtil.writeToFile(new File(remoteRepo, "/xxx/yyy/1/yyy-1-javadoc.jar"), "111".getBytes());
    FileUtil.writeToFile(new File(remoteRepo, "/xxx/yyy/1/yyy-1-javadoc.jar.sha1"),
                         "6216f8a75fd5bb3d5f22b6f9958cdede3fc086c2  xxx/yyy/1/yyy-1-javadoc.jar".getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>swf</packaging>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>xxx</groupId>" +
                  "    <artifactId>yyy</artifactId>" +
                  "    <version>1</version>" +
                  "    <type>swc</type>" +
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

    File sources = new File(getRepositoryPath(), "/xxx/yyy/1/yyy-1-sources.jar");
    File asdoc = new File(getRepositoryPath(), "/xxx/yyy/1/yyy-1-asdoc.zip");
    File javadoc = new File(getRepositoryPath(), "/xxx/yyy/1/yyy-1-javadoc.jar");

    assertFalse(sources.exists());
    assertFalse(asdoc.exists());
    assertFalse(javadoc.exists());

    downloadArtifacts();

    assertTrue(sources.exists());
    assertTrue(asdoc.exists());
    assertFalse(javadoc.exists());
  }

  public void testDownloadingPlugins() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-surefire-plugin</artifactId>" +
                  "      <version>2.4.2</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    File f = new File(getRepositoryPath(), "/org/apache/maven/plugins/maven-surefire-plugin/2.4.2/maven-surefire-plugin-2.4.2.jar");
    assertFalse(f.exists());

    resolvePlugins();

    assertTrue(f.exists());
  }

  public void testDownloadBuildExtensionsOnResolve() throws Exception {
    File f = new File(getRepositoryPath(), "/org/apache/maven/wagon/wagon/1.0-alpha-6/wagon-1.0-alpha-6.pom");
    assertFalse(f.exists());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <extensions>" +
                  "    <extension>" +
                  "      <groupId>org.apache.maven.wagon</groupId>" +
                  "      <artifactId>wagon</artifactId>" +
                  "      <version>1.0-alpha-6</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    assertTrue(f.exists());
  }
}
