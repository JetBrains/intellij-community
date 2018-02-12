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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ArtifactsDownloadingTest extends ArtifactsDownloadingTestCase {
  public void testJavadocsAndSources() {
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

  public void testIgnoringOfflineSetting() {
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

    getMavenGeneralSettings().setWorkOffline(false);
    myProjectsManager.getEmbeddersManager().reset(); // to recognize change
    downloadArtifacts();

    assertTrue(sources.exists());
    assertTrue(javadoc.exists());

    FileUtil.delete(sources);
    FileUtil.delete(javadoc);

    getMavenGeneralSettings().setWorkOffline(true);
    myProjectsManager.getEmbeddersManager().reset(); // to recognize change

    downloadArtifacts();

    assertTrue(sources.exists());
    assertTrue(javadoc.exists());
  }

  public void testDownloadingSpecificDependency() {
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

  public void testReturningNotFoundArtifacts() {
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

  public void testJavadocsAndSourcesForTestDeps() {
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

  public void testJavadocsAndSourcesForDepsWithClassifiersAndType() throws Exception {
    String remoteRepo = FileUtil.toSystemIndependentName(myDir.getPath() + "/repo");
    updateSettingsXmlFully("<settings>" +
                           "<mirrors>" +
                           "  <mirror>" +
                           "    <id>central</id>" +
                           "    <url>" + VfsUtilCore.pathToUrl(remoteRepo) + "</url>" +
                           "    <mirrorOf>*</mirrorOf>" +
                           "  </mirror>" +
                           "</mirrors>" +
                           "</settings>");

    createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-sources.jar");
    createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-javadoc.jar");

    createDummyArtifact(remoteRepo, "/xxx/yyy/1/yyy-1-test-sources.jar");
    createDummyArtifact(remoteRepo, "/xxx/yyy/1/yyy-1-test-javadoc.jar");

    createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-foo-sources.jar");
    createDummyArtifact(remoteRepo, "/xxx/xxx/1/xxx-1-foo-javadoc.jar");

    createDummyArtifact(remoteRepo, "/xxx/zzz/1/zzz-1-test-foo-sources.jar");
    createDummyArtifact(remoteRepo, "/xxx/zzz/1/zzz-1-test-foo-javadoc.jar");


    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>xxx</groupId>" +
                  "    <artifactId>xxx</artifactId>" +
                  "    <version>1</version>" +
                  "    <classifier>foo</classifier>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>xxx</groupId>" +
                  "    <artifactId>yyy</artifactId>" +
                  "    <version>1</version>" +
                  "    <type>test-jar</type>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>xxx</groupId>" +
                  "    <artifactId>zzz</artifactId>" +
                  "    <version>1</version>" +
                  "    <classifier>foo</classifier>" +
                  "    <type>test-jar</type>" +
                  "  </dependency>" +
                  "</dependencies>");

    List<File> files1 = Arrays.asList(new File(getRepositoryPath(), "/xxx/xxx/1/xxx-1-sources.jar"),
                                      new File(getRepositoryPath(), "/xxx/xxx/1/xxx-1-javadoc.jar"),
                                      new File(getRepositoryPath(), "/xxx/yyy/1/yyy-1-test-sources.jar"),
                                      new File(getRepositoryPath(), "/xxx/yyy/1/yyy-1-test-javadoc.jar"));

    List<File> files2 = Arrays.asList(new File(getRepositoryPath(), "/xxx/xxx/1/xxx-1-foo-sources.jar"),
                                      new File(getRepositoryPath(), "/xxx/xxx/1/xxx-1-foo-javadoc.jar"),
                                      new File(getRepositoryPath(), "/xxx/zzz/1/zzz-1-test-foo-sources.jar"),
                                      new File(getRepositoryPath(), "/xxx/zzz/1/zzz-1-test-foo-javadoc.jar"));

    for (File each : files1) {
      assertFalse(each.toString(), each.exists());
    }
    for (File each : files2) {
      assertFalse(each.toString(), each.exists());
    }
    downloadArtifacts();

    for (File each : files1) {
      assertTrue(each.toString(), each.exists());
    }
    for (File each : files2) {
      assertFalse(each.toString(), each.exists());
    }
  }

  public void testDownloadingPlugins() {
    try {
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
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testDownloadBuildExtensionsOnResolve() {
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
