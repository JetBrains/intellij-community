package com.jetbrains.python.inspections.unresolvedReference;

import java.util.HashMap;
import java.util.Map;

public class PyPackageAliasesProvider {
  private PyPackageAliasesProvider() {
  }

  public static final Map<String, String> commonImportAliases = new HashMap<>();
  public static final Map<String, String> packageAliases = new HashMap<>();

  static {
    commonImportAliases.put("np", "numpy");
    commonImportAliases.put("pl", "pylab");
    commonImportAliases.put("p", "pylab");
    commonImportAliases.put("sp", "scipy");
    commonImportAliases.put("pd", "pandas");
    commonImportAliases.put("sym", "sympy");
    commonImportAliases.put("sm", "statmodels");
    commonImportAliases.put("nx", "networkx");
    commonImportAliases.put("sk", "sklearn");

    commonImportAliases.put("plt", "matplotlib.pyplot");
    commonImportAliases.put("mpimg", "matplotlib.image");
    commonImportAliases.put("mimg", "matplotlib.image");
  }

  static {
    packageAliases.put("sklearn", "scikit-learn");
    packageAliases.put("Crypto", "PyCrypto");
    packageAliases.put("cv2", "pyopencv");
  }
}
