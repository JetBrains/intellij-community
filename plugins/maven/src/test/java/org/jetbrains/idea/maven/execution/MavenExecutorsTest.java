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
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.idea.maven.MavenTestCase;

import java.io.File;
import java.util.Arrays;

public class MavenExecutorsTest extends MavenTestCase {
  public void testExternalExecutor() throws Exception {
    if (!hasMavenInstallation()) return;

    VfsUtil.saveText(createProjectSubFile("src/main/java/A.java"), "public class A {}");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    assertFalse(new File(getProjectPath(), "target").exists());

    MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), null);
    MavenRunnerSettings settings = new MavenRunnerSettings();

    MavenExecutor e;
    settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);
    e = new MavenExternalExecutor(params, getMavenGeneralSettings(), settings, NULL_MAVEN_CONSOLE);

    assertTrue(e.execute(new EmptyProgressIndicator()));

    assertTrue(new File(getProjectPath(), "target").exists());
  }
}
