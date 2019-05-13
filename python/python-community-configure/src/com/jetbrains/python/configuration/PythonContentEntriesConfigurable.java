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
package com.jetbrains.python.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.PlatformContentEntriesConfigurable;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

/**
 * @author yole
 */
public class PythonContentEntriesConfigurable extends ModuleAwareProjectConfigurable implements NonDefaultProjectConfigurable {
  public PythonContentEntriesConfigurable(final Project project) {
    super(project, "Project Structure", "reference.settingsdialog.project.structure");
  }

  @NotNull
  @Override
  protected Configurable createModuleConfigurable(Module module) {
    if (PlatformUtils.isPyCharmCommunity())
      return new PlatformContentEntriesConfigurable(module, JavaSourceRootType.SOURCE);
    return new PyContentEntriesModuleConfigurable(module);
  }
}
