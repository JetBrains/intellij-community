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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyiFile extends PyFileImpl {
  public PyiFile(FileViewProvider viewProvider) {
    super(viewProvider, PyiLanguageDialect.getInstance());
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return PyiFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "PyiFile:" + getName();
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    return LanguageLevel.PYTHON35;
  }
}
