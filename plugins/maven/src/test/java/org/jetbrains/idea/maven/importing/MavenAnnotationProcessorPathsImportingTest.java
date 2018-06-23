// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.dom.MavenDomTestCase;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.List;

import static java.util.Collections.singletonList;

/**
 * @author ibessonov
 */
public class MavenAnnotationProcessorPathsImportingTest extends MavenDomTestCase {

  public void testExternalDependencyPath() {
    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +

                  "<build>\n" +
                  "  <plugins>\n" +
                  "    <plugin>\n" +
                  "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                  "      <configuration>\n" +
                  "        <annotationProcessorPaths>\n" +
                  "          <path>\n" +
                  "            <groupId>com.google.dagger</groupId>\n" +
                  "            <artifactId>dagger-compiler</artifactId>\n" +
                  "            <version>2.2</version>\n" +
                  "          </path>\n" +
                  "        </annotationProcessorPaths>\n" +
                  "      </configuration>\n" +
                  "    </plugin>\n" +
                  "  </plugins>\n" +
                  "</build>");

    MavenProject mavenProject = myProjectsManager.findProject(getModule("project"));
    assertNotNull(mavenProject);

    List<MavenArtifact> annotationProcessors = mavenProject.getExternalAnnotationProcessors();
    assertNotEmpty(annotationProcessors);

    assertTrue(annotationProcessors.stream().anyMatch(a ->
      "com.google.dagger".equals(a.getGroupId()) && "dagger-compiler".equals(a.getArtifactId()) && "2.2".equals(a.getVersion())
    ));
    assertTrue(annotationProcessors.stream().anyMatch(a ->
      "com.google.dagger".equals(a.getGroupId()) && "dagger".equals(a.getArtifactId()) && "2.2".equals(a.getVersion())
    ));

    String path = mavenProject.getAnnotationProcessorPath(myProject);
    assertTrue(path.contains(FileUtil.toSystemDependentName("/com/google/dagger/dagger-compiler/2.2/dagger-compiler-2.2.jar")));
  }

  public void testExternalDependencyAnnotationPath() {
    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +

                  "<build>\n" +
                  "  <plugins>\n" +
                  "    <plugin>\n" +
                  "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                  "      <configuration>\n" +
                  "        <annotationProcessorPaths>\n" +
                  "          <annotationProcessorPath>\n" +
                  "            <groupId>com.google.dagger</groupId>\n" +
                  "            <artifactId>dagger-compiler</artifactId>\n" +
                  "            <version>2.2</version>\n" +
                  "          </annotationProcessorPath>\n" +
                  "        </annotationProcessorPaths>\n" +
                  "      </configuration>\n" +
                  "    </plugin>\n" +
                  "  </plugins>\n" +
                  "</build>");

    MavenProject mavenProject = myProjectsManager.findProject(getModule("project"));
    assertNotNull(mavenProject);

    List<MavenArtifact> annotationProcessors = mavenProject.getExternalAnnotationProcessors();
    assertNotEmpty(annotationProcessors);

    assertTrue(annotationProcessors.stream().anyMatch(a ->
      "com.google.dagger".equals(a.getGroupId()) && "dagger-compiler".equals(a.getArtifactId()) && "2.2".equals(a.getVersion())
    ));
    assertTrue(annotationProcessors.stream().anyMatch(a ->
      "com.google.dagger".equals(a.getGroupId()) && "dagger".equals(a.getArtifactId()) && "2.2".equals(a.getVersion())
    ));

    String path = mavenProject.getAnnotationProcessorPath(myProject);
    assertTrue(path.contains(FileUtil.toSystemDependentName("/com/google/dagger/dagger-compiler/2.2/dagger-compiler-2.2.jar")));
  }

  public void testLocalDependency() {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +
                     "<packaging>pom</packaging>\n" +

                     "<modules>\n" +
                     "  <module>m1</module>\n" +
                     "  <module>m2</module>\n" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>\n" +
                    "<artifactId>m1</artifactId>\n" +
                    "<version>1</version>\n" +

                    "<dependencies>\n" +
                    "  <dependency>\n" +
                    "    <groupId>com.google.guava</groupId>\n" +
                    "    <artifactId>guava</artifactId>\n" +
                    "    <version>19.0</version>\n" +
                    "  </dependency>\n" +
                    "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>\n" +
                    "<artifactId>m2</artifactId>\n" +
                    "<version>1</version>\n" +

                    "<build>\n" +
                    "  <plugins>\n" +
                    "    <plugin>\n" +
                    "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                    "      <configuration>\n" +
                    "        <annotationProcessorPaths>\n" +
                    "          <path>\n" +
                    "            <groupId>test</groupId>\n" +
                    "            <artifactId>m1</artifactId>\n" +
                    "            <version>1</version>\n" +
                    "          </path>\n" +
                    "        </annotationProcessorPaths>\n" +
                    "      </configuration>\n" +
                    "    </plugin>\n" +
                    "  </plugins>\n" +
                    "</build>");

    importProject();

    Module module = getModule("m2");
    assertNotNull(module);

    MavenProject mavenProject = myProjectsManager.findProject(module);
    assertNotNull(mavenProject);

    List<MavenArtifact> annotationProcessors = mavenProject.getExternalAnnotationProcessors();
    assertEmpty(annotationProcessors);

    MavenAnnotationProcessorsModuleService apService = MavenAnnotationProcessorsModuleService.getInstance(module);
    assertEquals(singletonList("m1"), apService.getAnnotationProcessorModules());

    String path = mavenProject.getAnnotationProcessorPath(myProject);
    assertTrue(path.contains(FileUtil.toSystemDependentName("/m1/target/classes")));
    assertTrue(path.contains(FileUtil.toSystemDependentName("/com/google/guava/guava/19.0/guava-19.0.jar")));
  }
}
