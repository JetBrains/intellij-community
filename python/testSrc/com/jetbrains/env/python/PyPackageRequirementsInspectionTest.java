package com.jetbrains.env.python;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.Processor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyPackageRequirementsInspection;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author vlan
 */
public class PyPackageRequirementsInspectionTest extends PyTestCase {
  public void testPartiallySatisfiedRequirementsTxt() {
    doTest("test1.py");
  }

  public void testPartiallySatisfiedSetupPy() {
    doTest("test1.py");
  }

  public void testImportsNotInRequirementsTxt() {
    doTest("test1.py");
  }

  private void doTest(@NotNull final String filename) {
    final String dir = String.format("inspections/PyPackageRequirementsInspection/%s", getTestName(false));
    myFixture.enableInspections(PyPackageRequirementsInspection.class);
    PyPackagingTest.forAllPythonEnvs(getTestName(false), new Processor<Sdk>() {
      @Override
      public boolean process(Sdk sdk) {
        final String perSdkDir = Integer.toHexString(System.identityHashCode(sdk));
        final VirtualFile root = myFixture.copyDirectoryToProject(dir, perSdkDir);
        assertNotNull(root);
        setupModuleSdk(getSingleModule(myFixture.getProject()), sdk, root);
        myFixture.testHighlighting(true, true, true, perSdkDir + File.separator + filename);
        return true;
      }
    });
  }

  @NotNull
  private static Module getSingleModule(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    assertEquals(1, modules.length);
    return modules[0];
  }

  private static void setupModuleSdk(@NotNull Module module, @NotNull Sdk sdk, @NotNull VirtualFile root) {
    ModuleRootModificationUtil.setModuleSdk(module, sdk);
    PsiTestUtil.addContentRoot(module, root);
  }
}
