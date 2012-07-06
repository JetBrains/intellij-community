package com.jetbrains.env.python.debug;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public abstract class PyEnvTestCase extends UsefulTestCase {
  private static final Logger LOG = Logger.getInstance(PyEnvTestCase.class.getName());

  private static final String TAGS_FILE = "tags.txt";
  public static final String PYCHARM_PYTHON_ENVS = "PYCHARM_PYTHON_ENVS";
  private static final String PYCHARM_PYTHON_VIRTUAL_ENVS = "PYCHARM_PYTHON_VIRTUAL_ENVS";

  public static final boolean IS_UNDER_TEAMCITY = System.getProperty("bootstrap.testcases") != null;

  protected static final boolean IS_ENV_CONFIGURATION = System.getProperty("pycharm.env") != null;


  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public PyEnvTestCase() {
    PyTestCase.initPlatformPrefix();
  }

  @Override
  protected void invokeTestRunnable(final Runnable runnable) throws Exception {
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

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  protected boolean runInWriteAction() {
    return false;
  }

  public void runPythonTest(PyTestTask testTask) {
    runTest(testTask);
  }

  private void runTest(@NotNull PyTestTask testTask) {
    if (notEnvConfiguration()) {
      fail("Running under teamcity but not by Env configuration. Skipping.");
      return;
    }

    List<String> roots = getPythonRoots();

    if (roots.size() == 0) {
      String msg = getTestName(false) +
                   ": environments are not defined. Skipping. \nSpecify either " +
                   PYCHARM_PYTHON_ENVS +
                   " or " +
                   PYCHARM_PYTHON_VIRTUAL_ENVS +
                   " environment variable.";
      LOG.warn(msg);
      System.out.println(msg);
      return;
    }

    boolean wasExecuted = false;

    List<String> passedRoots = Lists.newArrayList();

    for (String env : roots) {

      if (!isSuitableForTask(loadEnvTags(env), testTask)) {
        continue;
      }

      try {
        testTask.setUp();
        wasExecuted = true;
        if (isJython(env)) {
          testTask.useLongTimeout();
        }
        else {
          testTask.useNormalTimeout();
        }
        final String executable = PythonSdkType.getPythonExecutable(env);
        if (executable == null) {
          throw new RuntimeException("Cannot find Python interpreter in " + env);
        }
        testTask.runTestOn(executable);
        passedRoots.add(env);
      }
      catch (Throwable e) {
        throw new RuntimeException(joinStrings(passedRoots, "Tests passed environments: ") + "Test failed on environment " + env, e);
      }
      finally {
        try {
          testTask.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException("Couldn't tear down task", e);
        }
      }
    }

    if (!wasExecuted) {
      throw new RuntimeException("test" +
                                 getTestName(false) +
                                 " was not executed.\n" +
                                 joinStrings(roots, "All roots: ") +
                                 "\n" +
                                 joinStrings(testTask.getTags(), "Required tags in tags.txt in root: "));
    }
  }

  public static boolean notEnvConfiguration() {
    return IS_UNDER_TEAMCITY && !IS_ENV_CONFIGURATION;
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
      for (File f : virtualEnvRoot.listFiles()) {
        result.add(f.getAbsolutePath());
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
      envTags = com.jetbrains.appengine.util.FileUtil.loadLines(new File(parent, TAGS_FILE));
    }
    catch (IOException e) {
      envTags = Lists.newArrayList();
    }
    return envTags;
  }

  private static boolean isSuitableForTask(List<String> tags, PyTestTask task) {
    return isSuitableForTags(tags, task.getTags());
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

  public static String joinStrings(Collection<String> roots, String rootsName) {
    return roots.size() > 0 ? rootsName + StringUtil.join(roots, ", ") + "\n" : "";
  }

  private static boolean isJython(String sdkHome) {
    return sdkHome.toLowerCase().contains("jython");
  }
}

