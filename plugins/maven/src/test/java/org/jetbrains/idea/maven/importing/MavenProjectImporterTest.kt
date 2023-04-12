// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class MavenProjectImporterTest extends DependenciesSubstitutionTest {
  @Test
  public void testMavenImportModulesProperlyNamed() {
    var previewModule = MavenImportUtil.createPreviewModule(myProject, myProjectRoot);

    myProjectsManager.addManagedFilesWithProfiles(List.of(myProjectRoot), new MavenExplicitProfiles(Collections.emptyList(), Collections.emptyList()), previewModule);

    var parentFile = createProjectPom("""
                <groupId>group</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
                <packaging>pom</packaging>
                <modules>
                  <module>project</module>
                </modules>
                """);
    var projectFile = createModulePom("project", """
                <artifactId>project</artifactId>
                <version>1</version>
                <parent>
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                </parent>
                """);
    importProject();

    var moduleManager = ModuleManager.getInstance(myProject);

    var modules = moduleManager.getModules();
    assertEquals(2, modules.length);

    var parentModule = moduleManager.findModuleByName("parent");
    assertNotNull(parentModule);

    var projectModule = moduleManager.findModuleByName("project");
    assertNotNull(projectModule);
  }

}
