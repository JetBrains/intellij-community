
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
@ApiStatus.Internal
public class PyDoctestFileType extends PythonFileType {
  public static final PythonFileType INSTANCE = new PyDoctestFileType();

  private PyDoctestFileType() {
    super(new PyDoctestLanguageDialect());
  }

  @Override
  public @NotNull String getName() {
    return PyNames.PY_DOCSTRING_ID;
  }

  @Override
  public @NotNull String getDescription() {
    return PyPsiBundle.message("filetype.python.docstring.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "doctest";
  }
}
