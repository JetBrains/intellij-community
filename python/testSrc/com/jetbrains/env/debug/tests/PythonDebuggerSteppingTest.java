// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.env.TestEnv;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.debug.tasks.PyDebuggerTask;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.debugger.settings.PySteppingFilter;
import com.jetbrains.python.debugger.smartstepinto.PySmartStepIntoVariant;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class PythonDebuggerSteppingTest extends PyEnvTestCase {

  private static class PySmartStepIntoDebuggerTask extends PyDebuggerTask {

    private final static String RELATIVE_TEST_DATA_PATH = "/debug/stepping";

    PySmartStepIntoDebuggerTask(@NotNull String scriptName) {
      this(RELATIVE_TEST_DATA_PATH, scriptName);
    }

    PySmartStepIntoDebuggerTask(@Nullable String relativeTestDataPath, String scriptName) {
      super(relativeTestDataPath, scriptName);
    }

    @Override
    public void runTestOn(@NotNull String sdkHome, @Nullable Sdk existingSdk) throws Exception {
      Sdk sdk = createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS);
      SdkModificator modificator = sdk.getSdkModificator();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        ApplicationManager.getApplication().runWriteAction(modificator::commitChanges);
      });
      super.runTestOn(sdk.getHomePath(), sdk);
    }

    @Override
    protected AbstractPythonRunConfiguration createRunConfiguration(@NotNull String sdkHome, @Nullable Sdk existingSdk) {
      PythonRunConfiguration runConfiguration = (PythonRunConfiguration)super.createRunConfiguration(sdkHome, existingSdk);
      runConfiguration.getEnvs().put("PYDEVD_USE_CYTHON", "NO");
      return runConfiguration;
    }

    @Override
    public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
      return level.compareTo(LanguageLevel.PYTHON27) > 0;
    }

    void assertSmartStepIntoVariants(@NotNull String @NotNull ... expectedFunctionNames) {
      var variants = getSmartStepIntoVariants();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        String[] arr = new String[variants.size()];
        for(int i = 0; i < arr.length; i++) {
          PySmartStepIntoVariant v = (PySmartStepIntoVariant) variants.get(i);
          arr[i] = v.getFunctionName();
        }
        Assert.assertArrayEquals(expectedFunctionNames, arr);
      });
    }

    @NotNull
    @Override
    public Set<String> getTags() {
      // Since we're creating an SDK it's a good idea to remove all the heavy stuff.
      return ImmutableSet.of("-qt", "-tensorflow1", "-tensorflow2");
    }
  }

  @Test
  public void testResumeAfterStepping() {
    // This test case is important for frame evaluation debugging, because we reuse old tracing function for stepping and there were
    // some problems with switching between frame evaluation and tracing
    runPythonTest(new PyDebuggerTask("/debug", "test_resume_after_step.py") {
      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 2);
        toggleBreakpoint(getScriptName(), 5);
        toggleBreakpoint(getScriptName(), 12);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("a").hasValue("1");
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        eval("c").hasValue("3");
        resume();
        waitForPause();
        eval("d").hasValue("4");
        resume();
        waitForPause();
        eval("t").hasValue("1");
        resume();
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
      }
    });
  }

  @Test
  public void testStepInto() {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 5);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepInto();
        waitForPause();
        eval("x").hasValue("1");
        stepOver();
        waitForPause();
        eval("y").hasValue("3");
        stepOver();
        waitForPause();
        eval("z").hasValue("1");
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
      }
    });
  }

  @Test
  public void testStepIntoMyCode() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_my_code.py") {

      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 5);
        toggleBreakpoint(getFilePath(getScriptName()), 7);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepIntoMyCode();
        waitForPause();
        eval("x").hasValue("2");
        resume();
        waitForPause();
        eval("x").hasValue("3");
        stepIntoMyCode();
        waitForPause();
        eval("stopped_in_user_file").hasValue("True");
      }
    });
  }

  @Test
  public void testSmartStepInto() {
    runPythonTest(new PyDebuggerTask("/debug", "test3.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 14);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        smartStepInto("foo", 0);
        waitForPause();
        stepOver();
        waitForPause();
        eval("y").hasValue("4");
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
      }
    });
  }

  @Test
  public void testSmartStepInto2() {
    runPythonTest(new PySmartStepIntoDebuggerTask("/debug", "test3.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 18);
        toggleBreakpoint(getFilePath(getScriptName()), 25);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        removeBreakpoint(getFilePath(getScriptName()), 18);
        smartStepInto("foo", 0);
        waitForPause();
        assertSmartStepIntoVariants("zoo", "foo");
        smartStepInto("foo", 0);
        waitForPause();
        eval("x").hasValue("4");
        resume();
        waitForPause();
        eval("a.z").hasValue("1");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoVariants() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into.py") {
      @Override
      public void before() {
        toggleBreakpoint(1);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("bar", "baz", "bar", "barbaz", "bar", "barbaz", "bar", "baz", "add");
        smartStepInto("barbaz", 1);
        waitForPause();
        eval("i").hasValue("42");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("bar", "baz", "add");
        smartStepInto("baz", 1);
        waitForPause();
        assertSmartStepIntoVariants("bar", "bar");
        smartStepInto("bar", 0);
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("bar");
        smartStepInto("bar", 1);
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        eval("y").hasValue("84");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("add");
        resume();
        waitForPause();
        assertSmartStepIntoVariants("bar", "baz", "bar", "barbaz", "bar", "barbaz", "bar", "baz", "add");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoWithStepInto() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into.py") {
      @Override
      public void before() {
        toggleBreakpoint(1);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("bar", "baz", "bar", "barbaz", "bar", "barbaz", "bar", "baz", "add");
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("baz", "bar", "barbaz", "bar", "barbaz", "bar", "baz", "add");
        smartStepInto("barbaz", 1);
        waitForPause();
        eval("i").hasValue("42");
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("bar", "baz", "add");
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("baz", "add");
        smartStepInto("baz", 1);
        waitForPause();
        smartStepInto("bar", 1);
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("add");
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("bar", "baz", "bar", "barbaz", "bar", "barbaz", "bar", "baz", "add");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoConstructor() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_constructor.py") {
      @Override
      public void before() {
        toggleBreakpoint(8);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("A", "get_x");
        smartStepInto("A", 0);
        waitForPause();
        eval("x").hasValue("42");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testExplicitInitCall() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_explicit_init.py") {
      @Override
      public void before() {
        toggleBreakpoint(11);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepInto();
        waitForPause();
        eval("x").hasValue("1");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoChain() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_chain.py") {
      @Override
      public void before() {
        toggleBreakpoint(6);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("f", "f", "f");
        smartStepInto("f", 0);
        waitForPause();
        eval("x").hasValue("1");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("f", "f");
        smartStepInto("f", 1);
        waitForPause();
        eval("x").hasValue("2");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("f");
        smartStepInto("f", 2);
        waitForPause();
        eval("x").hasValue("3");
        stepOver();
        waitForPause();
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoCondition() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_condition.py") {
      @Override
      public void before() {
        toggleBreakpoints(13, 18);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants(); // there shouldn't be any stepping variants here
        resume();
        waitForPause();
        assertSmartStepIntoVariants("cond1", "cond2");
      }
    });
  }

  @Test
  public void testSmartStepIntoGenExpr() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_genexpr.py") {
      @Override
      public void before() {
        toggleBreakpoint(11);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("<genexpr>");
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        eval("x").hasValue("0");
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        eval("i").hasValue("11");
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        removeBreakpoint(getScriptName(), 11);
        resume();
        waitForTerminate();
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) < 0;
      }
    });
  }

  @Test
  public void testSmartStepIntoDecorator1() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_decorator1.py") {
      @Override
      public void before() {
        toggleBreakpoint(12);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        smartStepInto("f", 1);
        waitForPause();
        eval("x").hasValue("2");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoDecorator2Python3() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_decorator2.py") {
      @Override
      public void before() {
        toggleBreakpoints(15);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("foo", "foo", "generate_power");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("foo", "foo", "generate_power");
        smartStepInto("generate_power", 0);
        waitForPause();
        eval("exponent").hasValue("5");
        resume();
        if (TestEnv.LINUX.isThisOs() || TestEnv.MAC.isThisOs()) {
          waitForPause();
          resume();
        }
        waitForTerminate();
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) < 0;
      }
    });
  }

  @Test
  public void testSmartStepIntoDecorator2Python2() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_decorator2.py") {
      @Override
      public void before() {
        toggleBreakpoints(15, 26);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("foo", "foo", "generate_power");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("foo", "foo", "generate_power");
        smartStepInto("generate_power", 0);
        waitForPause();
        toggleBreakpoint(26);
        eval("exponent").hasValue("5");
        resume();
        waitForPause();
        resume();
        waitForTerminate();
      }

      @Override
      public @NotNull Set<String> getTags() {
        return ImmutableSet.<String>builder().addAll(super.getTags()).add("python2.7").build();
      }
    });
  }

  @Test
  public void testSmartStepIntoNativeFunction() {
    runPythonTest(new PySmartStepIntoDebuggerTask( "test_smart_step_into_native_function.py") {
      @Override
      public void before() {
        toggleBreakpoint(12);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        smartStepInto("f", 0);
        waitForPause();
        stepInto();
        waitForPause();
        waitForOutput("Called");
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        waitForOutput("Reversed");
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        waitForOutput("Appended");
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("f", "f");
        smartStepInto("f", 1);
        waitForPause();
        resume();
        waitForPause();
        waitForOutput("Called");
        waitForOutput("Reversed");
        waitForOutput("Appended");
        assertSmartStepIntoVariants("f", "f", "f");
        resume();
        waitForTerminate();
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) < 0;
      }
    });

  }

  @Test
  public void testSmartStepIntoNativeFunctionInReturn() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_native_function_in_return.py") {
      @Override
      public void before() {
        toggleBreakpoint(5);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        smartStepInto("f", 0);
        waitForPause();
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("swapcase");
        smartStepInto("swapcase", 0);
        waitForPause();
        assertSmartStepIntoVariants("f", "f", "f", "f");
        smartStepInto("f", 1);
        waitForPause();
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("swapcase");
        smartStepInto("swapcase", 0);
        waitForPause();
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoAnotherModule() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_another_module.py") {
      @Override
      public void before() {
        toggleBreakpoint(2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("A", "get_x");
        smartStepInto("A", 0);
        waitForPause();
        eval("x").hasValue("42");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("get_x");
        smartStepInto("get_x", 0);
        waitForPause();
        eval("self.x").hasValue("42");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoMultiline1() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_multiline1.py") {
      @Override
      public void before() {
        toggleBreakpoint(4);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("f", "f", "f", "f", "f");
        smartStepInto("f", 2);
        waitForPause();
        eval("x").hasValue("2");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("f", "f");
        smartStepInto("f", 4);
        waitForPause();
        eval("x").hasValue("4");
      }
    });
  }

  @Test
  public void testSmartStepIntoMultiline2Python3() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_multiline2.py") {
      @Override
      public void before() {
        toggleBreakpoints(20, 25);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        removeBreakpoint(getScriptName(), 20);
        assertSmartStepIntoVariants("A", "return_my_lucking_link", "return_my_lucking_payload", "do_stuff");
        resume();
        waitForPause();
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoMultiline2Python2() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_multiline2.py") {
      @Override
      public void before() {
        toggleBreakpoints(20, 25);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        toggleBreakpoint(25);
        assertSmartStepIntoVariants("A", "return_my_lucking_link", "return_my_lucking_payload", "dumps", "do_stuff");
        resume();
        waitForTerminate();
      }

      @Override
      public @NotNull Set<String> getTags() {
        return ImmutableSet.<String>builder().addAll(super.getTags()).add("python2.7").build();
      }
    });
  }

  @Test
  public void testSmartStepIntoBinaryOperator1() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_binary_operator1.py") {
      @Override
      public void before() {
        toggleBreakpoint(4);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("identity", "identity", "identity", "identity", "identity");
        smartStepInto("identity", 4);
        waitForPause();
        eval("x").hasValue("43");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoBinaryOperator2() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_binary_operator2.py") {
      @Override
      public void before() {
        toggleBreakpoint(16);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("__add__", "__sub__", "Point", "__sub__", "Point", "__add__");
        smartStepInto("__sub__", 0);
        waitForPause();
        eval("other.x").hasValue("3");
        eval("other.y").hasValue("3");
        stepOver();
        waitForPause();
        smartStepInto("Point", 0);
        waitForPause();
        eval("x").hasValue("4");
        eval("y").hasValue("4");
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("__sub__", "Point", "__add__");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoBinaryOperator3() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_binary_operator3.py") {
      @Override
      public void before() {
        toggleBreakpoint(47);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepInto();
        waitForPause();
        assertSmartStepIntoVariants("A", "A", "__add__", "A", "__add__", "A", "__sub__", "A", "__mul__", "identity",
                                    "A", "__div__", "A", "__floordiv__", "__mod__", "A", "__pow__");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoComparisonOperator() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_comparison_operator.py") {
      @Override
      public void before() {
        toggleBreakpoint(14);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("A", "A", "__gt__", "A", "__gt__");
        smartStepInto("__gt__", 0);
        waitForPause();
        eval("other.a").hasValue("3");
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("A", "__gt__");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoUnaryOperator() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_unary_operator.py") {
      @Override
      public void before() {
        toggleBreakpoint(22);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants(
          "__add__", "__neg__", "__add__", "A", "__pos__", "__add__", "__invert__", "__add__");
        smartStepInto("__neg__", 0);
        waitForPause();
        eval("self.x").hasValue("3");
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        stepInto();
        waitForPause();
        smartStepInto("A", 0);
        waitForPause();
        eval("x").hasValue("-4");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("__pos__", "__add__", "__invert__", "__add__");
        smartStepInto("__pos__", 0);
        waitForPause();
        eval("self.x").hasValue("-4");
        stepOver();
        waitForPause();
        assertSmartStepIntoVariants("__add__", "__invert__", "__add__");
        smartStepInto("__invert__", 0);
        waitForPause();
        eval("self.x").hasValue("1");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testSmartStepIntoInheritancePython3() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_inheritance.py") {
      @Override
      public void before() {
        toggleBreakpoint(14);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("foo", "make_class");
        smartStepInto("foo", 0);
        waitForPause();
        stepOver();
        waitForPause();
        removeBreakpoint(getScriptName(), 14);
        smartStepInto("make_class", 0);
        waitForPause();
        eval("x").hasValue("100");

        resume();
        waitForTerminate();
      }

      @Override
      public @NotNull Set<String> getTags() {
        return ImmutableSet.<String>builder().addAll(super.getTags())
          .add("-python2.7")
          .add("-django")
          .build();
      }
    });
  }

  @Test
  public void testSmartStepIntoInheritancePython2() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_smart_step_into_inheritance.py") {
      @Override
      public void before() {
        toggleBreakpoint(14);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        assertSmartStepIntoVariants("foo", "make_class");
        smartStepInto("foo", 0);
        waitForPause();
        stepOver();
        waitForPause();
        smartStepInto("make_class", 0);
        waitForPause();
        eval("x").hasValue("100");
        resume();
        waitForPause();
        resume();
        waitForPause();
        resume();
        waitForTerminate();
      }

      @Override
      public @NotNull Set<String> getTags() {
        return ImmutableSet.<String>builder().addAll(super.getTags()).add("python2.7").build();
      }
    });
  }

  @Test
  public void testStepIntoWithThreads() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_step_into_with_threads.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 15);
        toggleBreakpoint(getFilePath(getScriptName()), 17);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepInto();
        waitForPause();
        stepOver();
        waitForPause();
        waitForOutput("foo");
        resume();
        waitForOutput("bar");
        waitForPause();
        stepInto();
        waitForPause();
        stepOver();
        waitForPause();
        waitForOutput("baz");
        resume();
        waitForTerminate();
      }
    });
  }

  @Test
  public void testStepOver() {
    runPythonTest(new PyDebuggerTask("/debug", "test2.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 5);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepOver();
        waitForPause();
        stepOver();
        waitForPause();
        eval("z").hasValue("2");
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
      }
    });
  }

  @Test
  public void testStepOverConditionalBreakpoint() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_stepOverCondition.py") {
      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 1);
        toggleBreakpoint(getScriptName(), 2);
        XDebuggerTestUtil.setBreakpointCondition(getProject(), 2, "y == 3");
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepOver();
        waitForPause();
        eval("y").hasValue("2");
      }
    });
  }

  @Test
  public void testStepOverYieldFrom() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_step_over_yield.py") {

      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 6);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepOver();
        waitForPause();
        eval("a").hasValue("42");
        stepOver();
        waitForPause();
        eval("a").hasValue("42");
        stepOver();
        waitForPause();
        eval("sum").hasValue("6");
        resume();
      }
    });
  }

  // interesting for Python 3.12
  @Test
  public void testStepOverAwait() {
    runPythonTest(new PyDebuggerTask("/debug/stepping", "test_step_over_await.py") {
      @Override
      public void before() {
        toggleBreakpoint(10);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepOver();
        waitForPause();
        eval("result").hasValue("3");
        stepOver();
        waitForPause();
        eval("z").hasValue("42");  // Check that we haven't fallen into the `asyncio` machinery.
        resume();
        waitForTerminate();
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0 && level.compareTo(LanguageLevel.PYTHON311) < 0;
      }
    });
  }

  @Test
  public void testStepOverOutsideProject() {
    runPythonTest(new PyDebuggerTask("/debug/stepping/", "test_step_over_outside_project_scope.py") {
      @Override
      public void before() {
        toggleBreakpoint(3);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepInto();
        waitForPause();
        stepOver();
        waitForPause();
        eval("instream").hasValue("'a b c'");  // ensure we're still in the library scope after performing a step over
        resume();
        waitForTerminate();
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
      }
    });
  }

  @Test
  public void testSteppingFilter() {
    runPythonTest(new PySmartStepIntoDebuggerTask("test_stepping_filter.py") {

      @Override
      public void before() {
        toggleBreakpoint(getScriptName(), 4);
        List<PySteppingFilter> filters = new ArrayList<>();
        filters.add(new PySteppingFilter(true, "*/test_m?_code.py"));
        final PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        debuggerSettings.setLibrariesFilterEnabled(true);
        debuggerSettings.setSteppingFiltersEnabled(true);
        debuggerSettings.setSteppingFilters(filters);
      }

      @Override
      public void doFinally() {
        final PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
        debuggerSettings.setLibrariesFilterEnabled(false);
        debuggerSettings.setSteppingFiltersEnabled(false);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        stepInto();
        waitForPause();
        eval("stopped_in_user_file").hasValue("True");
        stepInto();
        waitForPause();
        eval("stopped_in_user_file").hasValue("True");
      }
    });
  }

  @Test
  public void testSeveralReturnSignal() {
    runPythonTest(new PyDebuggerTask("/debug", "test_several_return_signal.py") {
      @Override
      public AbstractPythonRunConfiguration createRunConfiguration(@NotNull String sdkHome, Sdk existingSdk) {
        var runConfiguration = (PythonRunConfiguration) super.createRunConfiguration(sdkHome, existingSdk);
        runConfiguration.getEnvs().replace("PYDEVD_USE_CYTHON", "NO");
        return runConfiguration;
      }

      @Override
      public void before() {
        toggleBreakpoints(6);
      }

      private void stepIntoFunAndReturn() throws InterruptedException {
        stepOver();
        waitForPause();
        stepInto();
        waitForPause();
        // check STEP_INTO_CMD
        assertEquals(1, XDebuggerManager.getInstance(getProject()).getCurrentSession().getCurrentPosition().getLine());
        stepOver();
        waitForPause();
      }

      @Override
      public void testing() throws InterruptedException {
        waitForPause();
        stepIntoFunAndReturn();

        var session = XDebuggerManager.getInstance(getProject()).getCurrentSession();

        // check PY_RETURN signal
        assertEquals(7, session.getCurrentPosition().getLine());

        resume();
        waitForPause();
        removeBreakpoint(getScriptName(), 6);
        stepIntoFunAndReturn();

        // check PY_RETURN signal
        assertEquals(7, session.getCurrentPosition().getLine());
        resume();

        waitForTerminate();
      }

      @Override
      public boolean isLanguageLevelSupported(@NotNull final LanguageLevel level) {
        return level.compareTo(LanguageLevel.PYTHON27) > 0;
      }
    });
  }
}
