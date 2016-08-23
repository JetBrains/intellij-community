package com.jetbrains.env;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.TestEnv;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author traff
 */
public abstract class PyEnvTestCase {
  private static final Logger LOG = Logger.getInstance(PyEnvTestCase.class.getName());

  private static final String TAGS_FILE = "tags.txt";
  private static final String PYCHARM_PYTHON_ENVS = "PYCHARM_PYTHON_ENVS";
  private static final String PYCHARM_PYTHON_VIRTUAL_ENVS = "PYCHARM_PYTHON_VIRTUAL_ENVS";

  protected static final boolean IS_ENV_CONFIGURATION = System.getProperty("pycharm.env") != null;


  public static final boolean RUN_REMOTE = SystemProperties.getBooleanProperty("pycharm.run_remote", false);

  public static final boolean RUN_LOCAL = SystemProperties.getBooleanProperty("pycharm.run_local", true);

  private static final boolean STAGING_ENV = SystemProperties.getBooleanProperty("pycharm.staging_env", false);


  /**
   * Logger to be used with {@link #startMessagesCapture()}
   */
  private PyTestMessagesLogger myLogger;

  /**
   * Tags that should exist between all tags, available on all interpreters for test to run.
   * See {@link #PyEnvTestCase(String...)}
   */
  @Nullable
  private final String[] myRequiredTags;


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

  /**
   * Escape test output to prevent python test be processed as test result
   */
  public static String escapeTestMessage(@NotNull final String message) {
    return message.replace("##", "from test: \\[sharp][sharp]");
  }

  protected boolean isStaging(Description description) {
    try {
      if (description.getTestClass().getMethod(description.getMethodName()).isAnnotationPresent(Staging.class)) {
        return true;
      }
      else {
        for (StagingOn so : description.getTestClass().getMethod(description.getMethodName()).getAnnotationsByType(StagingOn.class)) {
          if (so.os() == TestEnv.WINDOWS && SystemInfo.isWindows ||
              so.os() == TestEnv.LINUX && SystemInfo.isLinux ||
              so.os() == TestEnv.MAC && SystemInfo.isMac) {
            return true;
          }
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
  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected PyEnvTestCase(@NotNull final String... requiredTags) {
    myRequiredTags = requiredTags.length > 0 ? requiredTags.clone() : null;
  }

  @Nullable
  public static PyPackage getInstalledDjango(@NotNull final Sdk sdk) throws ExecutionException {
    return PyPackageUtil.findPackage(PyPackageManager.getInstance(sdk).refreshAndGetPackages(false), "django");
  }

  public static String norm(String testDataPath) {
    return FileUtil.toSystemIndependentName(testDataPath);
  }

  @Before
  public void setUp() throws Exception {
    if (myRequiredTags != null) { // Ensure all tags exist between available interpreters
      Assume.assumeThat(
        "Can't find some tags between all available interpreter, test (all methods) will be skipped",
        getAvailableTags(),
        Matchers.hasItems(myRequiredTags)
      );
    }
  }


  /**
   * @return all tags available between all interpreters
   */
  @NotNull
  private static Collection<String> getAvailableTags() {
    final Collection<String> allAvailableTags = new HashSet<>();
    for (final String pythonRoot : getPythonRoots()) {
      allAvailableTags.addAll(loadEnvTags(pythonRoot));
    }
    return allAvailableTags;
  }

  protected void invokeTestRunnable(@NotNull final Runnable runnable) throws Exception {
    if (runInWriteAction()) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
      });
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

  public void runPythonTest(final PyTestTask testTask) {
    runTest(testTask, getTestName(false));
  }

  protected String getTestName(boolean lowercaseFirstLetter) {
    return UsefulTestCase.getTestName(myTestName.getMethodName(), lowercaseFirstLetter);
  }

  public void runTest(@NotNull PyTestTask testTask, @NotNull String testName) {
    if (notEnvConfiguration()) {
      Assert.fail("Running under teamcity but not by Env configuration. Skipping.");
      return;
    }

    checkStaging();

    List<String> roots = getPythonRoots();

    /**
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
                       ": environments are not defined. Skipping. \nSpecify either " +
                       PYCHARM_PYTHON_ENVS +
                       " or " +
                       PYCHARM_PYTHON_VIRTUAL_ENVS +
                       " environment variable.",
                       roots.isEmpty());
    doRunTests(testTask, testName, roots);
  }

  protected void checkStaging() {
    Assume.assumeTrue("Test is annotated as Staging and should only run on staging environment",
                      myStaging == STAGING_ENV);
  }

  protected void doRunTests(PyTestTask testTask, String testName, List<String> roots) {
    if (RUN_LOCAL) {
      PyEnvTaskRunner taskRunner = new PyEnvTaskRunner(roots);

      final EnvTestTagsRequired tagsRequiredAnnotation = getClass().getAnnotation(EnvTestTagsRequired.class);
      final String[] requiredTags;
      if (tagsRequiredAnnotation != null) {
        requiredTags = tagsRequiredAnnotation.tags();
      }
      else {
        requiredTags = ArrayUtil.EMPTY_STRING_ARRAY;
      }

      taskRunner.runTask(testTask, testName, requiredTags);
    }
  }

  public static boolean notEnvConfiguration() {
    return UsefulTestCase.IS_UNDER_TEAMCITY && !IS_ENV_CONFIGURATION;
  }

  public static List<String> getPythonRoots() {
    List<String> roots = Lists.newArrayList();

    String envs = System.getenv(PYCHARM_PYTHON_ENVS);
    if (envs != null) {
      roots.addAll(Lists.newArrayList(envs.split(File.pathSeparator)));
    }

    String virtualEnvs = System.getenv(PYCHARM_PYTHON_VIRTUAL_ENVS);

    if (virtualEnvs != null) {
      roots.addAll(readVirtualEnvRoots(virtualEnvs));
    }
    return roots;
  }

  protected static List<String> readVirtualEnvRoots(@NotNull String envs) {
    List<String> result = Lists.newArrayList();
    String[] roots = envs.split(File.pathSeparator);
    for (String root : roots) {
      File virtualEnvRoot = new File(root);
      File[] virtualenvs = virtualEnvRoot.listFiles();
      if (virtualenvs != null) {
        for (File f : virtualenvs) {
          result.add(f.getAbsolutePath());
        }
      }
      else {
        LOG.error(root + " is not a directory of doesn't exist");
      }
    }

    return result;
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
      envTags = Lists.newArrayList();
    }
    return envTags;
  }

  public static String joinStrings(Collection<String> roots, String rootsName) {
    return roots.size() > 0 ? rootsName + StringUtil.join(roots, ", ") + "\n" : "";
  }

  /**
   * Capture all messages and error logs and store them to be obtained with {@link #getCapturesMessages()}
   * and stopped with {@link #stopMessageCapture()}
   */
  protected final void startMessagesCapture() {
    myLogger = new PyTestMessagesLogger();
    LoggedErrorProcessor.setNewInstance(myLogger);
    Messages.setTestDialog(myLogger);
  }

  /**
   * @return captures messages (first start with {@link #startMessagesCapture()}).
   * Logged exceptions -- list of messages to be displayed (never null)
   */
  @NotNull
  protected final Pair<List<Throwable>, List<String>> getCapturesMessages() {
    assert myLogger != null : "Capturing not enabled";
    return Pair.create(Collections.unmodifiableList(myLogger.myExceptions), Collections.unmodifiableList(myLogger.myMessages));
  }

  /**
   * Stop message capturing started with {@link #startMessagesCapture()}
   */
  protected final void stopMessageCapture() {
    LoggedErrorProcessor.restoreDefaultProcessor();
    Messages.setTestDialog(TestDialog.DEFAULT);
    myLogger = null;
  }

  /**
   * Always call parrent when overwrite
   */
  @After
  public void tearDown() throws Exception {
    // We can stop message capturing even if it was not started as cleanup process.
    stopMessageCapture();
  }

  /**
   * Logger to be used with {@link #startMessagesCapture()}
   */
  private static final class PyTestMessagesLogger extends LoggedErrorProcessor implements TestDialog {

    private final List<String> myMessages = new ArrayList<>();
    private final List<Throwable> myExceptions = new ArrayList<>();

    @Override
    public int show(final String message) {
      myMessages.add(message);
      return 0;
    }

    @Override
    public void processWarn(final String message, final Throwable t, @NotNull final org.apache.log4j.Logger logger) {
      if (t != null) {
        myExceptions.add(t);
      }
    }

    @Override
    public void processError(final String message,
                             final Throwable t,
                             final String[] details,
                             @NotNull final org.apache.log4j.Logger logger) {
      if (t != null) {
        myExceptions.add(t);
      }
    }
  }
}

