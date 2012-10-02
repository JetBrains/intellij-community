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
package org.jetbrains.jps.maven.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;

/**
 * @author nik
 */
public class JpsMavenDependenciesEnumerationHandler extends JpsJavaDependenciesEnumerationHandler {
  private static final JpsMavenDependenciesEnumerationHandler INSTANCE = new JpsMavenDependenciesEnumerationHandler();

  @Override
  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return true;
  }

  @Override
  public boolean isProductionOnTestsDependency(JpsDependencyElement element) {
    return JpsMavenExtensionService.getInstance().isProductionOnTestDependency(element);
  }

  @Override
  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return false;
  }

  @Override
  public boolean shouldProcessDependenciesRecursively() {
    return false;
  }

  public static class MavenFactory extends Factory {
    @Nullable
    @Override
    public JpsJavaDependenciesEnumerationHandler createHandler(@NotNull Collection<JpsModule> modules) {
      JpsMavenExtensionService service = JpsMavenExtensionService.getInstance();
      for (JpsModule module : modules) {
        if (service.getExtension(module) != null) {
          return INSTANCE;
        }
      }
      return null;
    }
  }
}
