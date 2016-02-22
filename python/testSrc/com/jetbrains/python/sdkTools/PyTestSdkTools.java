package com.jetbrains.python.sdkTools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import com.jetbrains.python.sdk.skeletons.SkeletonVersionChecker;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Engine to create SDK for tests.
 * See {@link #createTempSdk(com.intellij.openapi.vfs.VirtualFile, SdkCreationType, com.intellij.openapi.module.Module)}
 *
 * @author Ilya.Kazakevich
 */
public final class PyTestSdkTools {

  private static final Sdk[] NO_SDK = new Sdk[0];

  private PyTestSdkTools() {

  }

  /**
   * Creates SDK by its path and associates it with module (if module provided)
   *
   * @param sdkHome         path to sdk
   * @param sdkCreationType SDK creation strategy (see {@link SdkCreationType} doc)
   * @return sdk
   */
  @NotNull
  public static Sdk createTempSdk(@NotNull final VirtualFile sdkHome,
                                  @NotNull final SdkCreationType sdkCreationType,
                                  @Nullable final Module module
  )
    throws InvalidSdkException, IOException {
    final Ref<Sdk> ref = Ref.create();
    UsefulTestCase.edt(new Runnable() {

      @Override
      public void run() {
        final Sdk sdk = SdkConfigurationUtil.setupSdk(NO_SDK, sdkHome, PythonSdkType.getInstance(), true, null, null);
        Assert.assertNotNull("Failed to create SDK on " + sdkHome, sdk);
        ref.set(sdk);
      }
    });
    final Sdk sdk = ref.get();
    if (sdkCreationType != SdkCreationType.EMPTY_SDK) {
      generateTempSkeletonsOrPackages(sdk, sdkCreationType == SdkCreationType.SDK_PACKAGES_AND_SKELETONS, module);
    }
    UsefulTestCase.edt(new Runnable() {
      @Override
      public void run() {
        SdkConfigurationUtil.addSdk(sdk);
      }
    });
    return sdk;
  }


  /**
   * Adds installed eggs to SDK, generates skeletons (optionally) and associates it with modle.
   *
   * @param sdk          sdk to process
   * @param addSkeletons add skeletons or only packages
   * @param module       module to associate with (if provided)
   * @throws InvalidSdkException bas sdk
   * @throws IOException         failed to read eggs
   */
  private static void generateTempSkeletonsOrPackages(@NotNull final Sdk sdk,
                                                      final boolean addSkeletons,
                                                      @Nullable final Module module)
    throws InvalidSdkException, IOException {
    Project project = null;

    if (module != null) {
      project = module.getProject();
      final Project finalProject = project;
      // Associate with module
      ModuleRootModificationUtil.setModuleSdk(module, sdk);

      UsefulTestCase.edt(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              ProjectRootManager.getInstance(finalProject).setProjectSdk(sdk);
            }
          });
        }
      });
    }


    final SdkModificator modificator = sdk.getSdkModificator();
    modificator.removeRoots(OrderRootType.CLASSES);

    modificator.setSdkAdditionalData(new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk)));

    for (final String path : PythonSdkType.getSysPathsFromScript(sdk.getHomePath())) {
      addTestSdkRoot(modificator, path);
    }
    if (!addSkeletons) {
      UsefulTestCase.edt(new Runnable() {
        @Override
        public void run() {
          modificator.commitChanges();
        }
      });
      return;
    }

    final File tempDir = FileUtil.createTempDirectory(PyTestSdkTools.class.getName(), null);
    final File skeletonsDir = new File(tempDir, PythonSdkType.SKELETON_DIR_NAME);
    FileUtil.createDirectory(skeletonsDir);
    final String skeletonsPath = skeletonsDir.toString();
    addTestSdkRoot(modificator, skeletonsPath);

    UsefulTestCase.edt(new Runnable() {
      @Override
      public void run() {
        modificator.commitChanges();
      }
    });

    final SkeletonVersionChecker checker = new SkeletonVersionChecker(0);

    final PySkeletonRefresher refresher = new PySkeletonRefresher(null, null, sdk, skeletonsPath, null, null);
    final List<String> errors = refresher.regenerateSkeletons(checker);

    PySkeletonRefresher
      .refreshSkeletonsOfSdk(project, null, PythonSdkType.getSkeletonsPath(PathManager.getSystemPath(), sdk.getHomePath()), sdk);

    Assert.assertThat("Errors found", errors, Matchers.empty());
  }

  public static void addTestSdkRoot(@NotNull SdkModificator sdkModificator, @NotNull String path) {
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (file != null) {
      sdkModificator.addRoot(PythonSdkType.getSdkRootVirtualFile(file), OrderRootType.CLASSES);
    }
  }
}
