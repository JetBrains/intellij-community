package com.intellij.python.sdk.backend

import com.intellij.openapi.components.service
import com.intellij.python.sdk.backend.service.ActivatableEnvironmentService
import com.jetbrains.python.errorProcessing.PyResult

/** The activation environment for [this] Python environment. */
suspend fun PythonEnvironment.activationEnvironment(): PyResult<Map<String, String>> =
  service<ActivatableEnvironmentService>().activationEnvironment(this)

/**
 * The id of the provider that built this environment, or null when no provider claims it.
 *
 * Null is a real answer. An environment of a kind whose provider is not loaded has no name, and the caller must not
 * guess one. At most one provider declares any class, so the order of the list does not matter.
 */
val PythonEnvironment.kindId: String?
  get() = PythonEnvironmentProvider.EP_NAME.filterableLazySequence()
    .firstOrNull { it.instance?.environmentClass?.isInstance(this) == true }
    ?.id
