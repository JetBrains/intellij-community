package com.jetbrains.env.python.dotNet;

import com.google.common.collect.Sets;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.GenerateBinaryStubsFix;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
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

  /**
   * @param expectedSkeletonFile          if you want test to compare generated result with some file, provide its name.
   *                                      Pass null if you do not want to compare result with anything
   *                                      (you may do it yourself by overwriting {@link #runTestOn(String)}) but <strong>call super</strong>
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
    super(null);
    myExpectedSkeletonFile = expectedSkeletonFile;
    myModuleNameToBeGenerated = moduleNameToBeGenerated.replace('.', '/');
    mySourceFileToRunGenerationOn = sourceFileToRunGenerationOn;
    myUseQuickFixWithThisModuleOnly = useQuickFixWithThisModuleOnly != null ? useQuickFixWithThisModuleOnly : "";
  }


  @Override
  public void runTestOn(@NotNull final String sdkHome) throws IOException, InvalidSdkException {
    final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_ONLY);
    final File skeletonsPath = new File(PythonSdkType.getSkeletonsPath(PathManager.getSystemPath(), sdk.getHomePath()));
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
      assert skeletonFile.delete() : "Failed to delete file " + skeletonFile;
    }

    ApplicationManager.getApplication().invokeAndWait(() -> {
      myFixture.copyFileToProject("dotNet/" + mySourceFileToRunGenerationOn, mySourceFileToRunGenerationOn); // File that uses CLR library
      myFixture.copyFileToProject("dotNet/PythonLibs.dll", "PythonLibs.dll"); // Library itself
      myFixture.copyFileToProject("dotNet/SingleNameSpace.dll", "SingleNameSpace.dll"); // Another library
      myFixture.configureByFile(mySourceFileToRunGenerationOn);
    }, ModalityState.NON_MODAL);
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class); // This inspection should suggest us to generate stubs


    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      final String intentionName = PyBundle.message("sdk.gen.stubs.for.binary.modules", myUseQuickFixWithThisModuleOnly);
      IntentionAction intention = myFixture.findSingleIntention(intentionName);

      if (intention instanceof IntentionActionDelegate) {
        intention = ((IntentionActionDelegate)intention).getDelegate();
      }

      Assert.assertNotNull("No intention found to generate skeletons!", intention);
      Assert.assertThat("Intention should be quick fix to run", intention, Matchers.instanceOf(QuickFixWrapper.class));
      final LocalQuickFix quickFix = ((QuickFixWrapper)intention).getFix();
      Assert.assertThat("Quick fix should be 'generate binary skeletons' fix to run", quickFix,
                        Matchers.instanceOf(GenerateBinaryStubsFix.class));
      final Task fixTask = ((GenerateBinaryStubsFix)quickFix).getFixTask(myFixture.getFile());
      fixTask.run(new AbstractProgressIndicatorBase());
    });

    FileUtil.copy(skeletonFile, new File(myFixture.getTempDirPath(), skeletonFile.getName()));
    if (myExpectedSkeletonFile != null) {
      final String actual = StreamUtil.readText(new FileInputStream(skeletonFile), Charset.defaultCharset());
      final String skeletonText =
        StreamUtil.readText(new FileInputStream(new File(getTestDataPath(), myExpectedSkeletonFile)), Charset.defaultCharset());

      // TODO: Move to separate method ?
      if (!Matchers.equalToIgnoringWhiteSpace(removeGeneratorVersion(skeletonText)).matches(removeGeneratorVersion(actual))) {
        throw new FileComparisonFailure("asd", skeletonText, actual, skeletonFile.getAbsolutePath());
      }
    }
    myFixture.configureByFile(skeletonFile.getName());
  }

  /**
   * Removes strings that starts with "# by generator", because generator version may change
   *
   * @param textToClean text to remove strings from
   * @return text after cleanup
   */
  private static String removeGeneratorVersion(@NotNull final String textToClean) {
    final List<String> strings = StringUtil.split(textToClean, "\n");
    final Iterator<String> iterator = strings.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().startsWith("# by generator")) {
        iterator.remove();
      }
    }
    return StringUtil.join(strings, "\n");
  }


  @NotNull
  @Override
  public Set<String> getTags() {
    return Collections.unmodifiableSet(IRON_TAGS);
  }
}
