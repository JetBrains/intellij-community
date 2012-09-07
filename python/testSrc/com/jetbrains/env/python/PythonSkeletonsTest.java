package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.env.python.debug.PyTestTask;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PySkeletonRefresher;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.SkeletonVersionChecker;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Heavyweight integration tests of skeletons of Python binary modules.
 *
 * An environment test environment must have a 'skeletons' tag in order to be compatible with this test case. No specific packages are
 * required currently. Both Python 2 and Python 3 are OK. All platforms are OK.
 *
 * @author vlan
 */
public class PythonSkeletonsTest extends PyTestCase {
  public static final ImmutableSet<String> TAGS = ImmutableSet.of("skeletons");

  public void testBuiltins() {
    runTest(new SkeletonsTask() {
      @Override
      public void runTestOn(@NotNull Sdk sdk) {
        // Check the builtin skeleton header
        final PyFile builtins = PyBuiltinCache.getBuiltinsForSdk(myFixture.getProject(), sdk);
        assertNotNull(builtins);
        final VirtualFile virtualFile = builtins.getVirtualFile();
        assertNotNull(virtualFile);
        assertTrue(virtualFile.isInLocalFileSystem());
        final String path = virtualFile.getPath();
        final PySkeletonRefresher.SkeletonHeader header = PySkeletonRefresher.readSkeletonHeader(new File(path));
        assertNotNull(header);
        final int version = header.getVersion();
        assertTrue("Header version must be > 0, currently it is " + version, version > 0);
        assertEquals(SkeletonVersionChecker.BUILTIN_NAME, header.getBinaryFile());

        // Run inspections on a file that uses builtins
        myFixture.configureByFile("skeletons/" + getTestName(false) + ".py");
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        myFixture.checkHighlighting(true, false, false);
      }
    });
  }

  private void generateTempSkeletons(@NotNull Sdk sdk) throws InvalidSdkException, IOException {
    final Project project = myFixture.getProject();
    ModuleRootModificationUtil.setModuleSdk(getSingleModule(project), sdk);

    final SdkModificator modificator = sdk.getSdkModificator();
    modificator.removeRoots(OrderRootType.CLASSES);
    for (String path : PythonSdkType.getSysPathsFromScript(sdk.getHomePath())) {
      PythonSdkType.addSdkRoot(modificator, path);
    }
    final File tempDir = FileUtil.createTempDirectory(getTestName(false), null);
    final File skeletonsDir = new File(tempDir, PythonSdkType.SKELETON_DIR_NAME);
    FileUtil.createDirectory(skeletonsDir);
    final String skeletonsPath = skeletonsDir.toString();
    PythonSdkType.addSdkRoot(modificator, skeletonsPath);
    modificator.commitChanges();

    final SkeletonVersionChecker checker = new SkeletonVersionChecker(0);
    final PySkeletonRefresher refresher = new PySkeletonRefresher(project, sdk, skeletonsPath, null);
    final List<String> errors = refresher.regenerateSkeletons(checker, null);
    assertEmpty(errors);
  }

  @NotNull
  private static Module getSingleModule(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    assertEquals(1, modules.length);
    return modules[0];
  }

  private void runTest(@NotNull PyTestTask task) {
    PyEnvTestCase.runTest(task, getTestName(false));
  }

  @NotNull
  private static Sdk createTempSdk(@NotNull String sdkHome) {
    final VirtualFile binary = LocalFileSystem.getInstance().findFileByPath(sdkHome);
    assertNotNull("Interpreter file not found: " + sdkHome, binary);
    final Sdk sdk = SdkConfigurationUtil.setupSdk(new Sdk[0], binary, PythonSdkType.getInstance(), true, null, null);
    assertNotNull(sdk);
    return sdk;
  }

  private abstract class SkeletonsTask extends PyTestTask {
    @Override
    public void runTestOn(String sdkHome) throws Exception {
      final Sdk sdk = createTempSdk(sdkHome);
      generateTempSkeletons(sdk);
      runTestOn(sdk);
    }

    @Override
    public Set<String> getTags() {
      return TAGS;
    }

    protected abstract void runTestOn(@NotNull Sdk sdk);
  }
}
