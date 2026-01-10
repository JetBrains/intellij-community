// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.python.test.env.common.PredefinedPyEnvironments;
import com.intellij.python.test.env.junit4.JUnit4FactoryHolder;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.LoggingRule;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.python.test.env.common.PredefinedPyEnvironments.*;
import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 * <p>
 * All inheritors must be in {@link com.jetbrains.env}.*
 * <p>
 * See "community/python/setup-test-environment/build.gradle"
 * {@link com.jetbrains.env.python.api.EnvTagsKt#loadEnvTags(Path)}
 *
 * @author traff
 */
public abstract class PyEnvTestCase {
  private static final Logger LOG = Logger.getInstance(PyEnvTestCase.class.getName());

  @NotNull
  protected static final PyEnvTestSettings SETTINGS = PyEnvTestSettings.Companion.fromEnvVariables();

  /**
   * Rule used to capture debug logging and display it if test failed.
   * See also {@link PyExecutionFixtureTestTask#getClassesToEnableDebug()} and
   * {@link PyEnvTaskRunner}
   */
  @Rule
  public LoggingRule myLoggingRule = new LoggingRule();

  /**
   * Tags that should exist between all tags, available on all interpreters for test to run.
   * See {@link #PyEnvTestCase(String...)}
   */
  private final String @Nullable [] myRequiredTags;

  /**
   * TODO: Move to {@link EnvTestTagsRequired} as well?
   */

  @Rule public TestName myTestName = new TestName();

  @Rule public final TestWatcher myWatcher = new TestWatcher() {
  };

  static {
    LOG.info("Using following config\n" + SETTINGS.reportConfiguration());
  }

  /**
   * All predefined environments used by PyEnvTestCase by default
   */
  public static final List<PredefinedPyEnvironments> ALL_ENVIRONMENTS = List.of(
    VENV_2_7,
    VENV_3_8_FULL,
    VENV_3_9,
    VENV_3_10,
    VENV_3_11,
    VENV_3_12,
    VENV_3_12_DJANGO,
    VENV_3_13,
    VENV_3_14
  );


  /**
   * Escape test output to prevent python test be processed as test result
   */
  @NotNull
  public static String escapeTestMessage(@NotNull final String message) {
    return message.replace("##teamcity", "from test: \\[sharp][sharp]");
  }

  /**
   * @param requiredTags tags that should exist on some interpreter for this test to run.
   *                     if some of these tags do not exist on any interpreter, all test methods would be skipped using
   *                     {@link org.junit.Assume}.
   *                     See <a href="http://junit.sourceforge.net/javadoc/org/junit/Assume.html">Assume manual</a>.
   *                     Check [IDEA-122939] and [TW-25043] as well.
   */
  protected PyEnvTestCase(final String @NotNull ... requiredTags) {
    myRequiredTags = requiredTags.length > 0 ? requiredTags.clone() : null;
    TestApplicationManager.getInstance(); // init app explicitly
  }

  public static String norm(String testDataPath) {
    return FileUtil.toSystemIndependentName(testDataPath);
  }

  @Before
  public void before() {
    if (myRequiredTags != null) { // Ensure all tags exist between available interpreters
      assertThat(getAvailableTags())
        .describedAs("Can't find some tags between all available interpreter, test (all methods) will be skipped")
        .contains(myRequiredTags);
    }
  }


  /**
   * @return all tags available between all interpreters
   */
  @NotNull
  private static Collection<String> getAvailableTags() {
    final Collection<String> allAvailableTags = new HashSet<>();
    for (@NotNull Set<@NotNull String> tags : Companion.getENVIRONMENTS_TO_TAGS().values()) {
      allAvailableTags.addAll(tags);
    }
    return allAvailableTags;
  }

  /**
   * Runs task on several envs. If you care about exception thrown from task use {@link #runPythonTestWithException(PyTestTask)}
   */
  public void runPythonTest(final PyTestTask testTask) {
    runTest(testTask, getTestName(false));
  }

  /**
   * Like {@link #runPythonTest(PyTestTask)} but for tasks that may throw exception
   */
  protected final void runPythonTestWithException(final PyTestTask testTask) throws Exception {
    try {
      runPythonTest(testTask);
    }
    catch (final PyEnvWrappingException ex) {
      throw ex.getCauseException();
    }
  }

  protected String getTestName(boolean lowercaseFirstLetter) {
    return UsefulTestCase.getTestName(myTestName.getMethodName(), lowercaseFirstLetter);
  }

  private void runTest(@NotNull PyTestTask testTask, @NotNull String testName) {
    Assume.assumeFalse("Running under teamcity but not by Env configuration. Test seems to be launched by accident, skip it.",
                       UsefulTestCase.IS_UNDER_TEAMCITY && !SETTINGS.isEnvConfiguration());
    doRunTests(testTask, testName);
  }

  protected void doRunTests(PyTestTask testTask, String testName) {
    Assume.assumeFalse("Tests launched in remote SDK mode, and this test is not remote", SETTINGS.useRemoteSdk());

    PyEnvTaskRunner taskRunner = new PyEnvTaskRunner(JUnit4FactoryHolder.INSTANCE.getOrCreate(), SETTINGS.getPythonVersion(), myLoggingRule);

    final EnvTestTagsRequired classAnnotation = getClass().getAnnotation(EnvTestTagsRequired.class);
    EnvTestTagsRequired methodAnnotation;
    try {
      String methodName = myTestName.getMethodName();
      if (methodName.contains("[")) {
        methodName = methodName.substring(0, methodName.indexOf('['));
      }
      final Method method = getClass().getMethod(methodName);
      methodAnnotation = method.getAnnotation(EnvTestTagsRequired.class);
    }
    catch (final NoSuchMethodException e) {
      throw new AssertionError("No such method", e);
    }
    final Class<? extends PythonSdkFlavor>[] skipOnFlavors;


    final EnvTestTagsRequired firstAnnotation = (methodAnnotation != null ? methodAnnotation : classAnnotation);


    if (firstAnnotation != null) {
      Assume.assumeFalse("Test skipped on this os", ContainerUtil.exists(firstAnnotation.skipOnOSes(), TestEnv::isThisOs));
      skipOnFlavors = firstAnnotation.skipOnFlavors();
    }
    else {
      skipOnFlavors = null;
    }

    final String[] classTags = getTags(classAnnotation);
    final String[] methodTags = getTags(methodAnnotation);

    taskRunner.runTask(testTask, testName, skipOnFlavors, ArrayUtil.mergeArrays(methodTags, classTags));
  }

  private static String @NotNull [] getTags(@Nullable final EnvTestTagsRequired tagsRequiredAnnotation) {
    if (tagsRequiredAnnotation != null) {
      return tagsRequiredAnnotation.tags();
    }
    else {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
  }

  private final Disposable myDisposable = Disposer.newDisposable();

  public Disposable getTestRootDisposable() {
    return myDisposable;
  }

  /**
   * Always call parent when overriding.
   */
  @After
  public void after() {
    Disposer.dispose(myDisposable);
  }
}

