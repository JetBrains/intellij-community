package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.testFramework.PsiTestUtil;

public class NonMavenProjectInspectionsTest extends MavenCompletionAndResolutionTestCase {
  public void testDisablingInpaectionForNonMavenProjects() throws Throwable {
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
  }

  public void testEnabligInspectionForNonMavenProjectsAfterImport() throws Throwable {
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
