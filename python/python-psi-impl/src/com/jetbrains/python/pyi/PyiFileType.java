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

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

public class PyiFileType extends PythonFileType {
  @NotNull
  public static final PythonFileType INSTANCE = new PyiFileType();

  private PyiFileType() {
    super(new PyiLanguageDialect());
  }

  @NotNull
  @Override
  public String getName() {
    return PyiLanguageDialect.ID;
  }

  @NotNull
  @Override
  public String getDescription() {
    return PyPsiBundle.message("filetype.python.stub.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "pyi";
  }
}
