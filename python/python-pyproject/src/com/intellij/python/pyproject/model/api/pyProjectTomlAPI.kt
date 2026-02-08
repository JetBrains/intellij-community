package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.python.pyproject.model.internal.isPyProjectTomlBasedImpl


/**
 * If module was generated from `pyproject.toml`
 */
val Module.isPyProjectTomlBased: Boolean get() = isPyProjectTomlBasedImpl
