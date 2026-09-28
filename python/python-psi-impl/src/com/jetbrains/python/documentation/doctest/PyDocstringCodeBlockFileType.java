// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

public class PyDocstringCodeBlockFileType extends PythonFileType {
  public static final PythonFileType INSTANCE = new PyDocstringCodeBlockFileType();

  private PyDocstringCodeBlockFileType() {
    super(new PyDocstringCodeBlockLanguageDialect());
  }

  @Override
  public @NotNull String getName() {
    return PyNames.PY_DOCSTRING_CODE_BLOCK_ID;
  }

  @Override
  public @NotNull String getDescription() {
    return PyPsiBundle.message("filetype.python.docstring.codeblock.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "docstringcodeblock";
  }
}
