package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.util.registry.Registry

// shared settings

val projectModelEnabled: Boolean get() = Registry.`is`("intellij.python.pyproject.model")