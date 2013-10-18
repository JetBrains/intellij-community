
package com.jetbrains.python.documentation.doctest;

import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyDocstringFileType extends PythonFileType {
  public static PythonFileType INSTANCE = new PyDocstringFileType();

  protected PyDocstringFileType() {
    super(new PyDocstringLanguageDialect());
  }

  @NotNull
  @Override
  public String getName() {
    return "PyDocstring";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "python docstring";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "docstring";
  }
}
