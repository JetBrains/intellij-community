package com.jetbrains.python;

import com.intellij.openapi.editor.colors.impl.BundledColorSchemesProvider;

/**
 * Adds bundled color schemes for PyCharm.
 * <br/>
 * User: dcheryasov
 * Date: 12/3/10 4:49 PM
 */
public class PythonBundledColorSchemeProvider implements BundledColorSchemesProvider {

  public static final String[] PATHS = {
    "/colorSchemes/WarmNeon",
  };

  @Override
  public String[] getBundledSchemesRelativePaths() {
    return PATHS;
  }

  @Override
  public String getDefaultSchemaExtensionPath() {
    return null;
  }
}
