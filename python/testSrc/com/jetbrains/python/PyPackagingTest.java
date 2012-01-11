package com.jetbrains.python;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.env.debug.PyEnvTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackagingUtil;

import java.util.List;

/**
 * @author vlan
 */
public class PyPackagingTest extends PyTestCase {
  private static final Logger LOG = Logger.getInstance(PyEnvTestCase.class.getName());

  public void testInstalledPackages() {
    final List<String> roots = PyEnvTestCase.getPythonRoots();
    if (roots.size() == 0) {
      String msg = getTestName(false) + ": environments are not defined. Skipping.";
      LOG.warn(msg);
      System.out.println(msg);
      return;
    }
    for (String root : roots) {
      List<PyPackage> packages = null;
      try {
         packages = PyPackagingUtil.getInstalledPackages(root);
      }
      catch (PyExternalProcessException e) {
        final int retcode = e.getRetcode();
        if (retcode != PyPackagingUtil.OK && retcode != PyPackagingUtil.ERROR_NO_PACKAGING_TOOLS) {
          fail(String.format("Error for root '%s': %s", root, e.getMessage()));
        }
      }
      if (packages != null) {
        assertTrue(packages.size() > 0);
        for (PyPackage pkg : packages) {
          assertTrue(pkg.getName().length() > 0);
        }
      }
    }
  }
}
