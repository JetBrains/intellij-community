package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
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
    "/colorSchemes/monokai",
    "/colorSchemes/twilight",
  };

  @Override
  public String[] getBundledSchemesRelativePaths() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    return PATHS;
  }

  @Override
  public String getDefaultSchemaExtensionPath() {
    return null;
  }
}
