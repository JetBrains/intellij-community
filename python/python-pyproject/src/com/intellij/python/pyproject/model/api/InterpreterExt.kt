package com.intellij.python.pyproject.model.api

import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.sdk.backend.PythonInterpreter

fun PythonInterpreter.getPyProjectManager(): PyProjectManager = PyProjectManager.forPythonInterpreter(this)
