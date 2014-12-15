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
package com.jetbrains.python;

import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author yole
 */
public class PlatformPythonModuleType extends PythonModuleTypeBase<EmptyModuleBuilder> {
  @NotNull
  @Override
  public EmptyModuleBuilder createModuleBuilder() {
    return new EmptyModuleBuilder() {
      @Override
      public ModuleType getModuleType() {
        return getInstance();
      }
    };
  }

  @Override
  public boolean isSupportedRootType(JpsModuleSourceRootType type) {
    return type == JavaSourceRootType.SOURCE;
  }
}
