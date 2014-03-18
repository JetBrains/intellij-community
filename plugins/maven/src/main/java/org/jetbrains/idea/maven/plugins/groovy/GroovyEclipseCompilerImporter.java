/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.groovy;

import org.jetbrains.idea.maven.importing.GroovyImporter;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;

/**
 * @author Sergey Evdokimov
 */
public class GroovyEclipseCompilerImporter extends GroovyImporter {

  public GroovyEclipseCompilerImporter() {
    super("org.apache.maven.plugins", "maven-compiler-plugin");
  }

  @Override
   public boolean isApplicable(MavenProject mavenProject) {
    MavenPlugin compilerPlugin = mavenProject.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
    if (compilerPlugin == null) return false;

    for(MavenId id : compilerPlugin.getDependencies()) {
      if ("groovy-eclipse-compiler".equals(id.getArtifactId()) && "org.codehaus.groovy".equals(id.getGroupId())) {
        return true;
      }
    }

    return false;
   }
}
