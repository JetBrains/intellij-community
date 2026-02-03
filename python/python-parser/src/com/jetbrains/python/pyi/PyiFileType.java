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
package com.jetbrains.python.pyi;

import com.jetbrains.python.PyParsingBundle;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

public final class PyiFileType extends PythonFileType {
  public static final @NotNull PythonFileType INSTANCE = new PyiFileType();

  private PyiFileType() {
    super(new PyiLanguageDialect());
  }

  @Override
  public @NotNull String getName() {
    return PyiLanguageDialect.ID;
  }

  @Override
  public @NotNull String getDescription() {
    return PyParsingBundle.message("filetype.python.stub.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "pyi";
  }
}
