// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase

val TARGET_CONTEXTS_KEY = Key.create<TargetStorage>("TARGET_CONTEXTS")

fun <T> UserDataHolder.getUserData(configuration: TargetEnvironmentConfiguration?, key: Key<T>): T? {
  val targetStorage = getUserData(TARGET_CONTEXTS_KEY) ?: TargetStorage().also { putUserData(TARGET_CONTEXTS_KEY, it) }
  return targetStorage.getUserData(configuration, key)
}

fun <T> UserDataHolder.putUserData(configuration: TargetEnvironmentConfiguration?, key: Key<T>, value: T?) {
  val targetStorage = getUserData(TARGET_CONTEXTS_KEY) ?: TargetStorage().also { putUserData(TARGET_CONTEXTS_KEY, it) }
  targetStorage.putUserData(configuration, key, value)
}

/**
 * This class stores information for a specific target.
 */
class TargetStorage {
  private val context = mutableMapOf<Id, UserDataHolder>()

  fun <T> getUserData(configuration: TargetEnvironmentConfiguration?, key: Key<T>): T? = context[configuration.getId()]?.getUserData(key)

  fun <T> putUserData(configuration: TargetEnvironmentConfiguration?, key: Key<T>, value: T?): Unit =
    context.computeIfAbsent(configuration.getId()) { UserDataHolderBase() }.putUserData(key, value)

  private sealed class Id

  private data object LocalMachineId : Id()

  private data class TargetId(val id: String) : Id()

  companion object {
    private fun TargetEnvironmentConfiguration?.getId() = if (this == null) LocalMachineId else TargetId(uuid)
  }
}