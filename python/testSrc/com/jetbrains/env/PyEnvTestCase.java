// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.LoggingRule;
import com.jetbrains.TestEnv;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 * <p>
 * All inheritors must be in {@link com.jetbrains.env}.*
 * <p>
 * See "community/python/setup-test-environment/build.gradle"
 *
 * @author traff
 */
public abstract class PyEnvTestCase {
  private static final Logger LOG = Logger.getInstance(PyEnvTestCase.class.getName());

  private static final String TAGS_FILE = "tags.txt";

  @NotNull
  protected static final PyEnvTestSettings SETTINGS = new PyEnvTestSettings();


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
   * Environments and tags they provide.
   */
  public static final Map<String, List<String>> envTags = new HashMap<>();

  private boolean myStaging = false;
  /**
   * TODO: Move to {@link EnvTestTagsRequired} as well?
   */

  @Rule public TestName myTestName = new TestName();

  @Rule public final TestWatcher myWatcher = new TestWatcher() {
    @Override
    protected void starting(Description description) {
      myStaging = isStaging(description);
    }
  };

  static {
    LOG.warn("Using following config\n" + SETTINGS.reportConfiguration());
  }

  /**
   * Escape test output to prevent python test be processed as test result
   */
  public static String escapeTestMessage(@NotNull final String message) {
    return message.replace("##", "from test: \\[sharp][sharp]");
  }

  protected boolean isStaging(Description description) {
    try {
      Class<?> aClass = description.getTestClass();
      if (aClass.isAnnotationPresent(Staging.class)) {
        return true;
      }
      if (aClass.getMethod(description.getMethodName()).isAnnotationPresent(Staging.class)) {
        return true;
      }
      else {
        final StagingOn[] methodAnnotations = aClass.getMethod(description.getMethodName()).getAnnotationsByType(StagingOn.class);
        final StagingOn[] classAnnotations = aClass.getAnnotationsByType(StagingOn.class);
        if (Arrays.stream(ArrayUtil.mergeArrays(methodAnnotations, classAnnotations)).map(StagingOn::os).anyMatch(TestEnv::isThisOs)) {
          return true;
        }
        return false;
      }
    }
    catch (NoSuchMethodException e) {
      return false;
    }
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
    for (List<String> tags : envTags.values()) {
      allAvailableTags.addAll(tags);
    }
    return allAvailableTags;
  }

  @BeforeClass
  public static void collectTagsForEnvs() {
    for (final String pythonRoot : getPythonRoots()) {
      envTags.put(pythonRoot, loadEnvTags(pythonRoot));
    }
  }

  protected void invokeTestRunnable(@NotNull final Runnable runnable) {
    if (runInWriteAction()) {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> ApplicationManager.getApplication().runWriteAction(runnable));
    }
    else {
      runnable.run();
    }
  }


  protected boolean runInDispatchThread() {
    return false;
  }

  protected boolean runInWriteAction() {
    return false;
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
    checkStaging();

    List<String> roots = getPythonRoots();

    /*
     * <p>
     * {@link org.junit.AssumptionViolatedException} here means this test must be <strong>skipped</strong>.
     * TeamCity supports this (if not you should create and issue about that).
     * Idea does not support it for JUnit 3, while JUnit 4 must be supported.
     * </p>
     *<p>
     * It this error brakes your test, please <strong>do not</strong> revert. Instead, do the following:
     * <ol>
     *   <li>Make sure {@link com.jetbrains.env.python} tests are <strong>excluded</strong> from your configuration (unless you are
     *   PyCharm developer)</li>
     *   <li>Check that your environment supports {@link AssumptionViolatedException}.
     *   JUnit 4 was created about 10 years ago, so fixing environment is much better approach than hacky "return;" here.
     *   </li>
     * </ol>
     *</p>
     */
    Assume.assumeFalse(testName +
                       ": environments are not defined. Skipping. \nChecks logs for settings that lead to this situation",
                       roots.isEmpty());

    doRunTests(testTask, testName, roots);
  }

  protected final void checkStaging() {
    if (!SETTINGS.isUnderTeamCity()) {
      return; // Its ok to run staging tests locally
    }
    Assume.assumeTrue("Test is annotated as Staging and should only run on staging environment",
                      myStaging == SETTINGS.isStagingMode());
  }

  protected void doRunTests(PyTestTask testTask, String testName, List<String> roots) {
    Assume.assumeFalse("Tests launched in remote SDK mode, and this test is not remote", SETTINGS.useRemoteSdk());

    PyEnvTaskRunner taskRunner = new PyEnvTaskRunner(roots, myLoggingRule);

    final EnvTestTagsRequired classAnnotation = getClass().getAnnotation(EnvTestTagsRequired.class);
    EnvTestTagsRequired methodAnnotation = null;
    try {
      final Method method = getClass().getMethod(myTestName.getMethodName());
      methodAnnotation = method.getAnnotation(EnvTestTagsRequired.class);
    }
    catch (final NoSuchMethodException e) {
      throw new AssertionError("No such method", e);
    }
    final Class<? extends PythonSdkFlavor>[] skipOnFlavors;


    final EnvTestTagsRequired firstAnnotation = (methodAnnotation != null ? methodAnnotation : classAnnotation);


    if (firstAnnotation != null) {
      Assume.assumeFalse("Test skipped on this os", Arrays.stream(firstAnnotation.skipOnOSes()).anyMatch(TestEnv::isThisOs));
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

  public static List<String> getPythonRoots() {
    return ContainerUtil.map(SETTINGS.getPythons(), File::getAbsolutePath);
  }


  public static List<String> loadEnvTags(String env) {
    List<String> envTags;

    try {
      File parent = new File(env);
      if (parent.isFile()) {
        parent = parent.getParentFile();
      }
      envTags = com.intellij.openapi.util.io.FileUtil.loadLines(new File(parent, TAGS_FILE));
    }
    catch (IOException e) {
      envTags = new ArrayList<>();
    }
    return envTags;
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

