/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.env;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.jetbrains.LoggingRule;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.tools.sdkTools.PySdkTools;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PyEnvTaskRunner {
  private static final Logger LOG = Logger.getInstance(PyEnvTaskRunner.class);
  private final List<String> myRoots;
  @Nullable
  private final LoggingRule myLoggingRule;

  /**
   * @param loggingRule to be passed by {@link PyEnvTestCase}.
   *                    {@link LoggingRule#startLogging(Disposable, Iterable)} will be called
   *                    if task has {@link PyExecutionFixtureTestTask#getClassesToEnableDebug()}
   */
  public PyEnvTaskRunner(List<String> roots, @Nullable final LoggingRule loggingRule) {
    myRoots = roots;
    myLoggingRule = loggingRule;
  }

  /**
   * Runs test on all interpreters.
   *
   * @param skipOnFlavors optional array of flavors of interpreters to skip
   *
   * @param tagsRequiredByTest optional array of tags to run tests on interpreters with these tags only
   */
  public void runTask(@NotNull final PyTestTask testTask,
                      @NotNull final String testName,
                      @Nullable final Class<? extends PythonSdkFlavor>[] skipOnFlavors,
                      @NotNull final String... tagsRequiredByTest) {
    boolean wasExecuted = false;

    List<String> passedRoots = Lists.newArrayList();

    final Set<String> requiredTags = Sets.union(testTask.getTags(), Sets.newHashSet(tagsRequiredByTest));

    final Set<String> tagsToCover = null;

    for (String root : myRoots) {

      List<String> envTags = PyEnvTestCase.loadEnvTags(root);
      final boolean suitableForTask = isSuitableForTask(envTags, requiredTags);
      final boolean shouldRun = shouldRun(root, testTask);
      if (!suitableForTask || !shouldRun) {
        LOG.warn(String.format("Skipping %s (compatible with tags: %s, should run:%s)", root, suitableForTask, shouldRun));
        continue;
      }

      if (tagsToCover != null && envTags.size() > 0 && !isNeededToRun(tagsToCover, envTags)) {
        LOG.warn(String.format("Skipping %s (test already was executed on a similar environment)", root));
        continue;
      }

      if (tagsToCover != null) {
        tagsToCover.removeAll(envTags);
      }

      LOG.warn(String.format("Running on root %s", root));

      try {
        testTask.setUp(testName);
        wasExecuted = true;
        if (isJython(root)) {
          testTask.useLongTimeout();
        }
        else {
          testTask.useNormalTimeout();
        }
        final String executable = PythonSdkType.getPythonExecutable(root);
        assert executable != null : "No executable in " + root;

        final Sdk sdk = getSdk(executable, testTask);
        if (skipOnFlavors != null) {
          final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
          if (Arrays.stream(skipOnFlavors).anyMatch((o) -> o.isInstance(flavor))) {
            LOG.warn("Skipping flavor " + flavor.toString());
            continue;
          }
        }

        /*
          Skipping test if {@link PyTestTask} reports it does not support this language level
         */
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        if (testTask.isLanguageLevelSupported(languageLevel)) {

          if (myLoggingRule != null) {
            final PyExecutionFixtureTestTask execTask = ObjectUtils.tryCast(testTask, PyExecutionFixtureTestTask.class);
            if (execTask != null) {
              // Fill be disabled automatically on project dispose
              myLoggingRule.startLogging(execTask.getProject(), execTask.getClassesToEnableDebug());
            }
          }


          testTask.runTestOn(executable, sdk);

          passedRoots.add(root);
        }
        else {
          LOG.warn(String.format("Skipping root %s", root));
        }
      }
      catch (final RuntimeException | Error ex) {
        // Runtime and error are logged including environment info
        LOG.warn(joinStrings(passedRoots, "Tests passed environments: ") +
                 "Test failed on " +
                 getEnvType() +
                 " environment " +
                 root);
        throw ex;
      }
      catch (final Exception e) {
        // Exception can't be thrown with out of
        throw new PyEnvWrappingException(e);
      }
      finally {
        try {
          // Teardown should be called on main thread because fixture teardown checks for
          // thread leaks, and blocked main thread is considered as leaked
          testTask.tearDown();
        }
        catch (final Exception e) {
          throw new RuntimeException("Couldn't tear down task", e);
        }
      }
    }

    if (!wasExecuted) {
      throw new RuntimeException("test" +
                                 testName +
                                 " was not executed.\n" +
                                 joinStrings(myRoots, "All roots: ") +
                                 "\n" +
                                 joinStrings(testTask.getTags(), "Required tags in tags.txt in root: "));
    }
  }

  private static boolean isNeededToRun(@NotNull Set<String> tagsToCover, @NotNull List<String> envTags) {
    for (String tag : envTags) {
      if (tagsToCover.contains(tag)) {
        return true;
      }
    }

    return false;
  }


  protected boolean shouldRun(String root, PyTestTask task) {
    return true;
  }

  /**
   * Get SDK by executable*
   */
  @NotNull
  protected Sdk getSdk(@NotNull final String executable, @NotNull final PyTestTask testTask) {
    try {
      final URL rootUrl = new URL(String.format("file:///%s", executable));
      final VirtualFile url = VfsUtil.findFileByURL(rootUrl);
      assert url != null : "No file " + rootUrl;
      return PySdkTools.createTempSdk(url, SdkCreationType.EMPTY_SDK, null);
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  protected String getEnvType() {
    return "local";
  }

  private static boolean isSuitableForTask(List<String> availableTags, @NotNull final Set<String> requiredTags) {
    return isSuitableForTags(availableTags, requiredTags);
  }

  public static boolean isSuitableForTags(List<String> envTags, Set<String> taskTags) {
    Set<String> necessaryTags = Sets.newHashSet(taskTags);

    for (String tag : envTags) {
      necessaryTags.remove(tag.trim());
    }

    for (String tag : taskTags) {
      if (tag.startsWith("-")) { //do not run on envs with that tag
        if (envTags.contains(tag.substring(1))) {
          return false;
        }
        necessaryTags.remove(tag);
      }
    }

    return necessaryTags.isEmpty();
  }

  public static boolean isJython(@NotNull String sdkHome) {
    return sdkHome.toLowerCase().contains("jython");
  }

  @NotNull
  private static String joinStrings(final Collection<String> roots, final String rootsName) {
    return !roots.isEmpty() ? rootsName + StringUtil.join(roots, ", ") + "\n" : "";
  }
}
