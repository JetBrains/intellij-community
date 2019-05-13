/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

public class MavenDependencySpecialVersionsCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  //public void testResolvingDependenciesWithVersionRanges() throws Throwable {
  //  createProjectPom("<groupId>test</groupId>" +
  //                   "<artifactId>project</artifactId>" +
  //                   "<version>1</version>" +
  //
  //                   "<dependencies>" +
  //                   "  <dependency>" +
  //                   "    <groupId>junit</groupId>" +
  //                   "    <artifactId><cursor>junit</artifactId>" +
  //                   "    <version>[4,5]</version>" +
  //                   "  </dependency>" +
  //                   "</dependencies>");
  //
  //  String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");
  //  VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath);
  //  assertResolved(myProjectPom, findPsiFile(f));
  //}

  public void testDoNotHighlightVersionRanges() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>jmock</groupId>" +
                     "    <artifactId>jmock</artifactId>" +
                     "    <version>[1,2]</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testDoNotHighlightLatestAndReleaseDependencies() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>jmock</groupId>" +
                     "    <artifactId>jmock</artifactId>" +
                     "    <version>LATEST</version>" +
                     "  </dependency>" +
                     "  <dependency>" +
                     "    <groupId>jmock</groupId>" +
                     "    <artifactId>jmock</artifactId>" +
                     "    <version>RELEASE</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }
}
