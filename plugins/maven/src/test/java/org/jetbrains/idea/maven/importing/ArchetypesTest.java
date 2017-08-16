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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import gnu.trove.THashMap;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.execution.MavenExecutor;
import org.jetbrains.idea.maven.execution.MavenExternalExecutor;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class ArchetypesTest extends MavenTestCase {
  public void testGenerating() {
    if (!hasMavenInstallation()) return;

    File dir = new File(myDir.getPath(), "generated");
    dir.mkdirs();

    MavenRunnerParameters params = new MavenRunnerParameters(false, dir.getPath(),
                                                             Arrays.asList("org.apache.maven.plugins:maven-archetype-plugin:RELEASE:generate"),
                                                             Collections.emptyList());

    MavenRunnerSettings settings = new MavenRunnerSettings();
    Map<String, String> props = new THashMap<>();
    props.put("archetypeGroupId", "org.apache.maven.archetypes");
    props.put("archetypeArtifactId", "maven-archetype-quickstart");
    props.put("archetypeVersion", "1.0");
    props.put("interactiveMode", "false");
    props.put("groupId", "foo");
    props.put("groupId", "foo");
    props.put("artifactId", "bar");

    settings.setMavenProperties(props);
    MavenExecutor exec;
    settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA);
    exec = new MavenExternalExecutor(myProject, params, getMavenGeneralSettings(), settings, NULL_MAVEN_CONSOLE);
    exec.execute(new EmptyProgressIndicator());

    assertTrue(new File(dir, "bar/pom.xml").exists());
  }
}
