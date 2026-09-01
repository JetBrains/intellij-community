// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.xmlb.annotations.Attribute
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.ApiStatus

/**
 * Reads one kind of Python environment off the file system layout around an interpreter.
 *
 * A tool that makes its own kind of environment contributes a provider from its own module, so this module holds no
 * knowledge of venv or conda. The detector asks each provider in the registered order and takes the first answer.
 */
@ApiStatus.Internal
interface PythonEnvironmentProvider {
  /**
   * The class this provider builds.
   *
   * The core matches an environment against it to name the environment, so no environment carries its own name. See
   * [kindId].
   */
  val environmentClass: Class<out PythonEnvironment>

  /**
   * The environment around [pythonBinary], or null when the layout is not this provider's kind.
   *
   * The provider finds the environment root itself, because where the root sits depends on the kind. A virtual
   * environment on Windows keeps the interpreter in `Scripts`, and a conda environment keeps it in the root.
   *
   * Null and a failure mean different things. Null says "another provider owns this layout". A failure says "this
   * layout is mine and it is broken", which stops the search.
   */
  @RequiresBackgroundThread
  fun detect(pythonBinary: PythonBinary): PyResult<PythonEnvironment>?

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<PythonEnvironmentProviderBean> =
      ExtensionPointName.create("Pythonid.pythonEnvironmentProvider")
  }
}

/**
 * The `id` a provider declares in its xml, together with the provider.
 *
 * The id is the one place a kind of environment is named. It reaches a caller through [kindId].
 */
@ApiStatus.Internal
class PythonEnvironmentProviderBean : CustomLoadingExtensionPointBean<PythonEnvironmentProvider>() {
  /** Names the kind of environment, for example `venv`, `conda` or `system`. */
  @Attribute("id")
  @RequiredElement
  @JvmField
  var id: String? = null

  @Attribute("implementation")
  @JvmField
  var implementation: String? = null

  override fun getImplementationClassName(): String? = implementation
}

/**
 * The id of the provider that built this environment, or null when no provider claims it.
 *
 * Null is a real answer. An environment of a kind whose provider is not loaded has no name, and the caller must not
 * guess one. At most one provider declares any class, so the order of the list does not matter.
 */
@get:ApiStatus.Internal
val PythonEnvironment.kindId: String?
  get() = PythonEnvironmentProvider.EP_NAME.extensionList
    .firstOrNull { it.instance.environmentClass.isInstance(this) }
    ?.id
