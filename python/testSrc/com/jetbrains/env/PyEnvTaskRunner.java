package com.jetbrains.env;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PyEnvTaskRunner {
  private final List<String> myRoots;

  public PyEnvTaskRunner(List<String> roots) {
    myRoots = roots;
  }

  public void runTask(PyTestTask testTask, String testName) {
    boolean wasExecuted = false;

    List<String> passedRoots = Lists.newArrayList();

    for (String root : myRoots) {

      if (!isSuitableForTask(PyEnvTestCase.loadEnvTags(root), testTask) || !shouldRun(root, testTask)) {
        continue;
      }

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
        testTask.runTestOn(executable);
        passedRoots.add(root);
      }
      catch (Throwable e) {
        throw new RuntimeException(
          PyEnvTestCase.joinStrings(passedRoots, "Tests passed environments: ") + "Test failed on " + getEnvType() + " environment " + root,
          e);
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
                                 testName +
                                 " was not executed.\n" +
                                 PyEnvTestCase.joinStrings(myRoots, "All roots: ") +
                                 "\n" +
                                 PyEnvTestCase.joinStrings(testTask.getTags(), "Required tags in tags.txt in root: "));
    }
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


  public static boolean isJython(@NotNull String sdkHome) {
    return sdkHome.toLowerCase().contains("jython");
  }
}
