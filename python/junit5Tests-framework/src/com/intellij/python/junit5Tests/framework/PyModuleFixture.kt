// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.jetbrains.python.PyNames
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Same as [moduleFixture], but with python module type. Use for python tests
 */
@TestOnly
fun TestFixture<Project>.pyModuleFixture(
  pathFixture: TestFixture<Path>,
  addPathToSourceRoot: Boolean = false,
): TestFixture<Module> = moduleFixture(pathFixture, addPathToSourceRoot, PyNames.PYTHON_MODULE_ID)

/**
 * Same as [moduleFixture], but with python module type. Use for python tests
 */
@TestOnly
fun TestFixture<Project>.pyModuleFixture(
  name: String? = null,
): TestFixture<Module> = moduleFixture(name, PyNames.PYTHON_MODULE_ID)