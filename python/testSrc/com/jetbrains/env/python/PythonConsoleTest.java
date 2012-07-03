package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.env.python.console.PyConsoleTask;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import org.junit.Assert;

import java.util.Set;

/**
 * @author traff
 */
public class PythonConsoleTest extends PyEnvTestCase {
  public void testConsolePrint() throws Exception {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("x = 96");
        exec("x += 1");
        exec("print(1)");
        exec("print(x)");
        waitForOutput("97");
      }
    });
  }

  public void testExecuteMultiline() throws Exception {   //PY-4329
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("if True:\n" +
             "        x=1\n" +
             "y=x+100\n" +
             "for i in range(1):\n" +
             "  print(y)");
        waitForOutput("101");
      }
    });
  }

  public void testInterruptAsync() throws Exception {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("import time");
        execNoWait("for i in range(10000):\n" +
                   "  print(i)\n" +
                   "  time.sleep(0.1)");
        waitForOutput("3\n4\n5");
        Assert.assertFalse(canExecuteNow());
        interrupt();
        waitForFinish();
        waitForReady();
      }

      @Override
      public Set<String> getTags() {
        return new ImmutableSet.Builder<String>().addAll(super.getTags()).add("-iron").build();
      }
    });
  }

  public void testLineByLineInput() throws Exception {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("x = 96");
        exec("x +=1");
        exec("if True:");
        exec("");
        exec("  print(x)");
        exec("");
        waitForOutput("97");
      }
    });
  }
}
