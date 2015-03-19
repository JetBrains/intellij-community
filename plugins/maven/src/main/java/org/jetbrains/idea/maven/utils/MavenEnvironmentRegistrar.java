/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

public class MavenEnvironmentRegistrar extends ApplicationComponent.Adapter {
  private static final String MAVEN_REPOSITORY = "MAVEN_REPOSITORY";

  @NotNull
  @Override
  public String getComponentName() {
    return MavenEnvironmentRegistrar.class.getName();
  }

  @Override
  public void initComponent() {
    registerPathVariable();
  }

  private static void registerPathVariable() {
    String repository = MavenUtil.resolveLocalRepository(null, null, null).getAbsolutePath();
    PathMacros macros = PathMacros.getInstance();

    for (String each : macros.getAllMacroNames()) {
      String path = macros.getValue(each);
      if (path != null && FileUtil.pathsEqual(repository, path)) {
        return;
      }
    }

    macros.setMacro(MAVEN_REPOSITORY, repository);
  }
}
