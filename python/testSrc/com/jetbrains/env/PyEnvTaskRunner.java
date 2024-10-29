// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env;

import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.LoggingRule;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.VirtualEnvReader;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.tools.sdkTools.PySdkTools;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

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
   * @param skipOnFlavors optional array of interpreter flavors to skip
   *
   * @param tagsRequiredByTest optional array of tags to run tests on interpreters with these tags only
   */
  public void runTask(@NotNull final PyTestTask testTask,
                      @NotNull final String testName,
                      final Class<? extends PythonSdkFlavor> @Nullable [] skipOnFlavors,
                      final String @NotNull ... tagsRequiredByTest) {
    boolean wasExecuted = false;

    List<String> passedRoots = new ArrayList<>();

    final Set<String> requiredTags = Sets.union(testTask.getTags(), Sets.newHashSet(tagsRequiredByTest));

    for (String root : getMinimalSetOfTestEnvs(requiredTags)) {
      final boolean shouldRun = shouldRun(root, testTask);
      if (!shouldRun) {
        LOG.warn(String.format("Skipping %s (should run: %s)", root, shouldRun));
        continue;
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
        final Path executable = VirtualEnvReader.getInstance().findPythonInPythonRoot(Path.of(root));
        assert executable != null : "No executable in " + root;

        final Sdk sdk = getSdk(executable.toString(), testTask);
        if (skipOnFlavors != null) {
          final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
          if (ContainerUtil.exists(skipOnFlavors, o -> o.isInstance(flavor))) {
            LOG.warn("Skipping flavor " + flavor.toString());
            continue;
          }
        }

        /*
          Skipping test if {@link PyTestTask} reports it does not support this language level
         */
        final LanguageLevel languageLevel = PySdkUtil.getLanguageLevelForSdk(sdk);
        if (testTask.isLanguageLevelSupported(languageLevel)) {

          if (myLoggingRule != null) {
            final PyExecutionFixtureTestTask execTask = ObjectUtils.tryCast(testTask, PyExecutionFixtureTestTask.class);
            if (execTask != null) {
              // Fill be disabled automatically on project disposing.
              myLoggingRule.startLogging(execTask.getProject(), execTask.getClassesToEnableDebug());
            }
          }


          testTask.runTestOn(executable.toString(), sdk);

          passedRoots.add(root);
        }
        else {
          LOG.warn(String.format("Skipping root %s", root));
        }
      }
      catch (final RuntimeException | Error ex) {
        // Runtime and error are logged including environment info
        LOG.warn(formatCollectionToString(passedRoots, "Tests passed environments") +
                 "Test failed on " +
                 getEnvType() +
                 " environment " +
                 root);
        throw ex;
      }
      catch (final Exception e) {
        throw new PyEnvWrappingException(e);
      }
      finally {
        try {
          // Teardown should be called on the main thread because fixture teardown checks for
          // thread leaks, and blocked main thread is considered as leaked.
          testTask.tearDown();
        }
        catch (final Exception e) {
          throw new RuntimeException("Couldn't tear down task", e);
        }
      }
    }

    if (!wasExecuted) {
      throw new RuntimeException("test " +
                                 testName +
                                 " was not executed.\n" +
                                 formatCollectionToString(myRoots, "All roots") +
                                 "\n" +
                                 formatCollectionToString(requiredTags, "Required tags in tags.txt in root"));
    }
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
      final VirtualFile url = VfsUtil.findFileByIoFile(new File(executable), true);
      assert url != null : "No file " + executable;
      return PySdkTools.createTempSdk(url, SdkCreationType.EMPTY_SDK, null, null);
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  protected String getEnvType() {
    return "local";
  }

  /**
   * Given the path to a test environment root folder, collects and provides information about this environment.
   * <p>
   *  Instances of this class are ordered by the number of tags they provide. Typically, fewer tags
   *  mean fewer packages installed inside the environment. If two environments have the same Python version
   *  and both are suitable for a test, we prefer the one with fewer numbers of packages
   *  since it can improve performance when code inside is involved.
   */
  private static class EnvInfo implements Comparable<EnvInfo> {
    @NotNull final String root;
    @NotNull final List<String> tags;
    @Nullable final String pythonVersion;

    EnvInfo(@NotNull String root) {
      this.root = root;
      tags = PyEnvTestCase.loadEnvTags(root);
      pythonVersion = ContainerUtil.find(tags, tag -> tag.matches("^python\\d\\.\\d+$"));
    }

    @Override
    public int compareTo(@NotNull PyEnvTaskRunner.EnvInfo o) {
      return Integer.compare(tags.size(), o.tags.size());
    }
  }

  private @NotNull List<String> getMinimalSetOfTestEnvs(@NotNull Set<String> requiredTags) {
    var pq = new PriorityQueue<>(ContainerUtil.map(myRoots, root -> new EnvInfo(root)));
    var addedPythonVersions = new HashSet<String>();
    var result = new ArrayList<String>();
    while (!pq.isEmpty()) {
      var envInfo = pq.poll();
      if (isSuitableForTags(envInfo.tags, requiredTags)) {
        if (envInfo.pythonVersion == null) {
          result.add(envInfo.root);
        }
        else {
          if (!addedPythonVersions.contains(envInfo.pythonVersion)) {
            addedPythonVersions.add(envInfo.pythonVersion);
            result.add(envInfo.root);
          }
        }
      }
    }
    return result;
  }

  public static boolean isSuitableForTags(@NotNull List<String> envTags, Set<String> taskTags) {
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
    return sdkHome.toLowerCase(Locale.ROOT).contains("jython");
  }

  @NotNull
  private static String formatCollectionToString(Collection<String> coll, String collName) {
    return !coll.isEmpty() ? collName + ": " + StringUtil.join(coll, ", ") + "\n" : "";
  }
}
