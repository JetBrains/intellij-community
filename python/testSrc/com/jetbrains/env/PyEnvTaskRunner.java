/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.env;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.tools.sdkTools.PySdkTools;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PyEnvTaskRunner {
  private static final Logger LOG = Logger.getInstance(PyEnvTaskRunner.class);
  private final List<String> myRoots;

  public PyEnvTaskRunner(List<String> roots) {
    myRoots = roots;
  }

  // todo: doc
  public void runTask(PyTestTask testTask, String testName, @NotNull final String... tagsRequiedByTest) {
    boolean wasExecuted = false;

    List<String> passedRoots = Lists.newArrayList();

    final Set<String> requiredTags = Sets.union(testTask.getTags(), Sets.newHashSet(tagsRequiedByTest));

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
        final String executable = getExecutable(root, testTask);
        if (executable == null) {
          throw new RuntimeException("Cannot find Python interpreter in " + root);
        }
        final Sdk sdk = createSdkByExecutable(executable);

        /*
          Skipping test if {@link PyTestTask} reports it does not support this language level
         */
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        if (testTask.isLanguageLevelSupported(languageLevel)) {
          testTask.runTestOn(executable);
          passedRoots.add(root);
        }
        else {
          LOG.warn(String.format("Skipping root %s", root));
        }
      }
      catch (final Throwable e) {
        // Direct output of enteredTheMatrix may break idea or TC since can't distinguish test output from real test result
        // Exception is thrown anyway, so we escape message before logging
        if (e.getMessage().contains("enteredTheMatrix")) {
          // .error( may lead to new exception with out of stacktrace.
          LOG.warn(PyEnvTestCase.escapeTestMessage(e.getMessage()));
        }
        else {
          LOG.error(e);
        }
        throw new RuntimeException(
          PyEnvTestCase.joinStrings(passedRoots, "Tests passed environments: ") + "Test failed on " + getEnvType() + " environment " + root,
          e);
      }
      finally {
        try {
          // Teardown should be called on main thread because fixture teardown checks for
          // thread leaks, and blocked main thread is considered as leaked
          testTask.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException("Couldn't tear down task", e);
        }
      }
    }

    if (!wasExecuted) {
      throw new RuntimeException("test" +
                                 testName +
                                 " was not executed.\n" +
                                 PyEnvTestCase.joinStrings(myRoots, "All roots: ") +
                                 "\n" +
                                 PyEnvTestCase.joinStrings(testTask.getTags(), "Required tags in tags.txt in root: "));
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

  /**
   * Create SDK by path to python exectuable
   *
   * @param executable path executable
   * @return sdk or null if there is no sdk on this path
   * @throws InvalidSdkException bad sdk
   */
  @Nullable
  private static Sdk createSdkByExecutable(@NotNull final String executable) throws InvalidSdkException, IOException {
    final URL rootUrl = new URL(String.format("file:///%s", executable));
    final VirtualFile url = VfsUtil.findFileByURL(rootUrl);
    if (url == null) {
      return null;
    }
    return PySdkTools.createTempSdk(url, SdkCreationType.EMPTY_SDK, null);
  }

  protected boolean shouldRun(String root, PyTestTask task) {
    return true;
  }

  protected String getExecutable(String root, PyTestTask testTask) {
    return PythonSdkType.getPythonExecutable(root);
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
}
