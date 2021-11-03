// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.debug;

import com.intellij.xdebugger.XDebuggerTestUtil;
import com.intellij.xdebugger.frame.XValue;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.python.debugger.PyDebugValue;
import org.junit.Test;

import java.util.List;

public class PythonDebuggerSetValueTest extends PyEnvTestCase {

  /**
   * setValue(x, 'change')
   * assert(x == 'change')
   */
  @Test
  public void testSetValueSimpleVariable() {
    runPythonTest(new PyDebuggerTask("/debug", "test_set_value_simple_var.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        eval("x").hasValue("10");
        setVal("x", "\"change\"");
        eval("x").hasValue("'change'");
      }
    });
  }

  /**
   * setValue(objs['two'], "change")
   * assert(objs['two'] == "change")
   * <p>
   * setValue(objs['three'], [1, 2, 3])
   * assert(objs['three'] == [1, 2, 3])
   * <p>
   * setValue(objs['three'][0], 'hello')
   * assert(objs['three'][0] == 'hello')
   */
  @Test
  public void testSetValueDict() {
    runPythonTest(new PyDebuggerTask("/debug", "test_set_value_dict.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 6);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();

        List<PyDebugValue> frameVariables = loadFrame();
        PyDebugValue var = findDebugValueByName(frameVariables, "objs");
        List<XValue> children = XDebuggerTestUtil.collectChildren(var);
        PyDebugValue two = (PyDebugValue)children.get(1);
        PyDebugValue three = (PyDebugValue)children.get(2);

        eval("objs['two']").hasValue("2");
        myDebugProcess.changeVariable(two, "\"change\"");
        eval("objs['two']").hasValue("'change'");

        eval("objs['three']").hasValue("3");
        myDebugProcess.changeVariable(three, "[1, 2, 3]");
        eval("objs['three']").hasValue("[1, 2, 3]");

        children = XDebuggerTestUtil.collectChildren(var);
        three = (PyDebugValue)children.get(2);
        children = XDebuggerTestUtil.collectChildren(three);
        PyDebugValue one = (PyDebugValue)children.get(0);

        eval("objs['three'][0]").hasValue("1");
        myDebugProcess.changeVariable(one, "\"hello\"");
        eval("objs['three'][0]").hasValue("'hello'");
      }
    });
  }

  /**
   * setValue(lst[0], "change")
   * assert(lst[0] == "change")
   * <p>
   * setValue(lst[1], ['1', '2', '3'])
   * assert(lst[1] == ['1', '2', '3'])
   * <p>
   * setValue(lst[2], {'one': 1, 'two': 2})
   * assert(lst[2] == {'one': 1, 'two': 2})
   * <p>
   * setValue(lst[2]['one'], 'hello')
   * assert(lst[2]['one'] == 'hello')
   */
  @Test
  public void testSetValueList() {
    runPythonTest(new PyDebuggerTask("/debug", "test_set_value_list.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 2);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();
        PyDebugValue var = findDebugValueByName(frameVariables, "lst");
        List<XValue> children = XDebuggerTestUtil.collectChildren(var);
        PyDebugValue one = (PyDebugValue)children.get(0);
        PyDebugValue two = (PyDebugValue)children.get(1);
        PyDebugValue three = (PyDebugValue)children.get(2);

        eval("lst[0]").hasValue("1");
        myDebugProcess.changeVariable(one, "\"change\"");
        eval("lst[0]").hasValue("'change'");

        eval("lst[1]").hasValue("2");
        myDebugProcess.changeVariable(two, "['1', '2', '3']");
        eval("lst[1]").hasValue("['1', '2', '3']");

        eval("lst[2]").hasValue("3");
        myDebugProcess.changeVariable(three, "{'one': 1, 'two': 2}");
        eval("lst[2]").hasValue("{'one': 1, 'two': 2}");

        children = XDebuggerTestUtil.collectChildren(var);
        three = (PyDebugValue)children.get(2);
        children = XDebuggerTestUtil.collectChildren(three);
        one = (PyDebugValue)children.get(0);

        eval("lst[2]['one']").hasValue("1");
        myDebugProcess.changeVariable(one, "'hello'");
        eval("lst[2]['one']").hasValue("'hello'");
      }
    });
  }

  /**
   * setValue(foo.fst, 'change')
   * assert(foo.fst == 'change')
   * <p>
   * setValue(foo.snd, ['1', '2', '3'])
   * assert(foo.snd == ['1', '2', '3'])
   * <p>
   * setValue(foo.snd[0], 'hello')
   * assert(foo.snd[0] == 'hello')
   */
  @Test
  public void testSetValueClass() {
    runPythonTest(new PyDebuggerTask("/debug", "test_set_value_class.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 6);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();
        PyDebugValue var = findDebugValueByName(frameVariables, "foo");
        List<XValue> children = XDebuggerTestUtil.collectChildren(var);
        PyDebugValue one = (PyDebugValue)children.get(0);
        PyDebugValue two = (PyDebugValue)children.get(1);

        eval("foo.fst").hasValue("1");
        myDebugProcess.changeVariable(one, "\"change\"");
        eval("foo.fst").hasValue("'change'");

        eval("foo.snd").hasValue("'hello'");
        myDebugProcess.changeVariable(two, "['1', '2', '3']");
        eval("foo.snd").hasValue("['1', '2', '3']");

        children = XDebuggerTestUtil.collectChildren(var);
        two = (PyDebugValue)children.get(1);
        children = XDebuggerTestUtil.collectChildren(two);
        one = (PyDebugValue)children.get(0);

        eval("foo.snd[0]").hasValue("'1'");
        myDebugProcess.changeVariable(one, "'hello'");
        eval("foo.snd[0]").hasValue("'hello'");
      }
    });
  }

  /**
   * setValue(test().lst[0], 'change')
   * assert(test().lst[0] == 'change')
   * <p>
   * assert(lst == [1, 2, 3])
   */
  @Test
  public void testSetValueLocalsGlobals() {
    runPythonTest(new PyDebuggerTask("/debug", "test_set_value_locals_globals.py") {
      @Override
      public void before() {
        toggleBreakpoint(getFilePath(getScriptName()), 5);
        toggleBreakpoint(getFilePath(getScriptName()), 9);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        List<PyDebugValue> frameVariables = loadFrame();
        PyDebugValue var = findDebugValueByName(frameVariables, "lst");
        List<XValue> children = XDebuggerTestUtil.collectChildren(var);
        PyDebugValue one = (PyDebugValue)children.get(0);

        eval("lst[0]").hasValue("'a'");
        myDebugProcess.changeVariable(one, "'change'");
        eval("lst[0]").hasValue("'change'");

        resume();
        waitForPause();

        eval("lst").hasValue("[1, 2, 3]");
      }
    });
  }
}