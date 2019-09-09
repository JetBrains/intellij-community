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

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.jetbrains.python.PythonParserDefinition;

/**
 * @author vlan
 */
public class PyiParserDefinition extends PythonParserDefinition {
  public static final IFileElementType PYTHON_STUB_FILE = new PyiFileElementType(PyiLanguageDialect.getInstance());

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyiFile(viewProvider);
  }

  @Override
  public IFileElementType getFileNodeType() {
    return PYTHON_STUB_FILE;
  }
}
