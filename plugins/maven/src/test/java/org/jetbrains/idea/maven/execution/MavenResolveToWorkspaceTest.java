/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.artifactResolver.common.MavenModuleMap;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenResolveToWorkspaceTest extends MavenImportingTestCase {

  public void testIgnoredProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<modules>" +
                     "  <module>moduleA</module>" +
                     "  <module>moduleIgnored</module>" +
                     "  <module>moduleB</module>" +
                     "</modules>");

    VirtualFile moduleA = createModulePom("moduleA", "<groupId>test</groupId>" +
                                                     "<artifactId>moduleA</artifactId>" +
                                                     "<version>1</version>");

    VirtualFile moduleIgnored = createModulePom("moduleIgnored", "<groupId>test</groupId>" +
                                                                 "<artifactId>moduleIgnored</artifactId>" +
                                                                 "<version>1</version>");

    VirtualFile moduleB = createModulePom("moduleB", "<groupId>test</groupId>" +
                                                     "<artifactId>moduleB</artifactId>" +
                                                     "<version>1</version>" +
                                                     "<dependencies>" +
                                                     "  <dependency>" +
                                                     "    <groupId>test</groupId>" +
                                                     "    <artifactId>moduleA</artifactId>" +
                                                     "    <version>1</version>" +
                                                     "  </dependency>" +
                                                     "  <dependency>" +
                                                     "    <groupId>test</groupId>" +
                                                     "    <artifactId>moduleIgnored</artifactId>" +
                                                     "    <version>1</version>" +
                                                     "  </dependency>" +
                                                     "</dependencies>"
    );

    MavenProjectsManager.getInstance(myProject).setIgnoredFilesPaths(Collections.singletonList(moduleIgnored.getPath()));

    importProject();

    MavenProjectsManager.getInstance(myProject).setIgnoredFilesPaths(Collections.singletonList(moduleIgnored.getPath()));

    //assertModules("project", "moduleA", "moduleB");

    WriteAction.run(() -> ProjectRootManager.getInstance(myProject).setProjectSdk(createJdk()));

    MavenRunnerParameters runnerParameters = new MavenRunnerParameters(moduleB.getParent().getPath(), null, false, Collections.singletonList("jetty:run"), Collections.emptyMap());
    runnerParameters.setResolveToWorkspace(true);

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(myProject).getSettings().clone();
    runnerSettings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);

    JavaParameters parameters = MavenExternalParameters.createJavaParameters(myProject,
                                                                             runnerParameters,
                                                                             MavenProjectsManager.getInstance(myProject).getGeneralSettings(),
                                                                             runnerSettings);

    String resolveMapFile = null;

    String prefix = "-D" + MavenModuleMap.PATHS_FILE_PROPERTY + "=";

    for (String param : parameters.getVMParametersList().getParameters()) {
      if (param.startsWith(prefix)) {
        resolveMapFile = param.substring(prefix.length());
        break;
      }
    }

    assertNotNull(resolveMapFile);

    Properties properties = readProperties(resolveMapFile);

    assertEquals(moduleA.getPath(), properties.getProperty("test:moduleA:pom:1"));
    assert properties.getProperty("test:moduleA:jar:1").endsWith("/moduleA/target/classes");

    assertNull(properties.getProperty("test:moduleIgnored:pom:1"));
    assertNull(properties.getProperty("test:moduleIgnored:jar:1"));
  }

  private static Properties readProperties(String filePath) throws IOException {
    InputStream is = new BufferedInputStream(new FileInputStream(filePath));
    try {
      Properties properties = new Properties();
      properties.load(is);

      for (Map.Entry<Object, Object> entry : properties.entrySet()) {
        String value = (String)entry.getValue();
        entry.setValue(value.replace('\\', '/'));
      }

      return properties;
    }
    finally {
      is.close();
    }
  }

}
