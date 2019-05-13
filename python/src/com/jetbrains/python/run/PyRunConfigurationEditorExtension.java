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
package com.jetbrains.python.run;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Koshevoy
 */
public interface PyRunConfigurationEditorExtension {
  ExtensionPointName<PyRunConfigurationEditorExtension> EP_NAME =
    ExtensionPointName.create("Pythonid.runConfigurationEditorExtension");

  boolean accepts(@NotNull AbstractPythonRunConfiguration configuration);

  @NotNull
  SettingsEditor<AbstractPythonRunConfiguration> createEditor(@NotNull AbstractPythonRunConfiguration configuration);

  class Factory {
    @Nullable
    public static PyRunConfigurationEditorExtension getExtension(@NotNull AbstractPythonRunConfiguration<?> configuration) {
      PyRunConfigurationEditorExtension[] extensions = EP_NAME.getExtensions();
      for (PyRunConfigurationEditorExtension extension : extensions) {
        if (extension.accepts(configuration)) {
          return extension;
        }
      }
      return null;
    }
  }
}
