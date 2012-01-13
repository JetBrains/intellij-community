package com.jetbrains.python;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.jetbrains.python.env.debug.PyEnvTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackagingUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author vlan
 */
public class PyPackagingTest extends PyTestCase {
  private static final Logger LOG = Logger.getInstance(PyEnvTestCase.class.getName());

  public void testGetInstalledPackages() {
    forAllPythonEnvs(new Processor<Sdk>() {
      @Override
      public boolean process(Sdk sdk) {
        List<PyPackage> packages = null;
        try {
          packages = PyPackagingUtil.getInstance().getInstalledPackages(sdk);
        }
        catch (PyExternalProcessException e) {
          final int retcode = e.getRetcode();
          if (retcode != PyPackagingUtil.ERROR_NO_PACKAGING_TOOLS) {
            fail(String.format("Error for interpreter '%s': %s", sdk.getHomePath(), e.getMessage()));
          }
        }
        if (packages != null) {
          assertTrue(packages.size() > 0);
          for (PyPackage pkg : packages) {
            assertTrue(pkg.getName().length() > 0);
          }
        }
        return true;
      }
    });
  }

  public void testCreateVirtualEnv() {
    forAllPythonEnvs(new Processor<Sdk>() {
      @Override
      public boolean process(Sdk sdk) {
        try {
          final File tempDir = FileUtil.createTempDirectory(getTestName(false), null);
          final String venvSdkHome = PyPackagingUtil.createVirtualEnv(sdk, tempDir.toString());
          final Sdk venvSdk = createTempSdk(venvSdkHome);
          assertNotNull(venvSdk);
          assertTrue(PythonSdkType.isVirtualEnv(venvSdk));
          final List<PyPackage> packages = PyPackagingUtil.getInstance().getInstalledPackages(venvSdk);
          final PyPackage distribute = findPackage("distribute", packages);
          assertNotNull(distribute);
          assertEquals("distribute", distribute.getName());
          assertEquals("0.6.24", distribute.getVersion());
          final PyPackage pip = findPackage("pip", packages);
          assertNotNull(pip);
          assertEquals("pip", pip.getName());
          assertEquals("1.0.2", pip.getVersion());
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        catch (PyExternalProcessException e) {
          fail(String.format("Error for interpreter '%s': %s", sdk.getHomePath(), e.getMessage()));
        }
        return true;
      }
    });
  }

  public void testUninstallPackage() {
    forAllPythonEnvs(new Processor<Sdk>() {
      @Override
      public boolean process(final Sdk sdk) {
        try {
          final File tempDir = FileUtil.createTempDirectory(getTestName(false), null);
          final String venvSdkHome = PyPackagingUtil.createVirtualEnv(sdk, tempDir.getPath());
          final Sdk venvSdk = createTempSdk(venvSdkHome);
          assertNotNull(venvSdk);
          final PyPackagingUtil packaging = PyPackagingUtil.getInstance();
          final List<PyPackage> packages1 = packaging.getInstalledPackages(venvSdk);
          final PyPackage pip1 = findPackage("pip", packages1);
          assertNotNull(pip1);
          assertEquals("pip", pip1.getName());
          assertEquals("1.0.2", pip1.getVersion());
          packaging.uninstallPackage(venvSdk, pip1);
          final List<PyPackage> packages2 = packaging.getInstalledPackages(venvSdk);
          final PyPackage pip2 = findPackage("pip", packages2);
          assertNull(pip2);
        }
        catch (PyExternalProcessException e) {
          fail(String.format("Error for interpreter '%s': %s", sdk.getHomePath(), e.getMessage()));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        return true;
      }
    });
  }

  @Nullable
  private static PyPackage findPackage(String name, List<PyPackage> packages) {
    for (PyPackage pkg : packages) {
      if (name.equals(pkg.getName())) {
        return pkg;
      }
    }
    return null;
  }

  private void forAllPythonEnvs(@NotNull Processor<Sdk> processor) {
    final List<String> roots = PyEnvTestCase.getPythonRoots();
    if (roots.size() == 0) {
      String msg = getTestName(false) + ": environments are not defined. Skipping.";
      LOG.warn(msg);
      System.out.println(msg);
      return;
    }
    for (String root : roots) {
      final String sdkHome = PythonSdkType.getPythonExecutable(root);
      assertNotNull(sdkHome);
      final Sdk sdk = createTempSdk(sdkHome);
      assertNotNull(sdk);
      processor.process(sdk);
    }
  }

  @Nullable
  private static Sdk createTempSdk(@NotNull String sdkHome) {
    final VirtualFile binary = LocalFileSystem.getInstance().findFileByPath(sdkHome);
    if (binary != null) {
      return SdkConfigurationUtil.setupSdk(new Sdk[0], binary, PythonSdkType.getInstance(), true, null, null);
    }
    return null;
  }
}
