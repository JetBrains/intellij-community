// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenImportWizardTest extends ProjectWizardTestCase<AbstractProjectWizard> {
  @Override
  public void tearDown() throws Exception {
    try {
      MavenServerManager.getInstance().shutdown(true);
      JavaAwareProjectJdkTableImpl.removeInternalJdkInTests();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testImportModule() throws Exception {
    Path pom = createPom();
    Module module = importModuleFrom(new MavenProjectImportProvider(), pom.toString());
    assertEquals("project", module.getName());
  }

  public void testImportProject() throws Exception {
    Path pom = createPom();
    Module module = importProjectFrom(pom.toString(), null, new MavenProjectImportProvider());
    assertThat(module.getName()).isEqualTo("project");
  }

  private @NotNull Path createPom() throws IOException {
    return createTempFile("pom.xml", MavenTestCase.createPomXml("<groupId>test</groupId>" +
                                                                "<artifactId>project</artifactId>" +
                                                                "<version>1</version>")).toPath();
  }
}
