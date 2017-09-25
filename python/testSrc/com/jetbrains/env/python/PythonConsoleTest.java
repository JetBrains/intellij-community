package com.jetbrains.env.python;

import com.google.common.collect.Sets;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.Staging;
import com.jetbrains.env.python.console.PyConsoleTask;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static org.junit.Assert.assertTrue;

/**
 * @author traff
 */
public class PythonConsoleTest extends PyEnvTestCase {
  @Test
  public void testConsolePrint() {
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

  @Test
  public void testExecuteMultiline() {   //PY-4329
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("if True:\n" +
             "  x=1\n" +
             "y=x+100\n" +
             "for i in range(1):\n" +
             "  print(y)\n");
        waitForOutput("101");
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("-jython"); //jython doesn't support multiline execution: http://bugs.jython.org/issue2106
      }
    });
  }

  @Test
  @Staging
  public void testInterruptAsync() {
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

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("-iron", "-jython");
      }
    });
  }

  @Test
  public void testLineByLineInput() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("x = 96");
        exec("x +=1");
        exec("if True:\n" +
             "  print(x)\n");
        waitForOutput("97");
      }
    });
  }


  @Test
  public void testVariablesView() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("x = 1");
        exec("print(x)");
        waitForOutput("1");

        assertTrue("Variable has wrong value",
                   hasValue("x", "1"));
      }
    });
  }

  @Test
  public void testCompoundVariable() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("x = [1, 2, 3]");
        exec("print(x)");
        waitForOutput("[1, 2, 3]");

        List<String> values = getCompoundValueChildren(getValue("x"));
        Collections.sort(values);
        assertContainsElements(values, "1", "2", "3", "3");
      }
    });
  }

  @Test
  public void testChangeVariable() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("x = 1");
        exec("print(x)");
        waitForOutput("1");

        setValue("x", "2");

        exec("print(x)");

        waitForOutput("2");
      }
    });
  }
}
