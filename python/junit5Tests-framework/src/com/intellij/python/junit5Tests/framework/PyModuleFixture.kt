// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Same as [moduleFixture], but with python module type. Use for python tests
 */
@TestOnly
fun TestFixture<Project>.pyModuleFixture(
  pathFixture: TestFixture<Path>,
  addPathToSourceRoot: Boolean = false,
): TestFixture<Module> = moduleFixture(pathFixture, addPathToSourceRoot, PYTHON_MODULE_ID)

/**
 * Same as [moduleFixture], but with python module type. Use for python tests
 */
@TestOnly
fun TestFixture<Project>.pyModuleFixture(
  name: String? = null,
): TestFixture<Module> = moduleFixture(name, PYTHON_MODULE_ID)

/**
 * The [PyProject] of the module this fixture creates.
 *
 * A [PyProject] needs a content root, so build the module from the [TestFixture]<[Path]> overload of [pyModuleFixture].
 * The other overload makes a module with no content root, and this fixture then fails.
 */
@TestOnly
fun TestFixture<Module>.pyProjectFixture(): TestFixture<PyProject> = testFixture("pyProject") {
  val module = this@pyProjectFixture.init()
  val pyProject = requireNotNull(module.asPyProject()) {
    "Module ${module.name} is not a Python project. Build it on a path fixture, so it has a content root."
  }
  initialized(pyProject) {}
}