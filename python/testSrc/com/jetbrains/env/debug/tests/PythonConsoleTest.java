// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests;

import com.google.common.collect.ImmutableSet;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.common.EditorCaretTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.debug.tasks.PyConsoleTask;
import com.jetbrains.python.console.PyConsoleOptions;
import com.jetbrains.python.console.PyConsoleOptionsConfigurable;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static com.jetbrains.env.debug.tasks.PyBaseDebuggerTask.findCompletionVariantByName;
import static com.jetbrains.env.debug.tasks.PyBaseDebuggerTask.findDebugValueByName;
import static com.jetbrains.python.PyParameterInfoTest.checkParameters;
import static org.junit.Assert.*;

public class PythonConsoleTest extends PyEnvTestCase {
  private static @Nullable List<String> getStaticCompletion(CodeInsightTestFixture fixture, PythonConsoleView consoleView) {
    fixture.configureFromExistingVirtualFile(consoleView.getVirtualFile());
    ApplicationManager.getApplication().invokeAndWait(() -> {
      addCaretsToConsoleEditor(consoleView.getCurrentEditor());
    });

    fixture.completeBasic();

    return fixture.getLookupElementStrings();
  }

  @RequiresEdt
  private static void addCaretsToConsoleEditor(EditorEx consoleEditor) {
    var state = EditorTestUtil.extractCaretAndSelectionMarkers(consoleEditor.getDocument());
    AtomicBoolean primary = new AtomicBoolean(true);
    ReadAction.run(() -> {
      var model = consoleEditor.getCaretModel();
      List<Caret> oldCarets = List.copyOf(model.getAllCarets());

      for (EditorCaretTestUtil.CaretInfo caret : state.carets()) {
        if (caret.position != null) {
          model.addCaret(caret.position, primary.get());
          primary.set(false);
        }
      }

      oldCarets.forEach(model::removeCaret);
    });
  }

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
        exec("""
               if True:
                 x = 1
               y = x + 100
               for i in range(1):
                 print(y)
               """);
        waitForOutput("101");
      }
    });
  }

  @Test
  public void testInterruptAsync() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("import time");
        execNoWait("""
                     for i in range(10000):
                       print(i)
                       time.sleep(0.1)""");
        waitForOutput("3\n4\n5");
        Assert.assertFalse(canExecuteNow());
        interrupt();
        waitForFinish();
        waitForReady();
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
        exec("""
               if True:
                 print(x)
               """);
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
          for (TreeNode child : root.getChildren()) {
            XDebuggerTreeNode node = (XDebuggerTreeNode)child;
            if (node.toString().equals("foo")) {
              return true;
            }
          }
          return false;
        });

        exec("foo = 'baz'");
        waitForConditionIsTrue("`foo` value hasn't changed to 'baz'", (root) -> {
          for (TreeNode child : root.getChildren()) {
            XDebuggerTreeNode node = (XDebuggerTreeNode)child;
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
      public void before() {
        PyConsoleOptions.getInstance(getProject()).setCodeCompletionOption(PyConsoleOptionsConfigurable.CodeCompletionOption.STATIC);
      }

      @Override
      public void testing() throws Exception {
        exec("""
               class Bar:
                   @property
                   def prop(self):
                       x = 2389952
                       print(x + 1)
                       return "bar"
                  \s
               """);
        exec("bar = Bar()");
        exec("print(\"Hey\")");
        waitForOutput("Hey");

        addTextToEditor("bar.<caret>");
        getStaticCompletion(myFixture, getConsoleView());
        assertTrue(getConsoleView().getCurrentEditor().getDocument().getText().endsWith("prop"));

        // Just to make sure that output is updated
        exec("print('Hello')");
        waitForOutput("Hello");

        String currentOutput = output();
        assertFalse("Property was called for completion", currentOutput.contains("2389953"));
      }

      @Override
      public @NotNull Set<String> getTags() {
        return ImmutableSet.of("-python3.8", "-python3.9", "-python3.10", "-python3.11", "-python3.12");
      }
    });
  }

  @Test
  public void testParameterInfo() {
    runPythonTest(new PyConsoleTask("/debug") {
      @Override
      public void before() {
        PyConsoleOptions.getInstance(getProject()).setCodeCompletionOption(PyConsoleOptionsConfigurable.CodeCompletionOption.RUNTIME);
      }

      @Override
      public void testing() throws Exception {
        exec("from os import getenv");
        exec("print(\"Hi\")");
        waitForOutput("Hi");
        addTextToEditor("getenv()");
        ApplicationManager.getApplication().runReadAction(() -> {
          checkParameters(7, getConsoleFile(), "key, default=None", new String[]{"key, "}, myFixture.getEditor());
        });
      }
    });
  }

  @Test
  public void testCheckForThreadLeaks() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("x = 42");
        exec("print(x)");
        waitForOutput("42");
      }

      @Override
      public boolean reportThreadLeaks() {
        return true;
      }
    });
  }

  @Test
  public void testStaticCodeInside() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void before() {
        PyConsoleOptions.getInstance(getProject()).setCodeCompletionOption(PyConsoleOptionsConfigurable.CodeCompletionOption.STATIC);
      }

      @Override
      public void testing() throws Exception {
        exec("def foo():\n" +
             "  pass");
        exec("x = 42");
        exec("s = 'str'");

        assertContainsElements(getStaticCompletion(myFixture, getConsoleView()), "x", "foo", "s");
      }
    });
  }

  @Test
  public void testConsoleHistoryNavigation() {
    runPythonTest(new PyConsoleTask("/debug") {
      @Override
      public void testing() {
        addToHistory("a = 1");
        addToHistory("""
                       def foo():
                         x = 1
                         y = 2
                         return x + y""");

        runHistoryAction(true);
        checkCaretPosition(true);

        runHistoryAction(true);
        checkCaretPosition(true);

        runHistoryAction(false);
        checkCaretPosition(false);
      }

      private void addToHistory(String command) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
          ConsoleHistoryController.getController(getConsoleView()).addToHistory(command);
        });
      }

      private void runHistoryAction(boolean isNext) {
        ConsoleHistoryController controller = ConsoleHistoryController.getController(getConsoleView());
        EdtTestUtil.runInEdtAndWait(() -> {
          AnAction action = isNext ? controller.getHistoryNext() : controller.getHistoryPrev();
          action.actionPerformed(TestActionEvent.createTestEvent());
        });
      }

      private void checkCaretPosition(boolean isNext) {
        EditorEx consoleEditor = getConsoleView().getConsoleEditor();

        ApplicationManager.getApplication().runReadAction(() -> {
          int offset = consoleEditor.getCaretModel().getOffset();
          int caretLine = consoleEditor.getDocument().getLineNumber(offset);
          int linesCount = consoleEditor.getDocument().getLineCount();
          if (linesCount > 1) {
            if (isNext) {
              assertEquals(0, caretLine);
            }
            else {
              assertEquals(linesCount - 1, caretLine);
            }
          }
          else {
            assertEquals(0, caretLine);
          }
        });
      }
    });
  }

  @Test
  public void testPromptsPrinting() {
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("print('>?')");
        waitForOutput(">?");

        exec("print('...')");
        waitForOutput("...");

        exec("print('>>>')");
        waitForOutput(">>>");
      }
    });
  }

  // PY-66456
  @Test
  public void testAwaitHighlight() {
    // TODO: This shouldn't require an actual Python interpreter to run
    runPythonTest(new PyConsoleTask() {
      @Override
      public void testing() throws Exception {
        exec("import asyncio");
        waitForReady();
        addTextToEditor("await asyncio.sleep(1)");
        waitForReady();
        ApplicationManager.getApplication().invokeAndWait(() -> {
          myFixture.testHighlighting(true, false, false, getConsoleFile().getVirtualFile());
        });
      }

      @Override
      public @NotNull Set<String> getTags() {
        return Set.of("python3.8");
      }
    });
  }
}
