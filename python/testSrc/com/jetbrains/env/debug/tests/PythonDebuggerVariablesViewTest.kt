// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.debug.tasks.PyDebuggerTask
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyDebuggerException
import com.jetbrains.python.debugger.pydev.ProcessDebugger
import com.jetbrains.python.debugger.settings.PyDebuggerSettings
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PythonRunConfiguration
import org.junit.Assert
import org.junit.Test

class PythonDebuggerVariablesViewTest : PyEnvTestCase() {

  @Test
  fun testLoadValuesAsync() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_async_eval.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 14)
      }

      @Throws(Exception::class)
      override fun testing() {
        waitForPause()
        val frameVariables = loadFrame()
        var result = computeValueAsync(frameVariables, "f")
        Assert.assertEquals("foo", result)

        val listChildren = loadChildren(frameVariables, "l")
        result = computeValueAsync(listChildren, "0")
        Assert.assertEquals("list", result)
      }
    })
  }

  @Test
  fun testLargeCollectionsLoading() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_large_collections.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 15)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        val frameVariables = loadFrame()

        // The effective maximum number of the debugger returns is MAX_ITEMS_TO_HANDLE
        // plus the "Protected Attributes" group.
        val effectiveMaxItemsNumber = MAX_ITEMS_TO_HANDLE + 1

        // Large list.
        val L = findDebugValueByName(frameVariables, "L")

        var children = loadVariable(L)
        val collectionLength = 1000

        Assert.assertEquals(effectiveMaxItemsNumber.toLong(), children.size().toLong())
        for (i in 0..<MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        L!!.offset = 600
        children = loadVariable(L)
        Assert.assertEquals(effectiveMaxItemsNumber.toLong(), children.size().toLong())
        for (i in 600..<600 + MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        L!!.offset = 900
        children = loadVariable(L)
        Assert.assertEquals(101, children.size().toLong())
        for (i in 900..<collectionLength) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        // Large dict.
        val D = findDebugValueByName(frameVariables, "D")

        children = loadVariable(D)

        Assert.assertEquals(effectiveMaxItemsNumber.toLong(), children.size().toLong())
        for (i in 0..<MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, i))
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        D!!.offset = 600
        children = loadVariable(D)
        Assert.assertEquals(effectiveMaxItemsNumber.toLong(), children.size().toLong())
        for (i in 600..<600 + MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, i))
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        D!!.offset = 900
        children = loadVariable(D)
        Assert.assertEquals(101, children.size().toLong())
        for (i in 900..<collectionLength) {
          Assert.assertTrue(hasChildWithName(children, i))
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        // Large set.
        val S = findDebugValueByName(frameVariables, "S")

        children = loadVariable(S)

        Assert.assertEquals(effectiveMaxItemsNumber.toLong(), children.size().toLong())
        for (i in 0..<MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        S!!.offset = 600
        children = loadVariable(S)
        Assert.assertEquals(effectiveMaxItemsNumber.toLong(), children.size().toLong())
        for (i in 600..<600 + MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        S!!.offset = 900
        children = loadVariable(S)
        Assert.assertEquals(101, children.size().toLong())
        for (i in 900..<collectionLength) {
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        // Large deque.
        val dq = findDebugValueByName(frameVariables, "dq")

        children = loadVariable(dq)

        Assert.assertEquals((effectiveMaxItemsNumber + 1).toLong(), children.size().toLong()) // one extra child for maxlen
        for (i in 1..<MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        dq!!.offset = 600
        children = loadVariable(dq)
        Assert.assertEquals(effectiveMaxItemsNumber.toLong(), children.size().toLong())
        for (i in 600..<600 + MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
          Assert.assertTrue(hasChildWithValue(children, i))
        }

        dq!!.offset = 900
        children = loadVariable(dq)
        Assert.assertEquals(101, children.size().toLong())
        for (i in 900..<collectionLength) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
          Assert.assertTrue(hasChildWithValue(children, i))
        }
      }
    })
  }

  @Test
  fun testLargeNumpyArraysLoading() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_large_numpy_arrays.py") {
      override fun getTags(): Set<String> {
        return setOf("pandas")
      }

      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 9)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        val frameVariables = loadFrame()

        val collectionLength = 1000

        // NumPy array
        val nd = findDebugValueByName(frameVariables, "nd")
        var children = loadVariable(nd)

        Assert.assertEquals("min", children.getName(0))
        Assert.assertEquals("max", children.getName(1))
        Assert.assertEquals("shape", children.getName(2))
        Assert.assertEquals("dtype", children.getName(3))
        Assert.assertEquals("size", children.getName(4))
        Assert.assertEquals("array", children.getName(5))

        var array = children.getValue(5) as PyDebugValue

        children = loadVariable(array)
        Assert.assertEquals((MAX_ITEMS_TO_HANDLE + 1).toLong(), children.size().toLong())

        for (i in 0..<MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
        }

        array.offset = 950
        children = loadVariable(array)

        Assert.assertEquals(51, children.size().toLong())

        for (i in 950..<collectionLength) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
        }

        // Pandas series
        val s = findDebugValueByName(frameVariables, "s")
        children = loadVariable(s)
        var values = children.getValue(children.size() - 1) as PyDebugValue
        children = loadVariable(values)
        array = children.getValue(children.size() - 1) as PyDebugValue
        children = loadVariable(array)

        Assert.assertEquals((MAX_ITEMS_TO_HANDLE + 1).toLong(), children.size().toLong())

        for (i in 0..<MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
        }

        array.offset = 950
        children = loadVariable(array)

        Assert.assertEquals(51, children.size().toLong())

        for (i in 950..<collectionLength) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
        }

        // Pandas data frame
        val df = findDebugValueByName(frameVariables, "df")
        children = loadVariable(df)
        values = children.getValue(children.size() - 1) as PyDebugValue
        children = loadVariable(values)
        array = children.getValue(children.size() - 1) as PyDebugValue
        children = loadVariable(array)

        Assert.assertEquals((MAX_ITEMS_TO_HANDLE + 1).toLong(), children.size().toLong())

        for (i in 0..<MAX_ITEMS_TO_HANDLE) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
        }

        array.offset = 950
        children = loadVariable(array)

        Assert.assertEquals(51, children.size().toLong())

        for (i in 950..<collectionLength) {
          Assert.assertTrue(hasChildWithName(children, formatStr(i, collectionLength)))
        }
      }
    })
  }

  @Test
  fun testListComprehension() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_list_comprehension.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 2)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        resume()
        waitForPause()
        val frameVariables = loadFrame()
        Assert.assertTrue(findDebugValueByName(frameVariables, ".0")!!.type!!.endsWith("_iterator"))
        eval(".0")
        // Different Python versions have different types of an internal list comprehension loop. Whatever the type is, we shouldn't get
        // an evaluating error.
        Assert.assertFalse(output().contains("Error evaluating"))
        removeBreakpoint(getFilePath(scriptName), 2)
        resume()
        waitForTerminate()
      }

      override fun getTags(): Set<String> {
        // Remove this after PY-36229 is fixed.
        return setOf("python3")
      }

      override fun isLanguageLevelSupported(level: LanguageLevel): Boolean {
        return level.compareTo(LanguageLevel.PYTHON312) < 0
      }
    })
  }

  @Test
  fun testCollectionsShapes() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_shapes.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 39)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        val frameVariables = loadFrame()
        var `var` = findDebugValueByName(frameVariables, "list1")
        Assert.assertEquals("120", `var`!!.shape)
        `var` = findDebugValueByName(frameVariables, "dict1")
        Assert.assertEquals("2", `var`!!.shape)
        `var` = findDebugValueByName(frameVariables, "custom")
        Assert.assertEquals("5", `var`!!.shape)
        `var` = findDebugValueByName(frameVariables, "df1")
        Assert.assertEquals("(3, 6)", `var`!!.shape)
        `var` = findDebugValueByName(frameVariables, "n_array")
        Assert.assertEquals("(3, 2)", `var`!!.shape)
        `var` = findDebugValueByName(frameVariables, "series")
        Assert.assertEquals("(5,)", `var`!!.shape)

        `var` = findDebugValueByName(frameVariables, "custom_shape")
        Assert.assertEquals("(3,)", `var`!!.shape)
        `var` = findDebugValueByName(frameVariables, "custom_shape2")
        Assert.assertEquals("(2, 3)", `var`!!.shape)
        resume()
        waitForTerminate()
      }

      override fun getTags(): Set<String> {
        return setOf("pandas")
      }
    })
  }

  @Test
  fun testLoadElementsForGroupsOnDemand() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_load_elements_for_groups_on_demand.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 2)
        toggleBreakpoint(getFilePath(scriptName), 8)
        val debuggerSettings = PyDebuggerSettings.getInstance()
        debuggerSettings.isWatchReturnValues = true
      }

      override fun doFinally() {
        val debuggerSettings = PyDebuggerSettings.getInstance()
        debuggerSettings.isWatchReturnValues = false
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        resume()
        waitForPause()

        val defaultVariables = loadSpecialVariables(ProcessDebugger.GROUP_TYPE.DEFAULT)
        var names = listOf("_dummy_ret_val", "_dummy_special_var", "boolean", "get_foo", "string")
        var values = listOf("", "True", "1", "Hello!")
        containsValue(defaultVariables, names, values)

        val specialVariables = loadSpecialVariables(ProcessDebugger.GROUP_TYPE.SPECIAL)
        names = listOf("__builtins__", "__doc__", "__file__", "__loader__", "__name__", "__package__", "__spec__")
        values = listOf("<module 'builtins' (built-in)>", "None", "test_load_elements_for_groups_on_demand.py", " ", "__main__", "")
        containsValue(specialVariables, names, values)

        val returnVariables = loadSpecialVariables(ProcessDebugger.GROUP_TYPE.RETURN)
        names = listOf("foo")
        values = listOf("1")
        containsValue(returnVariables, names, values)

        resume()
        waitForTerminate()
      }

      fun containsValue(variablesGroup: List<PyDebugValue>, names: List<String>, values: List<String>) {
        val currentNames = variablesGroup.map { obj: PyDebugValue -> obj.name }
        val currentValues = variablesGroup.map { obj: PyDebugValue -> obj.value }
        for (name in names) {
          Assert.assertTrue(currentNames.contains(name))
        }
        for (value in values) {
          Assert.assertTrue(currentValues.contains(value))
        }
      }

      override fun isLanguageLevelSupported(level: LanguageLevel): Boolean {
        return level.compareTo(LanguageLevel.PYTHON27) != 0
      }
    })
  }

  @Test
  fun testStringRepresentationInVariablesView() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_string_representation_in_variables_view.py") {
      override fun before() {
        toggleBreakpoint(getFilePath(scriptName), 17)
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        val frameVariables = loadFrame()
        checkVariableValue(frameVariables, "str", "foo_str")
        checkVariableValue(frameVariables, "repr", "foo_repr")
        val expected = eval("repr(foo_reprlib)").value.replace("[\"']".toRegex(), "")
        checkVariableValue(frameVariables, expected, "foo_reprlib")
        resume()
        waitForTerminate()
      }

      @Throws(PyDebuggerException::class, InterruptedException::class)
      fun checkVariableValue(frameVariables: List<PyDebugValue?>?, expected: String?, name: String?) {
        val value = findDebugValueByName(frameVariables!!, name!!)
        loadVariable(value)
        synchronized(this) {
          while (value!!.value!!.isEmpty() || value!!.value!!.isBlank()) {
            (this as Object).wait(1000)
          }
        }
        Assert.assertEquals(expected, value!!.value)
      }
    })
  }

  @Test
  fun testReturnValues() {
    runPythonTest(object : PyDebuggerTask("/debug", "test_return_values.py") {
      public override fun createRunConfiguration(sdkHome: String, existingSdk: Sdk?): AbstractPythonRunConfiguration<*> {
        val runConfiguration = super.createRunConfiguration(sdkHome, existingSdk) as PythonRunConfiguration
        runConfiguration.envs["PYDEVD_USE_CYTHON"] = "NO"
        return runConfiguration
      }

      override fun before() {
        toggleBreakpoint(scriptName, 2)
        val debuggerSettings = PyDebuggerSettings.getInstance()
        debuggerSettings.isWatchReturnValues = true
      }

      override fun doFinally() {
        val debuggerSettings = PyDebuggerSettings.getInstance()
        debuggerSettings.isWatchReturnValues = false
      }

      @Throws(java.lang.Exception::class)
      override fun testing() {
        waitForPause()
        stepOver()
        waitForPause()
        eval(PyDebugValue.RETURN_VALUES_PREFIX + "['bar'][0]").hasValue("1")
        stepOver()
        waitForPause()
        stepOver()
        waitForPause()
        eval(PyDebugValue.RETURN_VALUES_PREFIX + "['foo']").hasValue("33")
        resume()
        waitForTerminate()
      }
    })
  }
}