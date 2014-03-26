package com.jetbrains.env.python.dotNet;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.env.python.debug.PyExecutionFixtureTestTask;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
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
  /**
   * We check that generated skeleton is exactly as this file
   */
  private static final String DOT_NET_EXPECTED_SKELETON_PY = "dotNet/expected.skeleton.py";
  private static final String SKELETON_FILE_TO_TEST = "testSkeleton.py";

  @Override
  public void runTestOn(@NotNull final String sdkHome) throws IOException {
    final Sdk sdk = getSdk(sdkHome);
    ModuleRootModificationUtil.setModuleSdk(myFixture.getModule(), sdk);
    final File skeletonsPath = new File(PythonSdkType.getSkeletonsPath(PathManager.getSystemPath(), sdkHome));
    final File skeleton = new File(skeletonsPath, "com/just/like/java.py"); // File with module skeleton

    myFixture.copyFileToProject("dotNet/testSkeleton.py", SKELETON_FILE_TO_TEST); // File that uses CLR library
    myFixture.copyFileToProject("dotNet/PythonLibs.dll", "PythonLibs.dll"); // Library itself
    myFixture.configureByFile(SKELETON_FILE_TO_TEST);
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class); // This inspection should suggest us to generate stubs



    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
        myFixture.findSingleIntention("Generate").invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
        waitForSkeleton(skeleton);
      }
    });

    FileUtil.copy(skeleton, new File(myFixture.getTempDirPath(), skeleton.getName()));
    myFixture.checkResultByFile(skeleton.getName(), DOT_NET_EXPECTED_SKELETON_PY, false);
  }


  @Override
  public Set<String> getTags() {
    return Collections.unmodifiableSet(IRON_TAGS);
  }

  /**
   * Waits {@link #SECONDS_TO_WAIT_FOR_SKELETON_GENERATION} seconds for file to be created.
   * Fails test after that.
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
}
