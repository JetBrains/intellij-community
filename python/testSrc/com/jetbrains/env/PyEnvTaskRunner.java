// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env;

import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.python.test.env.common.PredefinedPyEnvironments;
import com.intellij.python.test.env.core.PyEnvironmentFactory;
import com.intellij.python.test.env.core.PythonVersionKt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.LoggingRule;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class PyEnvTaskRunner {
  private static final Logger LOG = Logger.getInstance(PyEnvTaskRunner.class);
  @NotNull private final PyEnvironmentFactory myFactory;
  private final @Nullable String myPythonVersionFilter;
  @Nullable
  private final LoggingRule myLoggingRule;

  /**
   * @param factory             factory creates test environment instances
   * @param pythonVersionFilter pythonVersionFilter
   * @param loggingRule         to be passed by {@link PyEnvTestCase}.
   *                            {@link LoggingRule#startLogging(Disposable, Iterable)} will be called
   *                            if task has {@link PyExecutionFixtureTestTask#getClassesToEnableDebug()}
   */
  public PyEnvTaskRunner(@NotNull PyEnvironmentFactory factory,
                         @Nullable String pythonVersionFilter,
                         @Nullable final LoggingRule loggingRule) {
    myFactory = factory;
    myPythonVersionFilter = pythonVersionFilter;
    myLoggingRule = loggingRule;
  }

  /**
   * Runs test on all interpreters.
   *
   * @param skipOnFlavors      optional array of interpreter flavors to skip
   * @param tagsRequiredByTest optional array of tags to run tests on interpreters with these tags only
   */
  public void runTask(@NotNull final PyTestTask testTask,
                      @NotNull final String testName,
                      final Class<? extends PythonSdkFlavor> @Nullable [] skipOnFlavors,
                      final String @NotNull ... tagsRequiredByTest) {
    boolean wasExecuted = false;

    List<String> passedEnvironments = new ArrayList<>();

    final Set<String> requiredTags = Sets.union(testTask.getTags(), Sets.newHashSet(tagsRequiredByTest));

    // Get all environments from strategy and filter them
    List<? extends PyTestEnvironment> allEnvironments = getAllEnvironments();
    List<PyTestEnvironment> environments = getMinimalSetOfTestEnvs(allEnvironments, requiredTags);

    if (environments.isEmpty()) {
      LOG.warn("No matching environments found for tags: " + requiredTags);
      return;
    }

    LOG.warn(String.format("Found %d matching environments", environments.size()));

    for (PyTestEnvironment environment : environments) {
      final String envDescription = environment.getDescription();

      LOG.warn(String.format("Running on environment: %s", envDescription));

      try {
        testTask.setUp(testName);
        wasExecuted = true;

        // Prepare SDK for this environment
        final Sdk sdk = environment.prepareSdk();

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
              // Will be disabled automatically on project disposing.
              myLoggingRule.startLogging(execTask.getProject(), execTask.getClassesToEnableDebug());
            }
          }

          testTask.runTestOn(sdk.getHomePath(), sdk);

          passedEnvironments.add(envDescription);
        }
        else {
          LOG.warn(String.format("Skipping environment %s (language level %s not supported)", envDescription, languageLevel));
        }
      }
      catch (final RuntimeException | Error ex) {
        // Runtime and error are logged including environment info
        LOG.warn(formatCollectionToString(passedEnvironments, "Tests passed environments") +
                 "Test failed on environment " + envDescription);
        throw ex;
      }
      catch (final Exception e) {
        throw new PyEnvWrappingException(e);
      }
      finally {
        // Clean up environment resources
        try {
          environment.close();
        }
        catch (final Exception e) {
          LOG.error("Failed to close environment: " + envDescription, e);
        }

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
      LOG.warn("test " +
               testName +
               " was not executed.\n" +
               "Required tags: " + requiredTags);
    }
  }

  private List<? extends PyTestEnvironment> getAllEnvironments() {
    return PyEnvTestCase.ALL_ENVIRONMENTS
      .stream()
      .map(e -> new ProviderTestEnvironment(myFactory, e.getSpec(), PredefinedPyEnvironments.Companion.getENVIRONMENTS_TO_TAGS().get(e)))
      .filter(e -> myPythonVersionFilter == null || PythonVersionKt.matches(e.getPythonVersion(), myPythonVersionFilter))
      .toList();
  }

  /**
   * Wrapper for PyTestEnvironment that provides ordering and filtering logic.
   * <p>
   * Instances of this class are ordered by the number of tags they provide. Typically, fewer tags
   * mean fewer packages installed inside the environment. If two environments have the same Python version
   * and both are suitable for a test, we prefer the one with fewer numbers of packages
   * since it can improve performance when code inside is involved.
   */
  private static class EnvInfo implements Comparable<EnvInfo> {
    @NotNull final PyTestEnvironment environment;
    @NotNull final List<String> tags;
    @Nullable final String pythonVersion;

    EnvInfo(@NotNull PyTestEnvironment environment) {
      this.environment = environment;
      this.tags = new ArrayList<>(environment.getTags());
      this.pythonVersion = ContainerUtil.find(tags, tag -> tag.matches("^python\\d\\.\\d+$"));
    }

    @Override
    public int compareTo(@NotNull PyEnvTaskRunner.EnvInfo o) {
      return Integer.compare(tags.size(), o.tags.size());
    }
  }

  private static @NotNull List<PyTestEnvironment> getMinimalSetOfTestEnvs(@NotNull List<? extends PyTestEnvironment> allEnvironments,
                                                                          @NotNull Set<String> requiredTags) {
    var pq = new PriorityQueue<>(ContainerUtil.map(allEnvironments, env -> new EnvInfo(env)));
    var addedPythonVersions = new HashSet<String>();
    var result = new ArrayList<PyTestEnvironment>();

    while (!pq.isEmpty()) {
      var envInfo = pq.poll();
      if (isSuitableForTags(envInfo.tags, requiredTags)) {
        if (envInfo.pythonVersion == null) {
          result.add(envInfo.environment);
        }
        else {
          if (!addedPythonVersions.contains(envInfo.pythonVersion)) {
            addedPythonVersions.add(envInfo.pythonVersion);
            result.add(envInfo.environment);
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

  @NotNull
  private static String formatCollectionToString(Collection<String> coll, String collName) {
    return !coll.isEmpty() ? collName + ": " + StringUtil.join(coll, ", ") + "\n" : "";
  }
}
