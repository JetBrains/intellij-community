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
package org.jetbrains.idea.maven.importing.configurers;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

/**
 * @author Sergey Evdokimov
 */
public class MavenEncodingConfigurer extends MavenModuleConfigurer {
  @Override
  public void configure(@NotNull MavenProject mavenProject, @NotNull Project project, @Nullable Module module) {
    String encoding = mavenProject.getEncoding();
    if (encoding != null) {
      try {
        EncodingProjectManager.getInstance(project).setEncoding(mavenProject.getDirectoryFile(), Charset.forName(encoding));
      }
      catch (UnsupportedCharsetException ignored) {/**/}
      catch (IllegalCharsetNameException ignored) {/**/}
    }
  }
}
