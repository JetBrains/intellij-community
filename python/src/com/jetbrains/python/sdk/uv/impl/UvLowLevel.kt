// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.util.io.delete
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.sdk.VirtualEnvReader
import com.jetbrains.python.sdk.uv.UvCli
import com.jetbrains.python.sdk.uv.UvLowLevel
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

internal class UvLowLevelImpl(val cwd: Path, val uvCli: UvCli) : UvLowLevel {
  override suspend fun initializeEnvironment(init: Boolean, python: Path?): Result<Path> {
    val addPythonArg: (MutableList<String>) -> Unit = { args ->
      python?.let {
        args.add("--python")
        args.add(python.pathString)
      }
    }

    if (init) {
      val initArgs = mutableListOf("init")
      addPythonArg(initArgs)
      initArgs.add("--no-readme")
      initArgs.add("--no-pin-python")
      initArgs.add("--vcs")
      initArgs.add("none")

      uvCli.runUv(cwd, *initArgs.toTypedArray()).getOrElse {
        return Result.failure(it)
      }
    }

    val venvArgs = mutableListOf("venv");
    addPythonArg(venvArgs)
    uvCli.runUv(cwd, *venvArgs.toTypedArray()).getOrElse {
      return Result.failure(it)
    }

    // TODO: ask for an uv option not to create
    val hello = cwd.resolve("hello.py").takeIf { it.exists() }
    hello?.delete()

    val path = VirtualEnvReader.Instance.findPythonInPythonRoot(cwd.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME))
    if (path == null) {
      return Result.failure(RuntimeException("failed to initialize uv environment"))
    }

    return Result.success(path)
  }

  override suspend fun listPackages(): Result<List<PythonPackage>> {
    val out = uvCli.runUv(cwd, "pip", "list", "--format", "json").getOrElse {
      return Result.failure(it)
    }

    data class PackageInfo(val name: String, val version: String)

    val mapper = jacksonObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val packages = mapper.readValue<List<PackageInfo>>(out).map {
      PythonPackage(it.name, it.version, true)
    }

    return Result.success(packages)
  }

  override suspend fun listOutdatedPackages(): Result<List<PythonOutdatedPackage>> {

    val out = uvCli.runUv(cwd, "pip", "list", "--outdated", "--format", "json").getOrElse {
      return Result.failure(it)
    }

    data class OutdatedPackageInfo(val name: String, val version: String, val latest_version: String)

    try {
      val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      val packages = mapper.readValue<List<OutdatedPackageInfo>>(out).map {
        PythonOutdatedPackage(it.name, it.version, it.latest_version)
      }

      return Result.success(packages)
    }
    catch (e: Exception) {
      return Result.failure(e)
    }
  }

  override suspend fun installPackage(spec: PythonPackageSpecification, options: List<String>): Result<Unit> {
    val version = if (spec.versionSpecs.isNullOrBlank()) spec.name else "${spec.name}${spec.versionSpecs}"
    uvCli.runUv(cwd, "add", version, *options.toTypedArray()).getOrElse {
      return Result.failure(it)
    }

    return Result.success(Unit)
  }

  override suspend fun uninstallPackage(name: PythonPackage): Result<Unit> {
    // TODO: check if package is in dependencies
    val result = uvCli.runUv(cwd, "remove", name.name)
    if (result.isFailure) {
      // try just to uninstall
      uvCli.runUv(cwd, "pip", "uninstall", name.name).onFailure {
        return Result.failure(it)
      }
    }

    return Result.success(Unit)
  }
}

fun createUvLowLevel(cwd: Path, uvCli: UvCli = createUvCli()): UvLowLevel {
  return UvLowLevelImpl(cwd, uvCli)
}