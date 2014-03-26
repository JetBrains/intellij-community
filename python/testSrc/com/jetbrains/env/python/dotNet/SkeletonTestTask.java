package com.jetbrains.env.python.dotNet;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.env.python.debug.PyExecutionFixtureTestTask;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * Task for test that checks skeleton generation
 *
 * @author Ilya.Kazakevich
 */
class SkeletonTestTask extends PyExecutionFixtureTestTask {

  /**
   * Tags for this task to run
   */
  private static final Set<String> IRON_TAGS = Sets.newHashSet(PyIronPythonTest.IRON_TAG);
  /**
   * Number of seconds we wait for skeleton generation external process (should be enough)
   */
  private static final int SECONDS_TO_WAIT_FOR_SKELETON_GENERATION = 20;

  @Nullable
  private final String myExpectedSkeletonFile;
  @NotNull
  private final String myModuleNameToBeGenerated;
  @NotNull
  private final String mySourceFileToRunGenerationOn;
  @NotNull
  private final String myUseQuickFixWithThisModuleOnly;
  private PyFile myGeneratedSkeleton;

  /**
   * @param expectedSkeletonFile          if you want test to compare generated result with some file, provide its name.
   *                                      Pass null if you do not want to compare result with anything (you may do it yourself with {@link #getGeneratedSkeleton()})
   * @param moduleNameToBeGenerated       name of module you think we should generate in dotted notation (like "System.Web" or "com.myModule").
   *                                      System will wait for skeleton file for this module to be generated
   * @param sourceFileToRunGenerationOn   Source file where we should run "generate stubs" on. Be sure to place "caret" on appropriate place!
   * @param useQuickFixWithThisModuleOnly If there are several quick fixes in code, you may run fix only on this module.
   *                                      Pass null if you are sure there would be only one quickfix
   */
  SkeletonTestTask(@Nullable final String expectedSkeletonFile,
                   @NotNull final String moduleNameToBeGenerated,
                   @NotNull final String sourceFileToRunGenerationOn,
                   @Nullable final String useQuickFixWithThisModuleOnly) {
    myExpectedSkeletonFile = expectedSkeletonFile;
    myModuleNameToBeGenerated = moduleNameToBeGenerated.replace('.', '/');
    mySourceFileToRunGenerationOn = sourceFileToRunGenerationOn;
    myUseQuickFixWithThisModuleOnly = useQuickFixWithThisModuleOnly != null ? useQuickFixWithThisModuleOnly : "";
  }


  @Override
  public void runTestOn(@NotNull final String sdkHome) throws IOException {
    final Sdk sdk = getSdk(sdkHome);
    ModuleRootModificationUtil.setModuleSdk(myFixture.getModule(), sdk);
    final File skeletonsPath = new File(PythonSdkType.getSkeletonsPath(PathManager.getSystemPath(), sdkHome));
    File skeletonFileOrDirectory = new File(skeletonsPath, myModuleNameToBeGenerated); // File with module skeleton

    // Module may be stored in "moduleName.py" or "moduleName/__init__.py"
    if (skeletonFileOrDirectory.isDirectory()) {
      skeletonFileOrDirectory = new File(skeletonFileOrDirectory, PyNames.INIT_DOT_PY);
    }
    else {
      skeletonFileOrDirectory = new File(skeletonFileOrDirectory.getAbsolutePath() + PyNames.DOT_PY);
    }

    final File skeletonFile = skeletonFileOrDirectory;

    if (skeletonFile.exists()) { // To make sure we do not reuse it
      skeletonFile.delete();
    }

    myFixture.copyFileToProject("dotNet/" + mySourceFileToRunGenerationOn, mySourceFileToRunGenerationOn); // File that uses CLR library
    myFixture.copyFileToProject("dotNet/PythonLibs.dll", "PythonLibs.dll"); // Library itself
    myFixture.copyFileToProject("dotNet/SingleNameSpace.dll", "SingleNameSpace.dll"); // Another library
    myFixture.configureByFile(mySourceFileToRunGenerationOn);
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class); // This inspection should suggest us to generate stubs


    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
        final String intentionName = PyBundle.message("sdk.gen.stubs.for.binary.modules", myUseQuickFixWithThisModuleOnly);
        myFixture.findSingleIntention(intentionName).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
        waitForSkeleton(skeletonFile);
      }
    });

    FileUtil.copy(skeletonFile, new File(myFixture.getTempDirPath(), skeletonFile.getName()));
    if (myExpectedSkeletonFile != null) {
      myFixture.checkResultByFile(skeletonFile.getName(), myExpectedSkeletonFile, false);
    }
    myGeneratedSkeleton = (PyFile)myFixture.configureByFile(skeletonFile.getName());
  }


  @Override
  public Set<String> getTags() {
    return Collections.unmodifiableSet(IRON_TAGS);
  }

  /**
   * Waits {@link #SECONDS_TO_WAIT_FOR_SKELETON_GENERATION} seconds for file to be created.
   * Fails test after that.
   *
   * @param skeletonToWait file to wait
   */
  private static void waitForSkeleton(@NotNull final File skeletonToWait) {
    for (int i = 0; i < SECONDS_TO_WAIT_FOR_SKELETON_GENERATION; i++) {
      try {
        // Can't sync with external process (no IPC is available for now), so we use busy wait
        //noinspection BusyWait
        Thread.sleep(1000L);
        if (skeletonToWait.exists()) {
          return;
        }
      }
      catch (final InterruptedException e) {
        throw new IllegalStateException("Interrupted while waiting for skeleton ", e);
      }
    }
    final String message =
      String.format("After %s seconds of waiting, skeleton %s still not ready", SECONDS_TO_WAIT_FOR_SKELETON_GENERATION, skeletonToWait);
    Assert.assertTrue(message, skeletonToWait.exists());
  }

  /**
   * @return File for generated skeleton. Call it after {@link #runTestOn(String)} only!
   */
  @NotNull
  PyFile getGeneratedSkeleton() {
    return myGeneratedSkeleton;
  }
}
