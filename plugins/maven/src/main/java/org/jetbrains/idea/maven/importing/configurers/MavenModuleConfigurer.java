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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenModuleConfigurer {

  private static volatile List<MavenModuleConfigurer> ourConfigurersList;

  public abstract void configure(@NotNull MavenProject mavenProject, @NotNull Project project, @Nullable Module module);

  public static List<MavenModuleConfigurer> getConfigurers() {
    List<MavenModuleConfigurer> configurers = ourConfigurersList;
    if (configurers == null) {
      configurers = new ArrayList<>();

      for (MavenModuleConfigurer configurer : new MavenModuleConfigurer[]{
        new MavenCompilerConfigurer(),
        new MavenEncodingConfigurer(),
        new MavenAnnotationProcessorConfigurer(),

        new MavenIdeaPluginConfigurer()
      }) {

        if (!Boolean.parseBoolean(System.getProperty("idea.maven.disable." + configurer.getClass().getSimpleName()))) {
          configurers.add(configurer);
        }
      }

      ourConfigurersList = configurers;
    }

    return configurers;
  }

}
