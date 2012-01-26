package com.jetbrains.python;

import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.jetbrains.python.env.debug.PyEnvTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author vlan
 */
public class PyPackagingTest extends PyTestCase {
  private static final Logger LOG = Logger.getInstance(PyEnvTestCase.class.getName());

  public void testGetPackages() {
    forAllPythonEnvs(new Processor<Sdk>() {
      @Override
      public boolean process(Sdk sdk) {
        List<PyPackage> packages = null;
        try {
          packages = PyPackageManager.getInstance(sdk).getPackages();
        }
        catch (PyExternalProcessException e) {
          final int retcode = e.getRetcode();
          if (retcode != PyPackageManager.ERROR_NO_PACKAGING_TOOLS) {
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
          final String venvSdkHome = PyPackageManager.getInstance(sdk).createVirtualEnv(tempDir.toString());
          final Sdk venvSdk = createTempSdk(venvSdkHome);
          assertNotNull(venvSdk);
          assertTrue(PythonSdkType.isVirtualEnv(venvSdk));
          final List<PyPackage> packages = PyPackageManager.getInstance(venvSdk).getPackages();
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

  public void testInstallPackage() {
    forAllPythonEnvs(new Processor<Sdk>() {
      @Override
      public boolean process(final Sdk sdk) {
        try {
          final File tempDir = FileUtil.createTempDirectory(getTestName(false), null);
          final String venvSdkHome = PyPackageManager.getInstance(sdk).createVirtualEnv(tempDir.getPath());
          final Sdk venvSdk = createTempSdk(venvSdkHome);
          assertNotNull(venvSdk);
          final PyPackageManager manager = PyPackageManager.getInstance(venvSdk);
          final List<PyPackage> packages1 = manager.getPackages();
          // TODO: Install Markdown from a local file
          manager.install(list(new PyRequirement("Markdown", "<", "2.2"), new PyRequirement("httplib2")));
          final List<PyPackage> packages2 = manager.getPackages();
          final PyPackage markdown2 = findPackage("Markdown", packages2);
          assertNotNull(markdown2);
          assertTrue(markdown2.isInstalled());
          final PyPackage pip1 = findPackage("pip", packages1);
          assertNotNull(pip1);
          assertEquals("pip", pip1.getName());
          assertEquals("1.0.2", pip1.getVersion());
          manager.uninstall(pip1);
          final List<PyPackage> packages3 = manager.getPackages();
          final PyPackage pip2 = findPackage("pip", packages3);
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

  public void testParseRequirement() {
    assertEquals(new PyRequirement("Django", null, null), PyRequirement.parse("Django"));
    assertEquals(new PyRequirement("django", null, null), PyRequirement.parse("Django"));
    assertEquals(new PyRequirement("Django", "==", "1.3.1"), PyRequirement.parse("Django==1.3.1"));
    assertEquals(new PyRequirement("Django", "<", "1.4"), PyRequirement.parse("   Django       <   1.4   "));
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

    if (PyEnvTestCase.notEnvConfiguration()) {
      LOG.info("Running under teamcity but not by Env configuration. Skipping.");
      return;
    }

    if (PyEnvTestCase.IS_UNDER_TEAMCITY && SystemInfo.isWindows) {
      return; //Don't run under Windows as after deleting from created virtualenvs original interpreter got spoiled
    }

    if (roots.size() == 0) {
      String msg = getTestName(false) + ": environments are not defined. Skipping.";
      LOG.warn(msg);
      System.out.println(msg);
      return;
    }
    for (String root : roots) {
      if (!PyEnvTestCase.isSuitableForTags(PyEnvTestCase.loadEnvTags(root), Sets.newHashSet("packaging"))) {
        continue; // Run only on special test-envs as because of downloading packages on every run is is too heavy to run it on all envs
      }
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

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
  }
}
