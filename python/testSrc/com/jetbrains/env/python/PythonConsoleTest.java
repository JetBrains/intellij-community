package com.jetbrains.env.python;

import com.google.common.collect.Sets;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.python.console.PyConsoleTask;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collections;
import java.util.List;
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

      @NotNull
      @Override
      public Set<String> getTags() {
        return Sets.newHashSet("-iron", "-jython");
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
        exec("  print(x)");
        exec("");
        exec("");
        waitForOutput("97");
      }
    });
  }


  public void testVariablesView() throws Exception {
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

  public void testCompoundVariable() throws Exception {
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

  public void testChangeVariable() throws Exception {
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
