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
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class MavenNotManagedProjectInspectionsTest extends MavenDomTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    setRepositoryPath(new File(myDir, "repo").getPath());
  }

  public void testWorkForNonMavenProjects() throws Throwable {
    Module m = createModule("module");
    PsiTestUtil.addContentRoot(m, myProjectRoot);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module><error>m1</error></module>" +
                     "  <module><error>m2</error></module>" +
                     "</modules>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><error>xxx</error></groupId>" +
                     "    <artifactId><error>yyy</error></artifactId>" +
                     "    <version><error>zzz</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting(); // should not fail nor highlight errors
  }

  public void testEnablingInspectionForNonMavenProjectsAfterImport() throws Throwable {
    if (ignore()) return;
    // can not reproduce in tests because of StartupManager.runWhenProjectIsInitialized
    // relies on ProjectManager.isProjectOpen. In tests the project is never being opened.
    
    ProjectManagerEx.getInstanceEx().openProject(myProject);
    
    Module m = createModule("module");
    PsiTestUtil.addContentRoot(m, myProjectRoot);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>yyy</artifactId>" +
                     "    <version>zzz</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting(); // should not fail nor highlight errors
    
    importProject();

    checkHighlighting(); // should not fail nor highlight errors
  }
}
