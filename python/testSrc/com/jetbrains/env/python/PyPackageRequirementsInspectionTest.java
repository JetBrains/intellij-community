package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.inspections.PyPackageRequirementsInspection;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author vlan
 */
public class PyPackageRequirementsInspectionTest extends PyEnvTestCase {
  public static final ImmutableSet<String> TAGS = ImmutableSet.of("requirements");

  public void testPartiallySatisfiedRequirementsTxt() {
    doTest("test1.py");
  }

  public void testPartiallySatisfiedSetupPy() {
    doTest("test1.py");
  }

  public void testImportsNotInRequirementsTxt() {
    doTest("test1.py");
  }

  public void testDuplicateInstallAndTests() {
    doTest("test1.py");
  }

  private void doTest(@NotNull final String filename) {
    final String dir = getTestName(false);
    runPythonTest(new PyExecutionFixtureTestTask() {
      @Override
      protected String getTestDataPath() {
        return PythonTestUtil.getTestDataPath() + "/inspections/PyPackageRequirementsInspection";
      }

      @Override
      public void runTestOn(String sdkHome) throws Exception {
        myFixture.enableInspections(PyPackageRequirementsInspection.class);
        final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_ONLY);
        final String perSdkDir = Integer.toHexString(System.identityHashCode(sdk));
        final VirtualFile root = myFixture.copyDirectoryToProject(dir, perSdkDir);
        assertNotNull(root);
        final Module module = myFixture.getModule();
        setupModuleSdk(module, sdk, root);
        try {
          final VirtualFile file = root.findFileByRelativePath(filename);
          assertNotNull(file);
          edt(new Runnable() {
            @Override
            public void run() {
              myFixture.testHighlighting(true, true, true, file);
            }
          });
        }
        finally {
          PsiTestUtil.removeAllRoots(module, sdk);
        }
      }

      @Override
      public Set<String> getTags() {
        return TAGS;
      }
    });
  }

  private static void setupModuleSdk(@NotNull Module module, @NotNull Sdk sdk, @NotNull VirtualFile root) {
    ModuleRootModificationUtil.setModuleSdk(module, sdk);
    PsiTestUtil.addContentRoot(module, root);
  }
}
