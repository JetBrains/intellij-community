// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom;

import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.dom.inspections.MavenParentMissedVersionInspection;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

public class MavenAutomaticVersioningResolutionTest extends MavenDomTestCase {

  @Test
  public void testAutomaticParentVersionResolutionForMaven4() throws IOException {

    assumeVersionAtLeast("4.0.0-alpha-2");
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m</module>
                       </modules>
                       """);

    VirtualFile m = createModulePom("m",
                                    """
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                      </parent>
                                       <artifactId>m</artifactId>
                                      """);
    importProject();
    assertEquals("1.1", myProjectsManager.findProject(m).getMavenId().getVersion());

    createModulePom("m",
                    """
                      <parent>
                        <groupId>test</groupId>
                        <artifactId><caret>project</artifactId>
                      </parent>
                       <artifactId>m</artifactId>
                      """);
    assertResolved(m, findPsiFile(myProjectPom));
    myFixture.enableInspections(Collections.singletonList(MavenParentMissedVersionInspection.class));
    checkHighlighting(m);
  }

  @Test
  public void testAutomaticParentVersionResolutionIsNotEnabledForMaven3() throws IOException {

    assumeVersionLessThan("4.0.0-alpha-2");
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m</module>
                       </modules>
                       """);

    VirtualFile m = createModulePom("m",
                                    """
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                      </parent>
                                       <artifactId>m</artifactId>
                                      """);
    importProject();

    createModulePom("m",
                    """
                      <<error descr="'version' child tag should be defined">parent</error>>
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                        </parent>
                         <artifactId>m</artifactId>
                        """);
    myFixture.enableInspections(Collections.singletonList(MavenParentMissedVersionInspection.class));
    checkHighlighting(m);
  }

  @Test
  public void testAutomaticDependencyVersionResolutionForMaven4() throws IOException {

    assumeVersionAtLeast("4.0.0-alpha-2");
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1.1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m1</module>
                        <module>m2</module>
                       </modules>
                       """);

    VirtualFile m1 = createModulePom("m1",
                                     """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                       </parent>
                                        <artifactId>m1</artifactId>
                                       """);
    VirtualFile m2 = createModulePom("m2",
                                     """
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                       </parent>
                                        <artifactId>m2</artifactId>
                                        <dependencies>
                                          <dependency>
                                            <groupId>test</groupId>
                                            <artifactId>m1</artifactId>
                                          </dependency>
                                        </dependencies>
                                       """);
    importProject();
    assertEquals("1.1", myProjectsManager.findProject(m1).getMavenId().getVersion());
    assertEquals("1.1", myProjectsManager.findProject(m2).getMavenId().getVersion());
    assertModuleModuleDeps("m2", "m1");

    createModulePom("m2",
                    """
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                      </parent>
                       <artifactId>m2</artifactId>
                       <dependencies>
                         <dependency>
                           <groupId><caret>test</groupId>
                           <artifactId>m1</artifactId>
                         </dependency>
                       </dependencies>
                      """);
    assertResolved(m2, findPsiFile(m1));
    checkHighlighting(m2);
  }
}
