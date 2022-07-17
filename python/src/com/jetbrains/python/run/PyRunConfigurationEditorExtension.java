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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to extend the editors of pythonic run configurations.
 * <p>
 * As for now, it is used to provide additional fields for editing settings of specific Python interpreters (f.e. custom Docker container
 * settings for Docker-based Python interpreters).
 */
@ApiStatus.Internal
public interface PyRunConfigurationEditorExtension {
  ExtensionPointName<PyRunConfigurationEditorExtension> EP_NAME = ExtensionPointName.create("Pythonid.runConfigurationEditorExtension");

  /**
   * Returns the editor factory that could be applied for the provided {@code configuration} or {@code null} if no factory is available for
   * it.
   * <p>
   * Note that this method is called frequently. It might be a good idea to cache the returned factory.
   *
   * @param configuration Python run configuration being edited
   * @return editor factory applicable for provided {@code configuration}
   */
  @Nullable
  PyRunConfigurationEditorFactory accepts(@NotNull AbstractPythonRunConfiguration<?> configuration);
}
