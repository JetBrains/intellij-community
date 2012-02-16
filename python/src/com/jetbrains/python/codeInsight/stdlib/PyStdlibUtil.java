package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author vlan
 */
public class PyStdlibUtil {
  @Nullable private static Collection<String> PACKAGES = loadStdlibPackagesList();

  private PyStdlibUtil() {
  }

  @Nullable
  public static Collection<String> getPackages() {
    return PACKAGES;
  }

  @Nullable
  private static Collection<String> loadStdlibPackagesList() {
    final Logger log = Logger.getInstance(PyStdlibUtil.class.getName());
    final String helperPath = PythonHelpersLocator.getHelperPath("/tools/stdlib_packages.txt");
    try {
      final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(helperPath)));
      try {
        final List<String> result = new ArrayList<String>();
        String line;
        while ((line = reader.readLine()) != null) {
          result.add(line);
        }
        return result;
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      log.error("Cannot read list of standard library packages: " + e.getMessage());
    }
    return null;
  }
}
