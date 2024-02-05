package com.jetbrains.performancePlugin;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.jetbrains.performancePlugin.commands.RunClassInPlugin;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

public class RunClassInPluginCommandTest extends HeavyPlatformTestCase {

  public void test_execute_script_in_classpath1() throws Exception {
    doTest(TestClass1.class);
  }

  public void test_execute_script_in_classpath2() throws Exception {
    doTest(TestClass2.class);
  }

  private void doTest(@NotNull Class<?> test) throws Exception {
    System.setProperty(test.getName(), "not set");

    String line = RunClassInPlugin.PREFIX +
                  " com.intellij " +
                  test.getName() +
                  " method123 \"" +
                  PathManager.getJarPathForClass(test) +
                  "\"";

    new RunClassInPlugin(line, 123).computePromise(getProject());
    Assert.assertEquals("123", System.getProperty(test.getName()));
  }

  public static class TestClass1 {
    @SuppressWarnings("unused")
    public void method123(@NotNull Project project) {
      System.setProperty(getClass().getName(), "123");
    }
  }

  public static class TestClass2 {
    @SuppressWarnings("unused")
    public void method123() {
      System.setProperty(getClass().getName(), "123");
    }
  }

}
