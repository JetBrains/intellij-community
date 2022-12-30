// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.joinToStringFunction
import com.intellij.execution.target.value.targetPath
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path

/**
 * This is a temporary interface for smoother transition to Targets API. Its
 * lifetime is expected to be limited by the lifetime of the legacy
 * implementation based on `GeneralCommandLine`.
 */
@ApiStatus.Internal
interface EnvironmentController {
  /**
   * Puts the constant [value] to the environment variable with the provided
   * [name].
   */
  fun putFixedValue(name: String, value: String)

  /**
   * Puts the path on the target for the provided [localPath] to the
   * environment variable with the provided [name].
   */
  fun putTargetPathValue(name: String, localPath: String)

  /**
   * Composes the value based on the paths on the target for the provided
   * [localPaths] by joining them using the path separator on the target OS and
   * puts the value to the environment variable with provided [name].
   */
  fun putTargetPathsValue(name: String, localPaths: Collection<String>)

  fun putTargetPathsValue(name: String, localPaths: Collection<String>, separator: CharSequence)

  /**
   * Composes the value based on [targetPaths] by joining them using the path
   * separator on the target OS and puts the value to the environment variable
   * with the provided [name].
   */
  fun putResolvedTargetPathsValue(name: String, targetPaths: Collection<String>)

  /**
   * Appends the path on the target for the provided [localPath] to the value
   * of the environment variable with the provided [name].
   */
  fun appendTargetPathToPathsValue(name: String, localPath: String)

  /**
   * Returns whether the value of the environment variable with the provided
   * [name] is set or not.
   */
  fun isEnvSet(name: String): Boolean
}

@ApiStatus.Internal
class PlainEnvironmentController(private val envs: MutableMap<String, String>) : EnvironmentController {
  override fun putFixedValue(name: String, value: String) {
    envs[name] = value
  }

  override fun putTargetPathValue(name: String, localPath: String) {
    envs[name] = localPath
  }

  override fun putTargetPathsValue(name: String, localPaths: Collection<String>) {
    putTargetPathsValue(name, localPaths, File.pathSeparator)
  }

  override fun putTargetPathsValue(name: String, localPaths: Collection<String>, separator: CharSequence) {
    envs[name] = localPaths.joinToString(separator = separator)
  }

  override fun putResolvedTargetPathsValue(name: String, targetPaths: Collection<String>) {
    envs[name] = targetPaths.joinToString(separator = File.pathSeparator)
  }

  override fun appendTargetPathToPathsValue(name: String, localPath: String) {
    envs.merge(name, localPath) { originalValue, additionalPath ->
      listOf(originalValue, additionalPath).joinToString(separator = File.pathSeparator)
    }
  }

  override fun isEnvSet(name: String): Boolean {
    return envs.containsKey(name)
  }
}

@ApiStatus.Internal
class TargetEnvironmentController(private val envs: MutableMap<String, TargetEnvironmentFunction<String>>,
                                  private val targetEnvironmentRequest: TargetEnvironmentRequest) : EnvironmentController {
  override fun putFixedValue(name: String, value: String) {
    envs[name] = constant(value)
  }

  override fun putTargetPathValue(name: String, localPath: String) {
    val targetValue = targetPath(Path.of(localPath))
    envs[name] = targetValue
  }

  override fun putTargetPathsValue(name: String, localPaths: Collection<String>) {
    val pathSeparator = targetEnvironmentRequest.targetPlatform.platform.pathSeparator.toString()
    putTargetPathsValue(name, localPaths, pathSeparator)
  }

  override fun putTargetPathsValue(name: String, localPaths: Collection<String>, separator: CharSequence) {
    envs[name] = localPaths
      .map { localPath -> targetPath(Path.of(localPath)) }
      .joinToStringFunction(separator)
  }

  override fun putResolvedTargetPathsValue(name: String, targetPaths: Collection<String>) {
    val pathSeparatorOnTarget = targetEnvironmentRequest.targetPlatform.platform.pathSeparator
    envs[name] = constant(targetPaths.joinToString(separator = pathSeparatorOnTarget.toString()))
  }

  override fun appendTargetPathToPathsValue(name: String, localPath: String) {
    val targetValue = targetPath(Path.of(localPath))
    envs.merge(name, targetValue) { originalValue, additionalValue ->
      listOf(originalValue, additionalValue).joinToPathValue(targetEnvironmentRequest.targetPlatform)
    }
  }

  override fun isEnvSet(name: String): Boolean {
    return envs.containsKey(name)
  }
}