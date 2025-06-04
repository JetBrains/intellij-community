// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry.packaging

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.asPythonResult
import com.jetbrains.python.packaging.management.PythonPackageManagerAction
import com.jetbrains.python.packaging.management.getPythonPackageManager
import com.jetbrains.python.sdk.poetry.PoetryPackageManager
import com.jetbrains.python.sdk.poetry.runPoetryWithSdk

internal sealed class PoetryPackageManagerAction : PythonPackageManagerAction<PoetryPackageManager, String>() {
  override fun getManager(e: AnActionEvent): PoetryPackageManager? = e.getPythonPackageManager()
}

internal class PoetryLockAction() : PoetryPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: PoetryPackageManager): Result<String, PyError> {
    return runPoetryWithManager(manager, listOf("lock"))
  }
}

internal class PoetryUpdateAction() : PoetryPackageManagerAction() {
  override suspend fun execute(e: AnActionEvent, manager: PoetryPackageManager): Result<String, PyError> {
    return runPoetryWithManager(manager, listOf("update"))
  }
}

private suspend fun runPoetryWithManager(manager: PoetryPackageManager, args: List<String>): Result<String, PyError> {
  val result = runPoetryWithSdk(manager.sdk, *args.toTypedArray())
  return result.asPythonResult()
}