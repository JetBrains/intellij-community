// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import org.junit.Test;

public class MavenDependencySpecialVersionsCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);
  }

  //@Test 
  //public void testResolvingDependenciesWithVersionRanges() throws Throwable {
  //  createProjectPom("<groupId>test</groupId>\n" +
  //                   "<artifactId>project</artifactId>\n" +
  //                   "<version>1</version>\n" +
  //
  //                   "<dependencies>\n" +
  //                   "  <dependency>\n" +
  //                   "    <groupId>junit</groupId>\n" +
  //                   "    <artifactId><cursor>junit</artifactId>\n" +
  //                   "    <version>[4,5]</version>\n" +
  //                   "  </dependency>\n" +
  //                   "</dependencies>");
  //
  //  String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");
  //  VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath);
  //  assertResolved(myProjectPom, findPsiFile(f));
  //}

  @Test
  public void testDoNotHighlightVersionRanges() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>jmock</groupId>
                           <artifactId>jmock</artifactId>
                           <version>[1,2]</version>
                         </dependency>
                       </dependencies>
                       """);

    checkHighlighting();
  }

  @Test 
  public void testDoNotHighlightLatestAndReleaseDependencies() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>jmock</groupId>
                           <artifactId>jmock</artifactId>
                           <version>LATEST</version>
                         </dependency>
                         <dependency>
                           <groupId>jmock</groupId>
                           <artifactId>jmock</artifactId>
                           <version>RELEASE</version>
                         </dependency>
                       </dependencies>
                       """);

    checkHighlighting();
  }
}
