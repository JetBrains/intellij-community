/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/29/2016
 */
public class MavenGroovyPomClassFinder extends NonClasspathClassFinder {
  public MavenGroovyPomClassFinder(Project project) {
    super(project, JavaFileType.DEFAULT_EXTENSION, GroovyFileType.DEFAULT_EXTENSION);
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    return MavenGroovyPomScriptType.additionalScopeFiles();
  }
}
