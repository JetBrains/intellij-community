package com.jetbrains.python.inspections.unresolvedReference;

import java.util.HashMap;
import java.util.Map;

public class PyPackageAliasesProvider {
  private PyPackageAliasesProvider() {
  }

  public static final Map<String, String> commonAliases = new HashMap<String, String>();

  static {
    commonAliases.put("np", "numpy");
    commonAliases.put("pl", "pylab");
    commonAliases.put("p", "pylab");
    commonAliases.put("sp", "scipy");
    commonAliases.put("pd", "pandas");
    commonAliases.put("sym", "sympy");
    commonAliases.put("sm", "statmodels");
    commonAliases.put("nx", "networkx");
    commonAliases.put("sk", "sklearn");

    commonAliases.put("plt", "matplotlib.pyplot");
    commonAliases.put("mpimg", "matplotlib.image");
    commonAliases.put("mimg", "matplotlib.image");
  }
}
