// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PipEnvParser {
  private val gson = Gson()

  @JvmStatic
  fun getPipFileLockRequirements(virtualFile: VirtualFile): List<PyRequirement>? {
    val pipFileLock = parsePipFileLock(virtualFile).getOrNull() ?: return null
    val packages = pipFileLock.packages?.let { toRequirements(it) } ?: emptyList()
    val devPackages = pipFileLock.devPackages?.let { toRequirements(it) } ?: emptyList()
    return packages + devPackages
  }

  @RequiresBackgroundThread
  private fun toRequirements(packages: Map<String, PipFileLockPackage>): List<PyRequirement> =
    packages.mapNotNull { (name, pkg) ->
      val packageVersion = "$name${pkg.version ?: ""}"
      PyRequirementParser.fromLine(packageVersion)
    }

  private fun parsePipFileLock(virtualFile: VirtualFile): Result<PipFileLock> {
    val text = runReadAction {
      FileDocumentManager.getInstance().getDocument(virtualFile)?.text
    }
    return try {
      Result.success(gson.fromJson(text, PipFileLock::class.java))
    }
    catch (e: JsonSyntaxException) {
      Result.failure(e)
    }
  }

}