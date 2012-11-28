/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.openapi.module.Module;
import org.jetbrains.idea.maven.MavenTestCase;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *         Date: 11/6/12
 */
public class MavenImportWizardTest extends ProjectWizardTestCase {

  public void testImportModule() throws Exception {
    File pom = createPom();
    Module module = importModuleFrom(new MavenProjectImportProvider(new MavenProjectBuilder()), pom.getPath());
    assertEquals("project", module.getName());
  }

  public void testImportProject() throws Exception {
    File pom = createPom();
    Module module = importProjectFrom(pom.getPath(), null, new MavenProjectImportProvider(new MavenProjectBuilder()));
    assertEquals("project", module.getName());
  }

  private File createPom() throws IOException {
    return createTempFile("pom.xml", MavenTestCase.createPomXml("<groupId>test</groupId>" +
                                                                "<artifactId>project</artifactId>" +
                                                                "<version>1</version>"));
  }
}
