// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.RunAll;
import com.intellij.util.ArrayUtil;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.wizards.AbstractMavenModuleBuilder;
import org.jetbrains.idea.maven.wizards.MavenJavaModuleBuilder;
import org.junit.Assume;
import org.junit.Test;

public class MavenModuleBuilderSameFolderAsParentTest extends MavenMultiVersionImportingTestCase {
  private AbstractMavenModuleBuilder myBuilder;

  @Override
  protected void tearDown() throws Exception {
    RunAll.runAll(
      () -> stopMavenImportManager(),
      () -> super.tearDown()
    );
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuilder = new MavenJavaModuleBuilder();

    createJdk();
    setModuleNameAndRoot("module", getProjectPath());
  }

  private void setModuleNameAndRoot(String name, String root) {
    myBuilder.setName(name);
    myBuilder.setModuleFilePath(root + "/" + name + ".iml");
    myBuilder.setContentEntryPath(root);
  }

  private void setParentProject(VirtualFile pom) {
    myBuilder.setParentProject(myProjectsManager.findProject(pom));
  }

  private void createNewModule(MavenId id) throws Exception {
    myBuilder.setProjectId(id);

    WriteAction.runAndWait(() -> {
      ModifiableModuleModel model = ModuleManager.getInstance(myProject).getModifiableModel();
      myBuilder.createModule(model);
      model.commit();
    });

    updateAllProjects();
  }

  @Test
  public void testSameFolderAsParent() throws Exception {
    configConfirmationForYesAnswer();

    Assume.assumeFalse(Registry.is("maven.linear.import"));

    VirtualFile customPomXml = createProjectSubFile("custompom.xml", createPomXml(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        """));
    importProject(customPomXml);
    assertModules("project");

    setModuleNameAndRoot("module", getProjectPath());
    setParentProject(customPomXml);

    createNewModule(new MavenId("org.foo", "module", "1.0"));

    if (supportsImportOfNonExistingFolders()) {
      var contentRoots = ArrayUtil.mergeArrays(allDefaultResources(),
                                               "src/main/java",
                                               "src/test/java");
      assertRelativeContentRoots("project", contentRoots);
    }
    else {
      assertRelativeContentRoots("project",
                                 "src/main/java",
                                 "src/main/resources",
                                 "src/test/java"
      );
    }
    assertRelativeContentRoots("module", "");

    MavenProject module = MavenProjectsManager.getInstance(myProject).findProject(getModule("module"));

    MavenDomProjectModel domProjectModel = MavenDomUtil.getMavenDomProjectModel(myProject, module.getFile());
    assertEquals("custompom.xml", domProjectModel.getMavenParent().getRelativePath().getRawText());
  }
}