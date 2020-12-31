// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.debug;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static com.jetbrains.env.debug.PyBaseDebuggerTask.findCompletionVariantByName;
import static com.jetbrains.env.debug.PyBaseDebuggerTask.findDebugValueByName;
import static com.jetbrains.python.PyParameterInfoTest.checkParameters;
import static org.junit.Assert.*;

public class PythonConsoleTest extends PyEnvTestCase {
  @Test
  public void testConsolePrint() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("x = 96");
        exec("x = x + 1");
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
             "  x = 1\n" +
             "y = x + 100\n" +
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
        exec("x = x + 1");
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

  @Test
  public void testConsoleActionsReflectedInVariableView() {
    runPythonTest(new PyConsoleTask() {

      private static final int TIMEOUT = 3000;
      private static final int NUMBER_OF_ATTEMPTS = 10;

      @Override
      public void testing() throws Exception {
        exec("foo = 'bar'");
        waitForConditionIsTrue("`foo` is not in the console variable tree", (root) -> {
          for(TreeNode child : root.getChildren()) {
            XDebuggerTreeNode node = (XDebuggerTreeNode) child;
            if (node.toString().equals("foo")) {
              return true;
            }
          }
          return false;
        });

        exec("foo = 'baz'");
        waitForConditionIsTrue("`foo` value hasn't changed to 'baz'", (root) -> {
          for(TreeNode child : root.getChildren()) {
            XDebuggerTreeNode node = (XDebuggerTreeNode) child;
            if (node.toString().equals("foo") && node.getText().toString().contains("'baz'")) {
              return true;
            }
          }
          return false;
        });

        exec("del foo");
        waitForConditionIsTrue("`foo` is still in the console variable tree", (root) -> {
          for (TreeNode child : root.getChildren()) {
            XDebuggerTreeNode node = (XDebuggerTreeNode)child;
            if (node.toString().equals("foo")) {
              return false;
            }
          }
          return true;
        });
      }

      private void waitForConditionIsTrue(String message, @NotNull Predicate<XDebuggerTreeNode> pred) throws InterruptedException {
        final long startedAt = System.currentTimeMillis();
        do {
          if (pred.test(getConsoleView().getDebuggerTreeRootNode())) return;
          Thread.sleep(TIMEOUT / NUMBER_OF_ATTEMPTS);
        }
        while (startedAt + TIMEOUT > System.currentTimeMillis());
        Assert.fail(message);
      }
    });
  }

  @Test
  public void testCollectionsShapes() {
    runPythonTest(new PyConsoleTask("/debug") {
      @Override
      public void testing() throws Exception {
        exec("from test_shapes import *");
        waitForOutput("Executed");

        final List<PyDebugValue> frameVariables = loadFrame();
        PyDebugValue var = findDebugValueByName(frameVariables, "list1");
        assertEquals("120", var.getShape());
        var = findDebugValueByName(frameVariables, "dict1");
        assertEquals("2", var.getShape());
        var = findDebugValueByName(frameVariables, "custom");
        assertEquals("5", var.getShape());
        var = findDebugValueByName(frameVariables, "df1");
        assertEquals("(3, 6)", var.getShape());
        var = findDebugValueByName(frameVariables, "n_array");
        assertEquals("(3, 2)", var.getShape());
        var = findDebugValueByName(frameVariables, "series");
        assertEquals("(5,)", var.getShape());

        var = findDebugValueByName(frameVariables, "custom_shape");
        assertEquals("(3,)", var.getShape());
        var = findDebugValueByName(frameVariables, "custom_shape2");
        assertEquals("(2, 3)", var.getShape());
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("pandas");
      }
    });
  }

  @Test
  public void testCompletionMethods() {
    runPythonTest(new PyConsoleTask("/debug") {
      @Override
      public void testing() throws Exception {
        exec("a = \"aaa\"");
        exec("a");
        waitForOutput("aaa");

        List<PydevCompletionVariant> completions = getCompletions("a.");
        assertFalse("Completion variants list is empty", completions.isEmpty());
        PydevCompletionVariant compVariant = findCompletionVariantByName(completions, "center");
        assertNotNull("Completion variant `center` is missing", compVariant);

        assertEquals(2, compVariant.getType());
        assertTrue("Missing completion argument `width` in `str.center()`", compVariant.getArgs().contains("width"));
        assertFalse("Documentation is empty for `str.center()`", compVariant.getDescription().isEmpty());
      }
    });
  }

  @Test
  public void testCompletionDoNotEvaluateProperty() {
    runPythonTest(new PyConsoleTask("/debug") {
      @Override
      public void testing() throws Exception {
        exec("class Bar:\n" +
             "    @property\n" +
             "    def prop(self):\n" +
             "        x = 238\n" +
             "        print(x + 1)\n" +
             "        return \"bar\"\n" +
             "    \n");
        exec("bar = Bar()");
        exec("print(\"Hey\")");
        waitForOutput("Hey");

        List<PydevCompletionVariant> completions = getCompletions("bar.");
        assertFalse("Completion variants list is empty", completions.isEmpty());

        // Just to make sure that output is updated
        exec("print('Hello')");
        waitForOutput("Hello");

        PydevCompletionVariant compVariant = findCompletionVariantByName(completions, "prop");
        assertNotNull("Completion variant `prop` is missing", compVariant);

        assertEquals(3, compVariant.getType());
        String currentOutput = output();
        assertFalse("Property was called for completion", currentOutput.contains("239"));
      }
    });
  }

  @Test
  public void testParameterInfo() {
    runPythonTest(new PyConsoleTask("/debug") {
      @Override
      public void testing() throws Exception {
        exec("from os import getenv");
        exec("print(\"Hi\")");
        waitForOutput("Hi");
        addTextToEditor("getenv()");
        ApplicationManager.getApplication().invokeAndWait(() -> {
          checkParameters(7, getConsoleFile(), "key, default=None", new String[]{"key, "});
        });
      }
    });
  }
}
